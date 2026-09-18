# Tasks: Organizar exceções por módulo em subpacote `exception/`

Quebra `spec.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Esta spec não tem
`plan.md` — a convenção que ela aplica já foi decidida e documentada na sessão da spec 05-002
(`docs/context/iteracao-5.md`), sem decisão técnica nova a registrar para uma mudança puramente
mecânica.

Levantamento desta sessão (`grep` por `class \w+Exception\b` em `app/src/main/java`): só dois
módulos qualificam hoje (duas ou mais exceções próprias) — `competition` (5) e `link` (2).
`captcha` tem uma só (`CaptchaInvalidException`) e não muda.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#56](https://github.com/lalgarve/jogo-acoes/issues/56) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Mover as 5 exceções de `competition/` (`CompetitionNotFoundException`, `CompetitionValidationException`, `EntryRequestValidationException`, `PlayerNotFoundException`, `PlayerValidationException`) para `competition/exception/`, ajustando a declaração `package` em cada uma | — | [P] | #56 |
| ~~T002~~ | Mover as 2 exceções de `link/` (`LoginLinkInvalidException`, `LoginLinkUsedOnAnotherDeviceException`) para `link/exception/`, ajustando a declaração `package` em cada uma | — | [P] | #56 |
| ~~T003~~ | Atualizar imports dentro do próprio módulo `competition` que referenciam as classes movidas: `CompetitionService`, `EntryRequestService`, `PlayerManagementService`, `CompetitionLinkHandler` (main) e `CompetitionLinkHandlerTest` (test) | T001 | | #56 |
| ~~T004~~ | Atualizar imports dentro do próprio módulo `link` que referenciam as classes movidas: `LinkService` (main) e `LinkServiceTest` (test) | T002 | | #56 |
| ~~T005~~ | Atualizar `common/ApiExceptionHandler` — importa as 7 classes movidas (as 5 de `competition` + as 2 de `link`), nenhum `@ExceptionHandler`/mapeamento HTTP muda, só o import | T001, T002 | | #56 |
| ~~T006~~ | Grep final por `dev.leilaalgarve.jogoacoes.competition.CompetitionNotFoundException` e demais nomes totalmente qualificados antigos (fora de diretórios de build gerados) para achar referência residual não coberta pelas tarefas acima, e corrigir — confirmado: nenhuma ocorrência residual | T003, T004, T005 | | #56 |
| ~~T007~~ | Rodar a suíte completa (`mvn test`) e confirmar 100% verde, mesma contagem de testes de antes desta spec, nenhuma asserção alterada — só pacote/import muda — confirmado: **125 testes, 0 falhas, 0 erros**, mesma contagem de antes | T006 | | #56 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`refactor`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
