# Tasks: Logout da sessão corrente

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (caminho `/sessions/current`, reaproveitamento do encerramento de
`SessionsService.revoke`, ramo "sessão autenticada sem `LOGIN_SESSION`") já estão resolvidas em
`plan.md` — esta lista só quebra a implementação em passos.

**Ordem: TDD + API-first**, como na spec 05-010: os cenários e passos Cucumber vêm primeiro e
devem falhar de forma previsível (o caminho ainda não existe) antes do contrato e da
implementação.

Nenhuma Issue criada ainda — a coluna "Issue" é preenchida quando a Issue-épico existir.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Acrescentar a `manage_active_sessions.feature` os três cenários do `plan.md` (logout do dispositivo atual; sessão encerrada some da lista vista pelo segundo dispositivo; logout sem estar logado), resolvendo como o cenário anônimo convive com o `Background` (`Rule` ou arquivo próprio, seguindo o padrão dos outros `.feature`). Escrever os passos novos em `ManageActiveSessionsSteps` chamando `DELETE /sessions/current` em JSON cru; rodar e confirmar que falham agora | — | | — |
| T002 | Contrato: em `docs/openapi.yaml`, `DELETE /sessions/current` (`operationId: logoutCurrentSession`, tag `sessions`, `x-roles: [PLAYER, ADMINISTRATOR]`, respostas `204`/`401`), declarado logo depois de `/sessions` e antes de `/sessions/{sessionId}` | T001 | | — |
| T003 | `LoginSessionRepository.findByHttpSessionIdAndEndedAtIsNull(String httpSessionId)` | T002 | [P] | — |
| T004 | `SessionsService`: extrair de `revoke` um método privado `end(LoginSession)` (apaga a sessão HTTP pelo `SessionRepository` e grava `endedAt`) e criar `endCurrent()`, que busca pela sessão HTTP da requisição e chama `end`; sem `LOGIN_SESSION` correspondente, invalida a sessão HTTP mesmo assim | T003 | | — |
| T005 | `SessionsController.logoutCurrentSession()` → `endCurrent()` e `204` | T004 | | — |
| T006 | Testes fora do Gherkin: (a) o ramo "sessão autenticada sem `LOGIN_SESSION`" termina com a sessão HTTP invalidada e `204`; (b) `DELETE /sessions/{id}` com id numérico continua funcionando depois do caminho literal novo (regressão da colisão de caminhos) | T005 | | — |
| T007 | Rodar `manage_active_sessions.feature` — confirmar verde | T001, T005 | | — |
| T008 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os ramos de erro adicionados ou alterados (`401` sem sessão, sessão sem `LOGIN_SESSION`); para cada um, apontar o teste que o exercita. Registrar a lista aqui | T006, T007 | | — |
| T009 | Rodar a suíte completa (`mvn test`) — confirmar verde, nenhum teste existente alterado além dos novos | T008 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
