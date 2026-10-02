# Tasks: Dividir `login`/`link` em `user`, `loginSession` e `loginSecurity`

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Decisões técnicas (nome dos
pacotes, quando atualizar os diagramas, mapeamento arquivo por arquivo) já resolvidas em
`plan.md` — esta lista só quebra a implementação em passos, seguindo a ordem sugerida lá (`user`
→ `loginsecurity` → `loginsession`, testando a cada passo).

Issue-épico: [#96](https://github.com/lalgarve/jogo-acoes/issues/96) — cada linha abaixo é um
item de checklist nela, ou vira Issue própria quando grande o bastante para PR isolada (mesmo
critério de sempre).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar pacote `user/`: mover `User`, `UserRepository`, `Role`, `RoleName`, `RoleRepository`, `UserRole`, `UserRoleId`, `UserRoleRepository` de `login/` pra lá (`package` + nada mais muda nelas, são independentes entre si) | — | [P] | #96 |
| T002 | Criar pacote `loginsecurity/`: mover `SecurityConfig`, `SecurityConfigContributor` de `login/` pra lá | — | [P] | #96 |
| T003 | Atualizar todo import de `dev.leilaalgarve.jogoacoes.login.{User,UserRepository,Role,RoleName,RoleRepository,UserRole,UserRoleId,UserRoleRepository}` no resto do projeto pra `dev.leilaalgarve.jogoacoes.user.*` — achados nesta sessão: `competition/CompetitionsController.java`, `CompetitionService.java`, `EntryRequestService.java`, `PlayerManagementService.java`, `CompetitionLinkHandler.java`, `email/SentEmailRecorder.java`, `common/testsupport/UserMother.java`/`LoginLinkFixtures.java` — confirmar com `grep -rl "jogoacoes\.login\.\(User\|Role\)"` que a lista está completa | T001 | | #96 |
| T004 | Atualizar todo import de `dev.leilaalgarve.jogoacoes.login.SecurityConfigContributor` no resto do projeto (todo `*SecurityConfigContributor` existente: `blackbox/BlackboxSecurityConfigContributor`, `competition/CompetitionSecurityConfigContributor`, `common/SwaggerUiSecurityConfigContributor`, outros achados via grep) pra `dev.leilaalgarve.jogoacoes.loginsecurity.SecurityConfigContributor` | T002 | | #96 |
| T005 | `mvn -pl app -am compile` — confirmar que só falta mesmo o que os passos 6–9 resolvem (classes que ainda ficam em `login/`/`link/` referenciando os tipos já movidos) | T003, T004 | | #96 |
| T006 | Criar pacote `loginsession/`: mover `LoginController`, `LoginLinkHandler`, `LoginLinkSessionService`, `SessionsController`, `SessionsService`, `LoginSecurityConfigContributor`, `AcceptChFilter`, `ReturnToValidator`, `DeviceLabelResolver` de `login/`, e `LoginSession`, `LoginSessionRepository` de `link/`, pra lá | T001, T002 | | #96 |
| T007 | Apagar o diretório `login/` (deve estar vazio depois de T001/T002/T006 — conferir antes de apagar) | T006 | | #96 |
| T008 | Atualizar todo import de `dev.leilaalgarve.jogoacoes.link.{LoginSession,LoginSessionRepository}` e `dev.leilaalgarve.jogoacoes.login.*` (controllers/handlers/services movidos em T006) no resto do projeto pra `dev.leilaalgarve.jogoacoes.loginsession.*` | T006 | | #96 |
| T009 | Mover os testes correspondentes (tabela de `plan.md`): `LoginLinkHandlerTest`, `DeviceLabelResolverTest`, `ReturnToValidatorTest`, `DeviceIdentificationSteps`, `ManageActiveSessionsSteps` (hoje em `test/.../login/`) e `LoginSteps`, `RedirectAfterLoginSteps`, `SpringSessionSmokeTest` (hoje em `test/.../link/`) pro pacote de teste `loginsession/`; `LinkRouterKeyUniquenessTest`/`LinkServiceTest` ficam em `test/.../link/`, sem mudança | T008 | | #96 |
| T010 | `mvn -pl app -am test` — confirmar a mesma contagem de testes verdes de antes da spec (nenhum comportamento muda, só organização) | T009 | | #96 |
| T011 | Adicionar a `ArchitectureTest` uma regra verificando que `user`/`loginsecurity` não têm dependência de saída pra `loginsession` (nem um do outro) — sintaxe ArchUnit exata a confirmar contra a versão já usada pelo projeto antes de escrever (ver nota em `plan.md`) | T010 | [P] | #96 |
| T012 | Atualizar `docs/diagrams/classes.md`, `docs/diagrams/modulos.md` e `docs/diagrams/sequencia.md` — substituir as seções que hoje descrevem `login`/`link` misturados pelos diagramas já desenhados em `spec.md` (classes de `user`/`loginsession`/`loginsecurity`, sequência de login módulo a módulo) | T010 | [P] | #96 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo — T001/T002 podem acontecer juntas (nenhuma depende da outra); T011/T012
  só dependem dos testes passando (T010), não uma da outra.
- T005/T010 não precisam de Docker — toda esta spec é reorganização de código Java, sem
  dependência de infraestrutura externa.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
