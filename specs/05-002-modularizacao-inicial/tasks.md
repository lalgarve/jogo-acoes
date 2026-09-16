# Tasks: Modularização inicial do `app/` por domínio

Quebra `spec.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Esta spec não tem
`plan.md` — as decisões técnicas (sete módulos, destino de `SecurityConfig`/`testsupport`,
convenção `client`/`dto`/`exception`) já foram resolvidas e registradas na seção "Decisões em
aberto"/corpo do próprio `spec.md`, sem justificar um `plan.md` separado.

**Pré-requisito de ordem** (ver "Decisões em aberto" desta spec e da 05-001): a spec 05-001
(renomear `io.deployo` → `dev.leilaalgarve`, Issue
[#45](https://github.com/lalgarve/jogo-acoes/issues/45)) deveria estar aplicada antes de
começar estas tarefas, para não editar o mesmo import duas vezes com duas mudanças em
andamento — mas não é uma dependência rígida, dá pra inverter se for mais conveniente.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#46](https://github.com/lalgarve/jogo-acoes/issues/46) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Confirmar o destino de `PlayerManagementService`/`EntryRequestService` (ver "Decisões em aberto" da spec — hoje propostos em `competition/`) antes de movê-los | — | [P] | #46 |
| T002 | Criar o pacote `{base}.link` e mover `LoginLink`, `LoginSession` e demais classes do mecanismo de token/link mágico | — | [P] | #46 |
| T003 | Criar o pacote `{base}.login` e mover `User`, `Role`, `UserRole`, `LoginService`, `LoginController`, `SecurityConfig` | — | [P] | #46 |
| T004 | Criar o pacote `{base}.competition` e mover `Competition`, `Participation`, `CompetitionService`, `EntryRequestService`, `PlayerManagementService` e controllers correspondentes, conforme decisão confirmada em T001 | T001 | | #46 |
| T005 | Criar o pacote `{base}.log` e mover `Log`, `LogType`, `LogRepository`, `AuditLogService` | — | [P] | #46 |
| T006 | Criar o pacote `{base}.email` e mover `EmailSender`, `StubEmailSender`, `SqsEmailSender`, `EmailContentRenderer`, `SentEmail`, `EmailRequest`/`EmailMessage`/`RenderedEmail` | — | [P] | #46 |
| T007 | Criar o pacote `{base}.captcha` e mover `CaptchaService` e a integração com o provedor de captcha (ALTCHA) | — | [P] | #46 |
| T008 | Criar o pacote `{base}.common` e mover `ScenarioWorld`/fixtures de teste compartilhadas (`testsupport`) e classes de infraestrutura genérica sem domínio próprio | — | [P] | #46 |
| T009 | Atualizar todos os imports afetados pelas movimentações (T002–T008) em todo `app/` (main e test) | T002, T003, T004, T005, T006, T007, T008 | | #46 |
| T010 | Atualizar javadoc/comentários/configuração que ainda citem os pacotes técnicos antigos (`web`, `service`, `repository`, `domain`) por nome | T009 | | #46 |
| T011 | Confirmar que os pacotes DTO gerados pelo `openapi-generator-maven-plugin` (`{base}.api.*`) permanecem intocados — checagem, sem mudança esperada | — | [P] | #46 |
| T012 | Grep final por remanescentes de `.web.`/`.service.`/`.repository.`/`.domain.` fora dos sete módulos, e corrigir | T009, T010 | | #46 |
| T013 | Rodar a suíte completa (`.feature` + testes unitários/integração) e confirmar 100% verde, sem nenhuma asserção alterada — só localização/pacote muda | T011, T012 | | #46 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`refactor`). Todas as tarefas desta spec ficam como checklist na Issue
  [#46](https://github.com/lalgarve/jogo-acoes/issues/46).
- A convenção `client`/`dto`/`exception` (ver `spec.md`) **não** é tarefa desta lista — é
  aplicada incrementalmente, módulo a módulo, à medida que a necessidade aparecer em specs
  futuras (ex.: quando um módulo ganhar cliente OpenFeign na Etapa 2), não como parte da
  modularização inicial.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
