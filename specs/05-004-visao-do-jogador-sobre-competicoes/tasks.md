# Tasks: Visão do jogador sobre competições

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões de
arquitetura referenciadas abaixo (caminhos novos, formato de resposta, onde vive o cálculo de
acesso, reaproveitamento de `entry-requests` para confirmar) já estão resolvidas em `plan.md`
("Decisões de arquitetura") — esta lista só quebra a implementação em passos.

**Ordem: TDD + API-first.** Os passos Cucumber vêm primeiro, contra o comportamento esperado
descrito em `plan.md` — usam corpo/resposta em JSON cru (RestAssured, sem depender de classes
geradas, que ainda não existem) e devem rodar e falhar de forma previsível (404 — o caminho
ainda não existe) antes de qualquer outra tarefa. O contrato OpenAPI vem em seguida,
formalizando exatamente o que os passos já esperavam, e só depois disso a implementação.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#50](https://github.com/lalgarve/jogo-acoes/issues/50) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Passos Cucumber para `browse_public_competitions.feature` — chamada HTTP direta a `GET /competitions/public`, corpo/resposta em JSON cru; rodar e confirmar que falha agora (caminho ainda não existe — `401` nos cenários sem sessão, porque `/competitions/public` ainda não está no `permitAll` do `SecurityConfig`, `404` no cenário com sessão, onde a rota simplesmente não existe; ambos confirmam a mesma causa raiz) | — | [P] | #50 |
| ~~T002~~ | Passos Cucumber para `view_my_competitions.feature` — chamadas diretas a `GET /competitions/mine` e `GET /competitions/{id}`, mesmo princípio (JSON cru, sem classe gerada); inclui o cenário de confirmar entrada a partir da tela de detalhe, que já chama o `POST /competitions/{id}/entry-requests` existente; rodar e confirmar que falha agora — todos os 10 cenários falham em `200` esperado vs `404` recebido (rota ainda não existe), exceto o de "sem relação com a competição", que já é `404` esperado e por isso passa desde já (mesmo valor, motivo diferente — vale reconferir na T011). Achados: (a) `Background` movido do nível da `Feature` para dentro de cada `Rule` — um único `Background` no topo rodaria "jogador logado" antes até do cenário do administrador, sem re-logar; (b) dois steps reaproveitados de arquivos já existentes em vez de duplicados (`the system denies access...` já existia em `CreateCompetitionSteps` com asserção diferente, por isso o texto do cenário de "sem relação" foi ajustado para não colidir; `the system adds the player to the competition` já existia em `LoginSteps`, reaproveitado como está); (c) `CompetitionFixtures` ganhou um `custom(type, status)` público para combinações fora das três já nomeadas (competição privada já aberta); (d) `canConfirmEntry` no detalhe é um campo que o `plan.md` não tinha nomeado explicitamente — carregar pra T003 | — | [P] | #50 |
| T003 | Contrato: adicionar os schemas `CompetitionSummary`/`CompetitionDetail`, os três caminhos novos (`GET /competitions/public`, `GET /competitions/mine`, `GET /competitions/{competitionId}`) e a extensão `x-roles` em toda operação de `docs/openapi.yaml` (novas e já existentes) — formaliza exatamente o que T001/T002 já esperavam | T001, T002 | | #50 |
| T004 | Adicionar `ParticipationRepository.findByUser_Id(Long userId)` | T003 | [P] | #50 |
| T005 | Adicionar `CompetitionRepository.findByTypeAndStatus(CompetitionType, CompetitionStatus)` | T003 | [P] | #50 |
| T006 | Criar `CompetitionAccessResolver` (função pura: `resolve(competition, participation, isAdministrator)` → `READ_WRITE`/`READ`/`DENIED`), em `competition/` | T003 | [P] | #50 |
| T007 | Teste dedicado de `CompetitionAccessResolver` cobrindo toda a matriz: participante em competição aberta (`READ_WRITE`); participou de competição encerrada (`READ`); convidado/pediu mas não confirmou (`READ`); administrador sem participar (`READ`); sem nenhuma relação, não administrador (`DENIED`) — sem subir o contexto Spring | T006 | [P] | #50 |
| T008 | Criar `CompetitionViewService` (`listPublicCompetitions()`, `listMyCompetitions(userId)` agrupando em `participating`/`pastParticipations`/`pendingConfirmation`, `getCompetitionDetail(competitionId, userId ou null, isAdministrator)`), usando `CompetitionAccessResolver` | T004, T005, T006 | | #50 |
| T009 | Implementar as três operações novas em `CompetitionsController` (gerado a partir de T003), delegando pra `CompetitionViewService` | T008 | | #50 |
| T010 | Atualizar `SecurityConfig`: `/competitions/public` público (`permitAll`); `/competitions/mine` e `GET /competitions/{competitionId}` exigem sessão (qualquer papel autenticado — a checagem fina de acesso é do `CompetitionAccessResolver`, não da rota) | T009 | | #50 |
| T011 | Rodar a suíte completa: confirmar que T001/T002 passam agora (verde) e que os cenários já existentes (login, convite, pedido de entrada, gerência de jogadores) continuam passando sem alteração de texto Gherkin | T009, T010 | | #50 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
