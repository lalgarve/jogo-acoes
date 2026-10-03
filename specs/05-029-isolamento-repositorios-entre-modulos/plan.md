# Plan: Isolar repositórios por módulo — comunicação entre módulos só por serviço

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Java/Spring Boot, Spring Data JPA (`JpaRepository`), ArchUnit já em uso
  (`app/src/test/java/.../common/ArchitectureTest.java`, spec 05-006).
- **Pré-requisito: spec 05-027 mesclada ([PR #106](https://github.com/lalgarve/jogo-acoes/pull/106)).**
  Todo nome de pacote e linha citados abaixo são os do branch daquela PR (`user`, `loginsession`,
  `loginsecurity`). Implementar esta spec antes da 05-027 obrigaria a
  reescrever metade das mudanças quando os arquivos mudassem de pacote.
- Sem mudança de schema, contrato OpenAPI ou `.feature`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| A regra vale para qualquer par de módulos ou só para os "proibidos" pela 05-003? | **Qualquer par.** `loginsession` → `link` também passa por serviço/interface. | resolvida (pedido da Leila, 2026-10-03) | A spec 05-003 define a **direção** permitida; esta spec define o **meio**. Uma dependência permitida continua permitida, só que via serviço. |
| Onde mora "usuário logado" (`CurrentUserService`)? | **`loginsession`** | resolvida (default desta spec; pode ser revista na revisão da PR) | Quem define que o *principal* da `Authentication` é o e-mail é `LoginLinkSessionService.establish()` (em `loginsession`). Pôr o serviço em `user` faria o módulo-base conhecer Spring Security e uma convenção de outro módulo. `competition` → `loginsession` não cria ciclo: `loginsession` não depende de `competition`. |
| Um serviço só em `user` ou separar leitura de criação? | **Dois**: `UserService` (leitura, novo) e `UserProvisioningService` (criação, já existe desde a 05-026) | resolvida (default) | `UserProvisioningService` já tem chamadores (`AdministratorBootstrap`) e um nome que diz o que faz. Juntar tudo numa classe só misturaria a consulta, usada em todo request, com a criação, que é rara e transacional. |
| `UserService` expõe a entidade `User` ou um DTO? | **A entidade `User`** | resolvida (default) | `Participation.user`, `SentEmail.user` e `Log.actor` são relações JPA com `User` — os chamadores precisam da entidade gerenciada. Trocar por DTO exigiria trocar essas relações por id, que está fora de escopo (`spec.md`). |
| Item 18 (`LinkRecordRepository.findByToken` em `LoginLinkSessionService`): operação nova em `LinkService` ou mudar a interface? | **Mudar `LinkSessionService.establish(Long, String)` para `establish(Long, LinkRecord)`** | resolvida (default) | `LinkService.finishIfNotPending()` já tem o `LinkRecord` carregado e hoje passa só o token para o outro lado buscá-lo de novo. Passar o registro elimina a chamada cruzada **e** uma query. A mudança fica dentro de `link` e não fere a regra da 05-027 (`link` não depende de `loginsession`/`user`). A alternativa (`LinkService.findRecordByToken`) exporia uma busca crua por token sem validação de expiração — convite a uso errado. |
| A regra de `ArchitectureTest` vale para código de teste? | **Não**, só `app/src/main/java` (`ImportOption.DoNotIncludeTests`) | resolvida (default) | Fixtures e steps (`common/testsupport`, `link/LoginSteps`, `log/...`) usam repositórios de vários módulos para montar e conferir estado real no banco — é o "preferir real a fake" da constitution. Obrigar teste a passar por serviço tornaria inviável montar estados que nenhum serviço produz (ex.: link expirado). |
| `isAdministrator()` de `CompetitionsController` entra no escopo? | **Sim**, vira `CurrentUserService.currentUserIsAdministrator()` | resolvida (default) | Não é repositório, mas é a mesma leitura de `SecurityContextHolder` fora do módulo de sessão, no mesmo arquivo que já está sendo alterado. Custo marginal zero. |

## Operações nos serviços — detalhe

### `user/UserService.java` (novo)

```java
@Service
@Transactional(readOnly = true)
public class UserService {

    /** Itens 5, 12: existe alguém com este e-mail (cadastrado ou não)? */
    public Optional<User> findByEmail(String email);

    /** Itens 3, 7: só devolve se o usuário já concluiu cadastro (User::isRegistered). */
    public Optional<User> findRegisteredByEmail(String email);

    /** Itens 11, 15: id que tem que existir — lança IllegalStateException se não existir. */
    public User getById(Long id);

    /** Item 16: nomes de papel (RoleName.*), para montar GrantedAuthority. */
    public List<String> roleNamesOf(Long userId);

    /** Item 17. */
    public boolean hasRole(Long userId, String roleName);
}
```

`getById` lança `IllegalStateException` — é o que os dois chamadores já fazem hoje
(`SentEmailRecorder` lança `IllegalArgumentException`; unificar em `IllegalStateException`, porque
nos dois casos o id vem de dentro do sistema, não da entrada do usuário — id ausente é bug, não
erro de entrada). Conferir na implementação que nenhum teste depende da classe exata da exceção.

`roleNamesOf` devolve `List<String>`, não `List<UserRole>`: o chamador só precisa do nome, e
`UserRole`/`Role` deixam de ser vistos fora de `user`.

### `user/UserProvisioningService.java` (existente, sem mudança de assinatura)

`CompetitionLinkHandler.complete()` passa a chamar
`userProvisioningService.createUser(payload.email(), extra.get("name"), List.of(RoleName.PLAYER))`
e perde `assignRole` e as dependências de `UserRepository`/`RoleRepository`/`UserRoleRepository`.
`createUser` já marca `registered = true` e já é `@Transactional` — mesmo comportamento do código
atual. (A spec 05-026 deixou em aberto se `CompetitionLinkHandler` migraria; esta spec fecha:
migra.)

### `loginsession/CurrentUserService.java` (novo)

```java
@Service
public class CurrentUserService {

    /** Itens 1, 2, 4, 6, 13. Chamado só em rota autenticada — sem usuário é bug. */
    public User currentUser();          // IllegalStateException se não houver/não achar

    /** Item 14: vazio quando anônimo (mesma checagem de AnonymousAuthenticationToken de hoje). */
    public Optional<Long> currentUserId();

    /** Substitui CompetitionsController.isAdministrator(). */
    public boolean currentUserIsAdministrator();
}
```

Depende de `UserService` (para `findByEmail`). `LoginLinkSessionService.currentAuthenticatedUserId()`
delega para `currentUserId()`, assim a regra "o *principal* é o e-mail" fica num lugar só.

### `link/LinkSessionService.java` (interface existente, assinatura muda)

```java
void establish(Long userId, LinkRecord linkRecord);
```

- `LinkService.finishIfNotPending()` passa `record` em vez de `record.getToken()`.
- `LoginLinkSessionService.establish()` usa o `LinkRecord` recebido e perde `LinkRecordRepository`.
- Javadoc da interface atualizado ("tied to the link that authenticated them").

## Mapeamento chamada → substituição

| # (spec) | Arquivo | Antes | Depois |
|---|---|---|---|
| 1 | `competition/CompetitionsController` | `currentUser()` privado + `UserRepository` | `currentUserService.currentUser()` |
| — | `competition/CompetitionsController` | `isAdministrator()` privado | `currentUserService.currentUserIsAdministrator()` |
| 2, 3 | `competition/CompetitionService` | `currentUser()` privado; `userRepository.findByEmail(e).filter(User::isRegistered)` | `currentUserService.currentUser()`; `userService.findRegisteredByEmail(e)` |
| 4, 5 | `competition/EntryRequestService` | `currentUser()` privado; `userRepository.findByEmail(e)` | `currentUserService.currentUser()`; `userService.findByEmail(e)` |
| 6, 7 | `competition/PlayerManagementService` | `currentUser()` privado; `findByEmail(...).filter(isRegistered)` | `currentUserService.currentUser()`; `userService.findRegisteredByEmail(e)` |
| 8–10 | `competition/CompetitionLinkHandler` | `new User()` + `save` + `assignRole` | `userProvisioningService.createUser(...)` |
| 11 | `email/SentEmailRecorder` | `userRepository.findById(id)` | `userService.getById(id)` |
| 12 | `loginsession/LoginController` | `userRepository.findByEmail(e)` | `userService.findByEmail(e)` |
| 13 | `loginsession/SessionsController` | `currentUser()` privado | `currentUserService.currentUser()` |
| 14 | `loginsession/LoginLinkSessionService` | `userRepository.findByEmail(auth.getName())` | `currentUserService.currentUserId()` |
| 15, 16 | `loginsession/LoginLinkSessionService` | `userRepository.findById`; `userRoleRepository.findByUser_Id` | `userService.getById`; `userService.roleNamesOf` |
| 17 | `loginsession/LoginLinkHandler` | `userRoleRepository.findByUser_Id(...).anyMatch(...)` | `userService.hasRole(userId, RoleName.ADMINISTRATOR)` |
| 18 | `loginsession/LoginLinkSessionService` | `linkRecordRepository.findByToken(token)` | parâmetro `LinkRecord` de `establish` |

## Verificação estrutural (`ArchitectureTest`)

A PR #106 já adiciona a `ArchitectureTest` a regra
`userLoginsecurityAndLinkDoNotDependOnEachOtherOrOnLoginsession` (direção das dependências entre
`user`/`loginsecurity`/`link`/`loginsession`, só código de produção). Esta spec soma uma regra
complementar — a de 05-027 diz **quem pode depender de quem**; esta diz **por onde** (nunca pelo
repositório). Mesma classe, mesma convenção ("one architecture test, not one class per rule"), e
o mesmo `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS` que a regra da 05-027 já usa:

```java
@Test
void repositoriesAreOnlyAccessedFromTheirOwnModule() {
    JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    ArchRule rule = classes()
            .that().areAssignableTo(org.springframework.data.repository.Repository.class)
            .and().areInterfaces()
            .should(onlyBeAccessedFromTheirOwnModule());

    rule.check(productionClasses);
}
```

`onlyBeAccessedFromTheirOwnModule()` é um `ArchCondition` próprio (mesmo estilo de
`haveASecurityConfigContributorInTheSamePackage`) que percorre
`repository.getDirectDependenciesToSelf()` e reporta cada origem cujo módulo difere. Módulo =
primeiro segmento depois de `dev.leilaalgarve.jogoacoes.` — por isso `competition.exception` conta
como `competition`. Não dá para usar `resideInAPackage` do ArchUnit direto porque o pacote dono
varia por repositório. `noClasses()`/`ImportOption` já são usados pela regra da 05-027, então a
API está disponível na versão do projeto.

O pacote gerado `api` não tem repositório, e `common/logging/RepositoryLoggingAspect` intercepta
repositórios por pointcut (string), sem dependência de bytecode — nenhum dos dois dispara a regra.

**Ordem**: a regra entra por último. Escrita antes, ela documenta as violações (útil para conferir
o inventário: ela deve listar exatamente os 18 itens), mas deixa o build vermelho até o fim.

## Estrutura de módulos/pacotes

Arquivos novos: `user/UserService.java`, `loginsession/CurrentUserService.java`. Nenhum pacote
novo. Grafo de dependências entre módulos não ganha aresta nova: `competition` → `loginsession`
substitui `competition` → `user` nos usos de "usuário logado" (e `competition` → `user` continua
existindo via `UserService`/`UserProvisioningService`).

`docs/diagrams/modulos.md` precisa refletir isso (ex.: a aresta `email → user`, que na
versão da PR #106 cita `UserRepository`, passa a citar `UserService`) — fazer depois da 05-027
mesclada, já que a PR #106 reescreve esse arquivo.

## Testes

- Testes novos: `UserServiceTest` (cada operação, contra banco real, seguindo a spec 05-028)
  e `CurrentUserServiceTest` (anônimo, autenticado, administrador).
- Testes existentes que constroem as classes alteradas à mão (ex.: `LoginLinkHandlerTest`,
  testes de `LinkService` que verificam `establish`) ajustam construtor/assinatura — sem mudar o
  que verificam.
- Critério de sucesso: `mvn -pl app -am verify` com a mesma contagem de cenários Gherkin verdes
  de antes, mais os testes novos, e `ArchitectureTest` verde com a regra nova.

## Riscos e trade-offs

- **Conflito com a 05-027.** As duas specs mexem nos mesmos arquivos (`loginsession/`,
  `competition/`, `ArchitectureTest`, `docs/diagrams/modulos.md`). Mitigação: esta spec só começa depois da 05-027 mesclada (ver
  "Contexto técnico").
- **Transação.** `findRegisteredByEmail`/`findByEmail` são `readOnly`, mas são chamados de dentro
  de métodos `@Transactional` de `competition`; o Spring junta na transação externa (propagação
  `REQUIRED`), então a entidade devolvida continua gerenciada e `participation.setUser(user)`
  funciona como hoje. Conferir no teste de convite.
- **Entidade exposta.** `UserService` devolve `User` (entidade gerenciada) — outro módulo ainda
  pode chamar `user.setEmail(...)` e ter a mudança persistida. Aceito por ora (ver decisão sobre
  DTO); a regra protege o acesso ao repositório, não a mutabilidade da entidade.
