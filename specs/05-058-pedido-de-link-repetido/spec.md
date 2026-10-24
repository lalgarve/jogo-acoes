# Spec: pedido de link de login ou de entrada repetido

**Status:** rascunho
**Issue:** [#117](https://github.com/lalgarve/jogo-acoes/issues/117) (item 4)
**Iteração:** iteration-6

## Resumo

Define e testa o que acontece quando alguém pede o mesmo link mais de uma vez: o link de login
(`POST /login-requests`) e o link de entrada numa competição pública (`requestEntry`). Depois de
qualquer número de pedidos, só o link mais recente funciona.

## Motivação

Os dois fluxos se comportam de forma diferente quando repetidos, e nenhum dos dois tem a regra
escrita:

- `POST /login-requests` (`LoginController.requestLoginLink`) chama
  `linkService.invalidateActiveLinksFor(user.getId())` antes de criar o link novo. Pedir de novo
  invalida todos os links ativos do usuário, inclusive links de entrada em competição. O cenário
  "Requesting a new login link invalidates the previous one" (`login.feature`) cobre só o link de
  login.
- `EntryRequestService.requestEntry` cria um link novo a cada pedido e não invalida nenhum. Um
  jogador que pede entrada três vezes fica com três links ativos para a mesma participação. Para
  quem ainda não tem cadastro não há `userId`, então `invalidateActiveLinksFor` não serve.

Repetir é comum: o e-mail demora, a pessoa clica de novo; o navegador repete o pedido depois de
uma falha de rede. Cada pedido também manda um e-mail, e a auditoria registra um
`LOGIN_LINK_ISSUED` por link.

A [spec 05-056](../05-056-pedido-link-login-transacional/spec.md) (Issue #135) trata da falha no
envio: se o e-mail não sai, nada muda. Esta spec trata do sucesso repetido, e parte da
orquestração em serviço transacional que a 05-056 cria.

## Cenários (comportamento esperado)

Cenário novo em `app/src/test/resources/features/request_competition_entry.feature`, a escrever
antes do código (Gherkin em inglês):

```gherkin
Scenario: Player requests entry twice and uses the first link
  Given the player is unregistered
  And requested entry into a public competition twice
  When they open the link from the first e-mail
  Then the system reports that the link is no longer valid
  And the link from the second e-mail still works
```

O cenário existente "Requesting a new login link invalidates the previous one"
(`login.feature`) continua valendo.

## Requisitos funcionais

- Pedir o link de login de novo invalida os links ativos anteriores do usuário e cria um novo,
  como hoje.
- Pedir entrada de novo na mesma competição invalida o link de entrada ativo anterior daquela
  participação e cria um novo, tenha ou não a pessoa cadastro.
- Pedir entrada numa competição não invalida links de entrada em outras competições.
- Usar um link invalidado responde como um link inválido hoje (`LoginLinkInvalidException`), sem
  revelar que existe um link mais novo.
- Cada pedido que manda e-mail grava um `LOGIN_LINK_ISSUED`; um link invalidado não apaga nem
  altera essa entrada (o histórico de auditoria é só de inclusão).
- O e-mail desconhecido em `POST /login-requests` continua respondendo `202` sem fazer nada.

## Requisitos não-funcionais

- Nenhum limite de pedidos por tempo nesta spec (ver "Decisões em aberto").

## Fora de escopo

- Falha no envio do e-mail durante o pedido (spec 05-056).
- Participação duplicada por pedido de entrada repetido (spec 05-057).
- Repetição de `POST /emails` (spec 05-059).

## Decisões em aberto

- **Reaproveitar ou trocar o link**: em vez de invalidar e criar um novo, pedidos repetidos numa
  janela curta (por exemplo 1 minuto) poderiam reenviar o mesmo link. Evita links que param de
  funcionar porque o segundo e-mail chegou antes do primeiro, mas exige guardar o token em claro
  ou reenviar sem link. A proposta acima mantém "o mais recente vale".
- **Limite de pedidos**: limitar quantos links uma pessoa pode pedir por hora? É proteção contra
  abuso, não idempotência; se for necessário, vira spec própria.
- **Juntar com a 05-056**: se a regra ficar como está para o login, só o link de entrada muda.
  Pode ser mais simples implementar as duas juntas.
