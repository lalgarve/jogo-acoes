# Tasks: Cobertura de testes do `app` depois da PR #120

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Medição primeiro.** T001 registra a cobertura antes de qualquer mudança, para que T009 possa
mostrar o efeito de cada frente. **Testes de D1 antes do código:** T004 nasce vermelho, porque
hoje o gateway não recusa nome vazio ou nulo, e T005 o deixa verde.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Rodar `mvn -pl app -am verify` (perfil `docker`, email-service de pé) e registrar a cobertura de instruções e linhas, o total e a do `EmailServiceGateway` (referência: 76,83% / 80,35% em 2026-10-06) | — | | #132 |
| ~~T002~~ | `app/pom.xml`: acrescentar `<exclude>dev/leilaalgarve/jogoacoes/email/client/api/**</exclude>` e reescrever o comentário para citar as duas execuções do openapi-generator. Conferir no relatório que nenhuma classe de `email.client.api` aparece e que as de `email.client` continuam (C1) | T001 | [P] | #132 |
| ~~T003~~ | `.github/workflows/ci.yml`: passo `actions/upload-artifact@v4` (`name: jacoco-app`, `path: app/target/site/jacoco/`, `if: always()`) logo depois de "Run app tests with coverage" | — | [P] | #132 |
| ~~T004~~ | `EmailServiceClientIntegrationTest`: F1, F2, U2, U3 e U5 esperando `IllegalArgumentException`. Rodar e registrar a falha | T001 | [P] | #132 |
| ~~T005~~ | `EmailServiceGateway`: validar o nome (`null`/em branco) em `sendEmail`, `findTemplate`, `preview` e `upsertTemplate`, e o template `null` em `upsertTemplate`, lançando `IllegalArgumentException` antes de qualquer chamada HTTP (D1). T004 fica verde | T004 | | #132 |
| ~~T006~~ | `EmailServiceClientIntegrationTest`: S0–S4 (S1, S2 e S4 com nome e comentário dizendo que registram o comportamento atual, D2), U1 (nome `coverage-<uuid>`, duas chamadas), U4, P0–P4 (P1, P2 e P4 também conferindo que não houve nova tentativa) | T005 | | #132 |
| ~~T007~~ | `competition/PlayerManagementServiceIntegrationTest` (novo): jogador A na competição privada 1 e não na 2; `removePlayer(competição 2, participação de A na 1)` lança `PlayerNotFoundException`, a participação continua com o mesmo status, sem registro `PARTICIPATION_STATUS_CHANGED` | — | [P] | #132 |
| ~~T008~~ | `competition/EntryRequestServiceIntegrationTest` (novo): E1 (`null`) e E2 (`"   "`) lançam `EntryRequestValidationException`, sem participação nova e sem linha nova em `sent_email` | — | [P] | #132 |
| ~~T009~~ | Rodar `mvn -pl app -am verify` de novo e registrar instruções e linhas, total e do `EmailServiceGateway`, comparando com T001. As duas métricas ≥ 80% (C3) | T002, T005–T008 | | #132 |
| T010 | Na PR: conferir que o run do CI tem o artifact `jacoco-app`, que o `index.html` dele abre e mostra o mesmo relatório de T009 (C2), e que o comentário da PR mostra instruções ≥ 80% (C3) | T003, T009 | | #132 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature. A Issue leva os labels
  `iteration-5` e `test`.
- T001, T004 e T006–T009 dependem do Docker Compose inteiro de pé (`db`, `db-email-service`,
  `localstack` e `email-service`, com a chave de teste restaurada por
  `scripts/test-api-key.sh restore`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues"). Não deixar a tabela dessincronizada do estado real.

## Registro de verificação

- **T001 (2026-10-06):** medição feita no `d5970d4` (branch da PR #120, mesmo código de partida
  deste branch), com `mvn -B -pl app -am verify` no perfil `docker`: 189 testes verdes; total
  **76,83% de instruções / 80,35% de linhas**; `EmailServiceGateway` 44/59 linhas, 9/14 branches;
  `EmailServiceRejectedException` 0/2 linhas.
- **T002:** depois da exclusão, nenhuma linha de `jacoco.csv` cita `email.client.api`; as cinco
  classes escritas à mão de `email.client` continuam no relatório (C1).
- **T003:** passo adicionado. A verificação fica para a T010, no CI da PR.
- **T004:** os 10 casos rodados primeiro (`find*`, `send*Without*`, `preview*Without*` e
  `upsertingNo*`) falharam todos. O nome vazio passava sem erro (`findTemplate`, e `sendEmail`
  com `""`, porque o email-service local tinha um template de nome `""`), ou virava
  `EmailServiceRejectedException` com 404 do email-service. O nome `null` no `sendEmail` virava
  `ConstraintViolationException` do cliente gerado, e o template `null` virava
  `NullPointerException`. O caso de upsert com nome vazio/em branco não foi rodado no vermelho,
  porque ele criaria templates sem nome no email-service.
- **T005/T006/T007/T008/T009:** `mvn -B -pl app -am verify` no perfil `docker` com o Compose
  inteiro de pé: **216 testes, 0 falhas, 0 pulados**; `jacoco:check` passou. Total
  **94,78% de instruções / 95,35% de linhas** (C3). `EmailServiceGateway` 57/68 linhas, 16/20
  branches; `EmailServiceRejectedException` 2/2 linhas. O P2 foi ajustado ao comportamento real
  (ver "Desvio encontrado na implementação" em `spec.md`).
