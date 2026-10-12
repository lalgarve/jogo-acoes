# Tasks: Logout da sessão corrente

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (caminho `/sessions/current`, reaproveitamento do encerramento de
`SessionsService.revoke`, ramo "sessão autenticada sem `LOGIN_SESSION`") já estão resolvidas em
`plan.md` — esta lista só quebra a implementação em passos.

**Ordem: TDD + API-first**, como na spec 05-010: os cenários e passos Cucumber vêm primeiro e
devem falhar de forma previsível (o caminho ainda não existe) antes do contrato e da
implementação.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico [#148](https://github.com/lalgarve/jogo-acoes/issues/148).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Acrescentar a `manage_active_sessions.feature` os três cenários do `plan.md` (logout do dispositivo atual; sessão encerrada some da lista vista pelo segundo dispositivo; logout sem estar logado), resolvendo como o cenário anônimo convive com o `Background` (`Rule` ou arquivo próprio, seguindo o padrão dos outros `.feature`). Escrever os passos novos em `ManageActiveSessionsSteps` chamando `DELETE /sessions/current` em JSON cru; rodar e confirmar que falham agora | — | | #148 |
| ~~T002~~ | Contrato: em `docs/openapi.yaml`, `DELETE /sessions/current` (`operationId: logoutCurrentSession`, tag `sessions`, `x-roles: [PLAYER, ADMINISTRATOR]`, respostas `204`/`401`), declarado logo depois de `/sessions` e antes de `/sessions/{sessionId}` | T001 | | #148 |
| ~~T003~~ | `LoginSessionRepository.findByHttpSessionIdAndEndedAtIsNull(String httpSessionId)` | T002 | [P] | #148 |
| ~~T004~~ | `SessionsService`: extrair de `revoke` um método privado `end(LoginSession)` (apaga a sessão HTTP pelo `SessionRepository` e grava `endedAt`) e criar `endCurrent()`, que busca pela sessão HTTP da requisição e chama `end`; sem `LOGIN_SESSION` correspondente, invalida a sessão HTTP mesmo assim | T003 | | #148 |
| ~~T005~~ | `SessionsController.logoutCurrentSession()` → `endCurrent()` e `204` | T004 | | #148 |
| ~~T006~~ | Testes fora do Gherkin: (a) o ramo "sessão autenticada sem `LOGIN_SESSION`" termina com a sessão HTTP invalidada e `204`; (b) `DELETE /sessions/{id}` com id numérico continua funcionando depois do caminho literal novo (regressão da colisão de caminhos) | T005 | | #148 |
| ~~T007~~ | Rodar `manage_active_sessions.feature` — confirmar verde | T001, T005 | | #148 |
| ~~T008~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os ramos de erro adicionados ou alterados (`401` sem sessão, sessão sem `LOGIN_SESSION`); para cada um, apontar o teste que o exercita. Registrar a lista aqui | T006, T007 | | #148 |
| ~~T009~~ | Rodar a suíte completa (`mvn test`) — confirmar verde, nenhum teste existente alterado além dos novos | T008 | | #148 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro da implementação (2026-10-11)

- **T001**: `manage_active_sessions.feature` reorganizado em duas `Rule`s — os seis cenários
  existentes, sem mudança de texto, mais os dois de logout ficam na regra com o `Background` de
  jogador logado; o cenário anônimo fica numa regra própria, sem `Background`. Passos novos em
  `ManageActiveSessionsSteps`. Antes da implementação, os dois cenários de logout falharam como
  previsto: `DELETE /sessions/current` caía em `/sessions/{sessionId}` e devolvia `400`
  (conversão de `"current"` para `Long`). O cenário anônimo já passava, porque `/sessions/**`
  exige autenticação.
- **T002**: `DELETE /sessions/current` (`logoutCurrentSession`) no `docs/openapi.yaml`, entre
  `/sessions` e `/sessions/{sessionId}`.
- **T003–T005**: `LoginSessionRepository.findByHttpSessionIdAndEndedAtIsNull`;
  `SessionsService.endCurrent()`, com o passo de encerramento extraído para `end(LoginSession)`
  e reaproveitado por `revoke`; `SessionsController.logoutCurrentSession()`. Diagramas
  atualizados: `docs/diagrams/modulos.md` (métodos novos) e `docs/diagrams/der.md` (uso de
  `http_session_id` pelo logout).
- **T006**: (a) `LogoutWithoutLoginSessionIntegrationTest` — login real, encerra a
  `LOGIN_SESSION` direto no banco (a sessão HTTP continua válida), chama o logout e confirma
  `204` e depois `401`. (b) Não ganhou teste novo: os cenários existentes "Player revokes a
  session from another device", "Player revokes their own current session" e os dois de `404`
  já chamam `DELETE /sessions/{id}` com id numérico e continuam verdes com o caminho literal
  declarado.
- **T007**: `manage_active_sessions.feature` verde (9 cenários). `OpenApiRolesConsistencyTest`,
  `OpenApiRoutesConsistencyTest`, `RouteOwnershipTest` e `ArchitectureTest` verdes.
- **T008**: nenhuma classe de exceção criada. Ramos de erro: `401` sem sessão → cenário
  "Anonymous user tries to log out"; sessão autenticada sem `LOGIN_SESSION` ativa →
  `LogoutWithoutLoginSessionIntegrationTest`.
- **T009**: `mvn -pl app test` — 221 testes, os 77 cenários Cucumber verdes. 38 falhas (36 em
  `EmailServiceClientIntegrationTest`, mais `ProductionProfileSuppressesLoggingAspectsTest` e
  `RepositoryLoggingAspectIntegrationTest`), todas porque o `email-service` não subiu neste
  ambiente (o build dele dentro do container não alcança o Maven Central). As mesmas 38 falham
  no `master` sem esta mudança (217 testes); `RepositoryLoggingAspectIntegrationTest` passa
  quando roda sozinho. A CI, com o `email-service` de pé, é a verificação da suíte completa.
