# Tasks: Isolar repositórios por módulo — comunicação entre módulos só por serviço

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Decisões técnicas (onde mora
cada serviço, assinaturas, escopo da regra de ArchUnit) já estão em `plan.md`; os números entre
parênteses (#1–#18) são os itens do inventário de `spec.md`.

**Pré-requisito de todas as tarefas:** spec 05-027 mesclada
([PR #106](https://github.com/lalgarve/jogo-acoes/pull/106)). Antes de T001, refazer o `grep` do
inventário contra `master` atualizado e conferir que continuam sendo exatamente os 18 itens.

Issue-épico: [#95](https://github.com/lalgarve/jogo-acoes/issues/95) — cada linha abaixo é um item
de checklist nela, ou vira Issue própria quando grande o bastante para PR isolada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `user/UserService` com `findByEmail`, `findRegisteredByEmail`, `getById`, `roleNamesOf`, `hasRole`, e `UserServiceTest` cobrindo cada operação | — | [P] | #95 |
| T002 | Mudar `LinkSessionService.establish(Long, String)` para `establish(Long, LinkRecord)`; `LinkService.finishIfNotPending()` passa o registro; `LoginLinkSessionService` usa o registro recebido e perde `LinkRecordRepository` (#18); ajustar testes de `LinkService` que verificam `establish` | — | [P] | #95 |
| T003 | Criar `loginsession/CurrentUserService` (`currentUser`, `currentUserId`, `currentUserIsAdministrator`) sobre `UserService`, e `CurrentUserServiceTest` (anônimo, autenticado, administrador) | T001 | | #95 |
| T004 | `loginsession`: `LoginController` usa `userService.findByEmail` (#12); `SessionsController` usa `currentUserService.currentUser()` e perde o `currentUser()` privado (#13); `LoginLinkSessionService` delega `currentAuthenticatedUserId()` a `currentUserService.currentUserId()` e usa `userService.getById`/`roleNamesOf` (#14–#16); `LoginLinkHandler` usa `userService.hasRole` (#17) | T002, T003 | | #95 |
| T005 | `competition`: remover os quatro `currentUser()` privados (`CompetitionsController`, `CompetitionService`, `EntryRequestService`, `PlayerManagementService`) e `CompetitionsController.isAdministrator()`, injetando `CurrentUserService` (#1, #2, #4, #6) | T003 | [P] | #95 |
| T006 | `competition`: `CompetitionService`/`PlayerManagementService` usam `userService.findRegisteredByEmail` (#3, #7); `EntryRequestService` usa `userService.findByEmail` (#5) | T001 | [P] | #95 |
| T007 | `competition/CompetitionLinkHandler.complete()` cria o usuário via `UserProvisioningService.createUser(email, name, List.of(RoleName.PLAYER))`; remove `assignRole` e as dependências de `UserRepository`/`RoleRepository`/`UserRoleRepository` (#8–#10) | — | [P] | #95 |
| T008 | `email/SentEmailRecorder` usa `userService.getById` (#11) | T001 | [P] | #95 |
| T009 | Adicionar a `ArchitectureTest` a regra `repositoriesAreOnlyAccessedFromTheirOwnModule` (só código de produção, módulo = primeiro segmento abaixo do pacote base) | T004–T008 | | #95 |
| T010 | `mvn -pl app -am verify` — mesma contagem de cenários Gherkin verdes de antes, mais os testes novos, `ArchitectureTest` verde | T009 | | #95 |
| T011 | Atualizar `docs/diagrams/modulos.md` (rótulos das arestas `competition → user`, `email → user`, `loginsession → user`, `loginsession → link`; aresta nova `competition → loginsession` via `CurrentUserService`) e `docs/diagrams/classes.md`/`sequencia.md` onde citarem repositório de outro módulo | T010 | | #95 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo — T001/T002/T007 não dependem de nada desta spec; T005–T008 só dependem dos
  serviços que usam.
- Escrever T009 antes de T004–T008 é útil para conferir o inventário (a regra deve listar
  exatamente os itens ainda não corrigidos), mas não commitar a regra antes de o build ficar
  verde com ela.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
