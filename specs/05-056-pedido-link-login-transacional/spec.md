# Spec: pedido de link de login não deixa rastro quando o e-mail não sai

**Status:** rascunho
**Issue:** [#135](https://github.com/lalgarve/jogo-acoes/issues/135)
**Iteração:** iteration-5

## Resumo

Se o e-mail com o link de login não puder ser enviado, `POST /login-requests` responde `503`
e não muda nada: os links que o usuário já tinha continuam valendo, nenhum link novo fica
ativo e o log de auditoria não registra `LOGIN_LINK_ISSUED`.

## Motivação

`LoginController.requestLoginLink` não é transacional e executa, nesta ordem:

1. `linkService.invalidateActiveLinksFor(user.getId())`
2. `linkService.create(...)` do link novo
3. `auditLogService.record(LOGIN_LINK_ISSUED, ...)`
4. `emailSender.send(...)`

Cada um dos passos 1 a 3 tem o próprio `@Transactional` e já foi gravado quando o passo 4
roda. Se o envio falhar (por exemplo `EmailServiceUnavailableException` com o `email-service`
fora do ar, mapeada para `503` pelo `ApiExceptionHandler`):

- os links anteriores do usuário já foram invalidados;
- o link novo está gravado e ativo, mas nunca foi entregue;
- o log de auditoria diz `LOGIN_LINK_ISSUED` para um link que não saiu.

O impacto é baixo, porque o usuário recebe `503` e pode pedir outro link. Mas um link que ele
já tinha recebido e ainda não usou para de funcionar por causa de um pedido que falhou, e a
auditoria não bate com o que foi enviado.

Os outros fluxos que mandam e-mail já chamam o `EmailSender` dentro do próprio
`@Transactional`, e a falha no envio desfaz a gravação: `CompetitionService.decideInviteEmailTiming`,
`EntryRequestService.requestEntry` e `PlayerManagementService` (`invitePlayers`,
`resendInviteEmail(s)`). A divergência foi registrada na spec 05-034
(`specs/05-034-fronteira-sincrona-app-email-service/plan.md`, "Envio dentro da transação:
conferido") e ficou fora daquele escopo por não ser comportamento novo: era igual com a fila
SQS.

Além disso, a orquestração mora no controller, e não num serviço como nos outros fluxos. É no
serviço que fica a fronteira da transação.

## Cenários (comportamento esperado)

Cenário novo em `app/src/test/resources/features/login.feature`, a ser escrito antes do código
(Gherkin em inglês, constitution, "Idioma"), ao lado de "Requesting a new login link invalidates
the previous one". Texto proposto:

```gherkin
Scenario: Requesting a login link while the e-mail service is unavailable changes nothing
  Given a registered player has an active login link that has not been used yet
  And the e-mail service is unavailable
  When they request a new login link
  Then the system reports that the e-mail could not be sent
  And the previous login link can still be used to log in
  And no new login link was issued
```

- "Reports that the e-mail could not be sent" = `503` com corpo `Error`, como já documentado em
  `docs/openapi.yaml` (resposta `EmailServiceUnavailable` de `requestLoginLink`).
- "No new login link was issued" = nenhum link novo ativo para o usuário e nenhuma entrada
  `LOGIN_LINK_ISSUED` no log de auditoria para esse pedido.
- O cenário falha no `master` atual (link anterior invalidado, link novo ativo, entrada de
  auditoria gravada) e passa depois da correção.

## Requisitos funcionais

- Quando o envio do e-mail falha, `POST /login-requests` responde `503` (como hoje) e o estado
  fica igual ao de antes do pedido:
  - os links de login ativos do usuário continuam ativos (sem `invalidated_at`);
  - nenhum link novo fica gravado como ativo;
  - nenhuma entrada `LOGIN_LINK_ISSUED` é gravada;
  - nenhuma linha em `sent_email` (já é assim hoje, spec 05-034).
- Quando o envio dá certo, o comportamento não muda: os links anteriores são invalidados, o link
  novo é criado, a auditoria registra `LOGIN_LINK_ISSUED`, o e-mail sai e a resposta é `202`.
- E-mail desconhecido continua respondendo `202` sem criar nada e sem enviar nada, para não
  revelar se o endereço existe.
- A validação de `returnTo` (`ReturnToValidator`) continua como hoje.
- O contrato (`docs/openapi.yaml`) não muda: o `503` já está documentado.

## Requisitos não-funcionais

- Critério de aceite da constitution ("Critério de aceite: exceções e caminhos de erro"): o
  caminho em que o envio lança `EmailServiceUnavailableException` dentro do pedido de link de
  login é exercitado por teste. Esta spec não prevê classe de exceção nova; se o desenho em
  `plan.md` criar alguma, ela também precisa de um teste que a lance.
- Mesmo padrão dos outros fluxos de e-mail: o envio fica dentro da transação de banco, com o
  tempo limite de 5 s do cliente do `email-service` limitando quanto tempo uma conexão do pool
  fica presa (risco já aceito na spec 05-034).

## Fora de escopo

- **O caso inverso** (o `email-service` aceita o envio, mas a resposta se perde ou o commit
  falha depois): o usuário pode receber um e-mail com um link que não foi gravado. É o mesmo
  risco dos outros fluxos de e-mail, que já enviam dentro da transação (spec 05-034, `plan.md`,
  "Riscos": "Envio dentro da transação"). Fechar esse caso exigiria enviar só depois do commit
  (item abaixo). A idempotência de `POST /emails` não resolve: ela evita o e-mail duplicado
  quando o pedido é repetido, não o e-mail de um link desfeito. Essa idempotência foi adiada para
  a Etapa 4 pela spec 05-031 (decisão de 2026-10-04, mantida na 05-034), mas não tem spec nem
  Issue até agora; enquanto isso, o `app` não repete `POST /emails` automaticamente.
- Mover o envio para depois do commit (outbox, evento pós-commit): muda a semântica de falha e
  não é o que a Issue pede.
- Mudar `consumeLoginLink` e `completeRegistration`: eles não enviam e-mail.
- Limitar a frequência de pedidos de link (rate limit).
- Os outros fluxos de e-mail, que já estão certos.

## Decisões em aberto

Nenhuma de requisito. Onde fica o novo método transacional (a Issue sugere um serviço em
`loginsession`, como nos outros fluxos) e em que nível o cenário é testado (Cucumber com um
`EmailSender` que falha, ou teste de integração do serviço) são decisões técnicas e vão para
`plan.md`.
