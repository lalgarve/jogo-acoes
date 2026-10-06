# Tasks: Isolar repositórios por módulo — comunicação entre módulos só por serviço

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Decisões técnicas (onde mora
cada serviço, assinaturas, escopo da regra de ArchUnit) já estão em `plan.md`; os números entre
parênteses (#1–#18) são os itens do inventário de `spec.md`.

**Pré-requisito de todas as tarefas:** spec 05-027 mesclada
([PR #106](https://github.com/lalgarve/jogo-acoes/pull/106)).

**Teste de arquitetura primeiro.** A regra de ArchUnit é a primeira tarefa e tem que nascer
vermelha, listando os 18 itens do inventário — é o que prova que ela funciona. As tarefas
seguintes vão tirando itens da lista até ela ficar verde. Tudo vai na mesma PR, mesclada só
quando o build estiver verde (ver "Ordem" em `plan.md`).

Issue-épico: [#95](https://github.com/lalgarve/jogo-acoes/issues/95) — cada linha abaixo é um item
de checklist nela, ou vira Issue própria quando grande o bastante para PR isolada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Adicionar a `ArchitectureTest` a regra `repositoriesAreOnlyAccessedFromTheirOwnModule` (só código de produção, módulo = primeiro segmento abaixo do pacote base) | — | | #95 |
| ~~T002~~ | Rodar `ArchitectureTest` e conferir que a regra falha com exatamente os 18 itens do inventário de `spec.md` — nem mais (inventário incompleto: atualizar `spec.md`), nem menos (regra com furo: corrigir a regra) | T001 | | #95 |
| ~~T003~~ | Criar `user/UserService` com `findByEmail`, `findRegisteredByEmail`, `getById`, `roleNamesOf`, `hasRole`, e `UserServiceTest` cobrindo cada operação | T002 | [P] | #95 |
| ~~T004~~ | Mudar `LinkSessionService.establish(Long, String)` para `establish(Long, LinkRecord)`; `LinkService.finishIfNotPending()` passa o registro; `LoginLinkSessionService` usa o registro recebido e perde `LinkRecordRepository` (#18); ajustar testes de `LinkService` que verificam `establish` | T002 | [P] | #95 |
| ~~T005~~ | `competition/CompetitionLinkHandler.complete()` cria o usuário via `UserProvisioningService.createUser(email, name, List.of(RoleName.PLAYER))`; remove `assignRole` e as dependências de `UserRepository`/`RoleRepository`/`UserRoleRepository` (#8–#10) | T002 | [P] | #95 |
| ~~T006~~ | Criar `loginsession/CurrentUserService` (`currentUser`, `currentUserId`, `currentUserIsAdministrator`) sobre `UserService`, e `CurrentUserServiceTest` (anônimo, autenticado, administrador) | T003 | | #95 |
| ~~T007~~ | `loginsession`: `LoginController` usa `userService.findByEmail` (#12); `SessionsController` usa `currentUserService.currentUser()` e perde o `currentUser()` privado (#13); `LoginLinkSessionService` delega `currentAuthenticatedUserId()` a `currentUserService.currentUserId()` e usa `userService.getById`/`roleNamesOf` (#14–#16); `LoginLinkHandler` usa `userService.hasRole` (#17) | T004, T006 | | #95 |
| ~~T008~~ | `competition`: remover os quatro `currentUser()` privados (`CompetitionsController`, `CompetitionService`, `EntryRequestService`, `PlayerManagementService`) e `CompetitionsController.isAdministrator()`, injetando `CurrentUserService` (#1, #2, #4, #6) | T006 | [P] | #95 |
| ~~T009~~ | `competition`: `CompetitionService`/`PlayerManagementService` usam `userService.findRegisteredByEmail` (#3, #7); `EntryRequestService` usa `userService.findByEmail` (#5) | T003 | [P] | #95 |
| ~~T010~~ | `email/SentEmailRecorder` usa `userService.getById` (#11) | T003 | [P] | #95 |
| ~~T011~~ | `mvn -pl app -am verify` — `ArchitectureTest` verde com a regra de T001, mesma contagem de cenários Gherkin verdes de antes, mais os testes novos | T005, T007–T010 | | #95 |
| ~~T012~~ | Atualizar `docs/diagrams/modulos.md` (rótulos das arestas `competition → user`, `email → user`, `loginsession → user`, `loginsession → link`; aresta nova `competition → loginsession` via `CurrentUserService`) e `docs/diagrams/classes.md`/`sequencia.md` onde citarem repositório de outro módulo | T011 | | #95 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo — depois de T002, T003/T004/T005 não dependem uma da outra; T008–T010 só
  dependem dos serviços que usam.
- Depois de cada tarefa de T003 a T010, rodar `ArchitectureTest` e conferir que os itens que ela
  corrige sumiram da lista de violações.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Conferência contra o `master` (2026-10-06)

Todas as tasks foram implementadas nos commits `8cf62db`, `759e3e1` e `6addd0a` (PR #107), mas
a tabela nunca foi marcada. Marcadas agora, depois de conferir o código; nenhuma delas foi
feita nesta revisão.

- **T001/T002**: `ArchitectureTest.repositoriesAreOnlyAccessedFromTheirOwnModule()`; a mensagem
  do `8cf62db` registra a falha inicial com os 18 pontos do inventário.
- **T003–T010**: `user/UserService`, `loginsession/CurrentUserService`,
  `LinkSessionService.establish(Long, LinkRecord)` e os chamadores de `competition`,
  `loginsession` e `email` passando por serviços; nenhum import de repositório de outro módulo
  sobrou.
- **T011**: verificação registrada na mensagem do `759e3e1` (`mvn -pl app -am verify`, 171
  testes, 73 cenários Cucumber).
- **T012**: `6addd0a` atualizou `docs/diagrams/modulos.md` e `sequencia.md`; `classes.md` não
  cita repositório de outro módulo.
