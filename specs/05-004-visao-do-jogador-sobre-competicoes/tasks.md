# Tasks: Visão do jogador sobre competições

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões de
arquitetura referenciadas abaixo (caminhos novos, formato de resposta, onde vive o cálculo de
acesso, reaproveitamento de `entry-requests` para confirmar) já estão resolvidas em `plan.md`
("Decisões de arquitetura") — esta lista só quebra a implementação em passos.

Ainda sem Issue-épico aberta — a coluna Issue é preenchida depois que ela existir, mesmo
padrão já usado nas specs 05-001/05-002/05-003.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Contrato primeiro: adicionar os schemas `CompetitionSummary`/`CompetitionDetail` e os três caminhos novos (`GET /competitions/public`, `GET /competitions/mine`, `GET /competitions/{competitionId}`) em `docs/openapi.yaml`, com respostas de erro (`401`/`404`) — só contrato, sem implementação ainda | — | | — |
| T002 | Adicionar a extensão `x-roles` em toda operação de `docs/openapi.yaml`, novas e já existentes (`ADMINISTRATOR`/`PLAYER`/`[]` para visitante) | T001 | [P] | — |
| T003 | Adicionar `ParticipationRepository.findByUser_Id(Long userId)` | — | [P] | — |
| T004 | Adicionar `CompetitionRepository.findByTypeAndStatus(CompetitionType, CompetitionStatus)` | — | [P] | — |
| T005 | Criar `CompetitionAccessResolver` (função pura: `resolve(competition, participation, isAdministrator)` → `READ_WRITE`/`READ`/`DENIED`), em `competition/` | — | [P] | — |
| T006 | Teste dedicado de `CompetitionAccessResolver` cobrindo toda a matriz: participante em competição aberta (`READ_WRITE`); participou de competição encerrada (`READ`); convidado/pediu mas não confirmou (`READ`); administrador sem participar (`READ`); sem nenhuma relação, não administrador (`DENIED`) — sem subir o contexto Spring | T005 | [P] | — |
| T007 | Criar `CompetitionViewService` (`listPublicCompetitions()`, `listMyCompetitions(userId)` agrupando em `participating`/`pastParticipations`/`pendingConfirmation`, `getCompetitionDetail(competitionId, userId ou null, isAdministrator)`), usando `CompetitionAccessResolver` | T003, T004, T005 | | — |
| T008 | Implementar as três operações novas em `CompetitionsController` (gerado a partir de T001), delegando pra `CompetitionViewService` | T001, T007 | | — |
| T009 | Atualizar `SecurityConfig`: `/competitions/public` público (`permitAll`); `/competitions/mine` e `GET /competitions/{competitionId}` exigem sessão (qualquer papel autenticado — a checagem fina de acesso é do `CompetitionAccessResolver`, não da rota) | T008 | | — |
| T010 | Passos Cucumber para `browse_public_competitions.feature` | T008, T009 | [P] | — |
| T011 | Passos Cucumber para `view_my_competitions.feature`, incluindo o cenário de confirmar entrada a partir da tela de detalhe (reaproveita `POST /competitions/{id}/entry-requests` já existente, sem mudança nele) | T008, T009 | [P] | — |
| T012 | Rodar a suíte completa e confirmar que os cenários já existentes (login, convite, pedido de entrada, gerência de jogadores) continuam passando sem alteração de texto Gherkin | T010, T011 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
