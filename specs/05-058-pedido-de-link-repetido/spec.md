# Spec: pedido de link de login ou de entrada repetido

**Status:** rascunho
**Issue:** [#117](https://github.com/lalgarve/jogo-acoes/issues/117) (item 4)
**Iteração:** iteration-6

## Resumo

Quando alguém pede de novo o link de login (`POST /login-requests`) ou o link de entrada numa
competição pública (`requestEntry`) enquanto ainda tem um link ativo para a mesma coisa, o sistema
reenvia o mesmo link em vez de criar outro. Assim, qualquer um dos e-mails recebidos funciona,
mesmo que a pessoa tenha pedido mais de uma vez porque o primeiro demorou (decidido em
2026-10-25).

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
uma falha de rede. No login, isso hoje é pior que no pedido de entrada: se o primeiro e-mail
chega depois do segundo, ou se a pessoa abre o primeiro que viu, o link dela já não funciona.

O token do link é guardado em claro (`LinkRecord.token`, um UUID), então reenviar o mesmo link
não exige nada novo para guardar.

A [spec 05-056](../05-056-pedido-link-login-transacional/spec.md) (Issue #135) trata da falha no
envio: se o e-mail não sai, nada muda. Esta spec trata do sucesso repetido, e parte da
orquestração em serviço transacional que a 05-056 cria.

## Cenários (comportamento esperado)

Cenários a escrever antes do código (Gherkin em inglês).

Em `app/src/test/resources/features/login.feature`, o cenário existente "Requesting a new login
link invalidates the previous one" é substituído por:

```gherkin
Scenario: Requesting a login link again resends the same link
  Given a registered player has an active login link that has not been used yet
  When they request a login link again
  Then they receive the same login link by e-mail
  And that link can be used to log in

Scenario: Requesting a login link after the previous one was used issues a new link
  Given a registered player already used their login link
  When they request a login link again
  Then they receive a new login link by e-mail
```

Em `app/src/test/resources/features/request_competition_entry.feature`:

```gherkin
Scenario: Player requests entry twice and uses the first link
  Given the player is unregistered
  And requested entry into a public competition twice
  When they open the link from the first e-mail
  Then they can finish registration and join the competition
```

## Requisitos funcionais

- **Link ativo** quer dizer: não usado, não invalidado e não expirado (validade de 7 dias,
  `LinkService.LINK_VALIDITY_DAYS`).
- Pedir o link de login quando o usuário já tem um link de login ativo com o mesmo `returnTo`
  reenvia esse link: mesmo token, mesma data de expiração (a validade não é estendida). Nenhum
  link novo é criado e nenhum link é invalidado.
- Pedir o link de login sem link de login ativo, ou com um `returnTo` diferente do link ativo,
  cria um link novo como hoje (invalidando os links ativos anteriores do usuário).
- Pedir entrada de novo numa competição em que a participação já tem um link de entrada ativo
  reenvia esse link, tenha ou não a pessoa cadastro. Sem link ativo, cria um novo.
- Pedir entrada numa competição não afeta links de entrada em outras competições nem o link de
  login.
- Cada e-mail mandado grava uma entrada `LOGIN_LINK_ISSUED` com o id do link; um reenvio grava
  outra entrada com o mesmo id, para que a auditoria mostre quantos e-mails saíram.
- Se o reenvio falhar (o `email-service` fora do ar), o link continua ativo como estava e a
  resposta é `503` (mesma regra da 05-056).
- O e-mail desconhecido em `POST /login-requests` continua respondendo `202` sem fazer nada.

## Requisitos não-funcionais

- Nenhum limite de pedidos por tempo nesta spec (ver "Fora de escopo").

## Fora de escopo

- Falha no envio do e-mail durante o pedido de um link novo (spec 05-056).
- Participação duplicada por pedido de entrada repetido (spec 05-057).
- Repetição de `POST /emails` (spec 05-059).
- Limitar quantos links uma pessoa pode pedir por hora: é proteção contra abuso, não
  idempotência, e fica para uma spec própria se for necessária.

## Decisões em aberto

- **Juntar com a 05-056**: as duas mexem na mesma orquestração do pedido de link de login. Pode
  ser mais simples implementar as duas juntas; é decisão de planejamento, não de requisito.
