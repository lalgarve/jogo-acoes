# Spec: convite e entrada em competição sem participação duplicada

**Status:** rascunho
**Issue:** [#117](https://github.com/lalgarve/jogo-acoes/issues/117) (item 4)
**Iteração:** iteration-6

## Resumo

Repetir um convite, um pedido de entrada ou uma confirmação de entrada deixa a competição no
mesmo estado da primeira vez: cada e-mail tem no máximo uma participação por competição, e a
confirmação repetida não muda a data de entrada nem grava auditoria nova.

## Motivação

Hoje nada impede duas participações para o mesmo e-mail na mesma competição:

- A tabela `participation` (`V1__init_schema.sql`) não tem restrição de unicidade em
  `(competition_id, email)`.
- `PlayerManagementService.invitePlayers` e a criação de competição privada
  (`CompetitionService`, laço sobre `request.getEmails()`) criam uma participação nova para
  cada e-mail da lista, sem conferir se ele já está na competição nem se aparece duas vezes na
  mesma lista. Convidar de novo o mesmo e-mail cria uma segunda linha e manda um segundo
  convite.
- `EntryRequestService.requestEntry` reaproveita a participação existente
  (`findByCompetition_IdAndEmailAndStatusNot(..., IN_COMPETITION)`), mas a busca diferencia
  maiúsculas de minúsculas, e dois pedidos simultâneos podem passar os dois pela busca e criar
  duas linhas.
- `EntryRequestService.confirmEntry` (jogador logado entrando de novo, `POST` no mesmo
  endpoint) regrava `joinedAt` com a data de hoje e grava outra entrada
  `PARTICIPATION_STATUS_CHANGED` a cada chamada, mesmo quando o jogador já está
  `IN_COMPETITION`.

`PlayerManagementService.updateEmail` já recusa um e-mail usado por outro jogador da mesma
competição, sem diferenciar maiúsculas (`existsByCompetition_IdAndIdNotAndEmailIgnoreCase`). Esta
spec estende a mesma regra a todos os caminhos que criam participação, e a garante no banco.

Duas chamadas iguais aparecem na prática por duplo clique, por o navegador repetir um pedido
depois de uma falha de rede, ou por o administrador colar a mesma lista duas vezes.

## Cenários (comportamento esperado)

Cenários novos, a escrever antes do código (Gherkin em inglês, constitution, "Idioma").

Em `app/src/test/resources/features/manage_competition_players.feature`:

```gherkin
Scenario: Administrator invites an e-mail that is already in the competition
  Given a private competition already has a player with a given e-mail
  When the administrator invites that same e-mail again
  Then the competition still has a single player with that e-mail

Scenario: Administrator invites a list that repeats an e-mail
  Given a private competition
  When the administrator invites a list in which the same e-mail appears twice
  Then the competition has a single player with that e-mail
```

Em `app/src/test/resources/features/request_competition_entry.feature`:

```gherkin
Scenario: Registered player confirms entry into a competition they already joined
  Given a registered player already participates in a public competition
  When they confirm entry into that competition again
  Then their entry date does not change
  And no new participation status change is recorded
```

Mais um teste de integração (não cenário), contra PostgreSQL real: dois pedidos de entrada
simultâneos para o mesmo e-mail e a mesma competição resultam numa participação só.

## Requisitos funcionais

- Uma competição tem no máximo uma participação por e-mail, comparando sem diferenciar
  maiúsculas de minúsculas. Vale para criação de competição privada, `invitePlayers`,
  `requestEntry`, `confirmEntry` e `updateEmail`.
- Convidar um e-mail que já está na competição não cria participação nova. O que acontece com o
  convite fica em "Decisões em aberto".
- Uma lista de convite com o mesmo e-mail repetido é tratada como se o e-mail aparecesse uma
  vez.
- `requestEntry` repetido continua reaproveitando a participação existente e reenviando o link
  (cenário já existente "Player requests entry again into a public competition they already
  requested"). A busca passa a ignorar maiúsculas.
- `confirmEntry` de um jogador já `IN_COMPETITION` responde `200` com a participação como está:
  não muda `joinedAt` e não grava `PARTICIPATION_STATUS_CHANGED`.
- `resendInviteEmail(s)` continua mandando o e-mail de novo a cada chamada (é o que o
  administrador pediu); a participação só muda para `EMAIL_SENT`, como hoje.
- A unicidade é garantida também no banco, por uma migration nova em
  `db/migration-jogo-acoes`, para que dois pedidos simultâneos não passem os dois. A migration
  não trata duplicatas já gravadas: o sistema não está em produção (decidido em 2026-10-25), e um
  ambiente local com duplicata é recriado.

## Requisitos não-funcionais

- O teste de concorrência roda contra o PostgreSQL real do Testcontainers, sem mock
  (constitution, "Testes").
- Toda exceção nova tem um teste que a lança (constitution, "Critério de aceite: exceções e
  caminhos de erro").

## Fora de escopo

- Pedido de link de login e link de entrada repetidos (spec 05-058).
- Repetição de `POST /emails` entre `app` e `email-service` (spec 05-059) e entrega duplicada da
  fila (spec 05-060).
- Concluir o cadastro duas vezes pelo mesmo link: o link já é de uso único (`used_at`).

## Decisões em aberto

- **Convite de um e-mail que já está na competição**: ignorar em silêncio, reenviar o convite
  quando a participação ainda não está `IN_COMPETITION`, ou recusar o pedido inteiro com `409`?
  Cada opção muda o contrato de `invitePlayers` (`docs/openapi.yaml` hoje só documenta `202` e
  `503`).
