# Variações de contexto Spring nos testes

Levantamento de 2026-10-21 (atualizado depois do commit `0dd8bf4`), no branch `claude/tests-start-nothing` (PR #196), feito a partir das
anotações das classes de teste de `app`, `email-service` e `config-server`. A `email-lambda` é
Quarkus e fica de fora: o `@QuarkusTest` sobe uma única aplicação para a suíte inteira.

## Como o Spring decide se reaproveita um contexto

O Spring guarda em cache cada contexto de teste que sobe e o reaproveita para toda classe que
tiver **a mesma configuração**. Entram na chave do cache, entre outros:

- o tipo de teste (`@SpringBootTest`, `@DataJpaTest`...);
- o `webEnvironment` (`MOCK`, que é o padrão, ou `RANDOM_PORT`);
- as `properties` do `@SpringBootTest`;
- os perfis (`@ActiveProfiles`);
- as classes de `@Import`;
- os métodos `@DynamicPropertySource`.

Não entram: `@Transactional`, `@ExtendWith(OutputCaptureExtension.class)` e
`@DirtiesContext`. O `@DirtiesContext` não cria contexto novo; ele **fecha** o contexto que a
classe usou, no fim dela, e quem vier depois com a mesma chave sobe outro.

Um contexto em cache fica vivo até o fim da suíte (o limite padrão é de 32 contextos). Cada um
tem o próprio pool de conexões Hikari, de até 10 conexões. Sem `minimum-idle` configurado, o
Hikari mantém as 10 abertas.

Além dos contextos em cache, alguns testes sobem a aplicação "na mão", com
`SpringApplicationBuilder`. Esses não entram no cache: são fechados pelo próprio teste.

## `app`

Nove variações em cache, todas com banco (Postgres `db`, `localhost:5432`, `max_connections`
100).

| # | Configuração | Classes | Vida |
|---|---|---|---|
| A1 | `@SpringBootTest`, `MOCK`, sem propriedades | `SchemaLayoutTest`, `ControllerLoggingAspectIntegrationTest`, `RepositoryLoggingAspectIntegrationTest`, `EntryRequestServiceIntegrationTest`, `PlayerManagementServiceIntegrationTest`, `PlayerSearchQueryIntegrationTest`, `PlayerSummaryQueryIntegrationTest`, `EmailLinkUrlIntegrationTest`, `AuditLoggingIntegrationTest`, `SpringSessionSmokeTest`, `OpenApiRolesConsistencyTest`, `OpenApiRoutesConsistencyTest`, `RouteOwnershipTest` | até o fim da suíte |
| A2 | `@SpringBootTest`, `RANDOM_PORT`, sem propriedades | `CucumberSpringConfiguration` (todos os cenários de `RunCucumberTest`), `LogoutWithoutLoginSessionIntegrationTest` | até o fim da suíte |
| A3 | `@SpringBootTest`, `RANDOM_PORT`, perfil `blackbox` | `BlackboxProfileIntegrationTest` | até o fim da suíte |
| A4 | `@SpringBootTest`, `MOCK`, perfil `production`, `spring.config.location` apontando para o `application.yml` real, credenciais do `config-server`, banco e `email-service` explícitos, Flyway desligado | `ProductionProfileSuppressesLoggingAspectsTest` | até o fim da suíte |
| A5 | `@SpringBootTest`, `RANDOM_PORT`, `email-service.base-url=http://localhost:8082/api`, `email.sender=email-service` | `RecipientDomainRejectionIntegrationTest` | fechado no fim da classe (`@DirtiesContext`, desde o commit `0dd8bf4`) |
| A6 | `@SpringBootTest`, `MOCK`, `email-service.base-url` (constante da classe), `email.sender=email-service` | `EmailServiceClientIntegrationTest` | fechado no fim da classe (`@DirtiesContext`, desde o commit `0dd8bf4`) |
| A7 | `@DataJpaTest` com o banco real (`Replace.NONE`) | `StubEmailSenderTest`, `AuditLogServiceTest`, `LogRepositoryTest` | até o fim da suíte |
| A8 | A7 + `@Import(CurrentUserService, UserService, UserProvisioningService)` | `CurrentUserServiceTest` | fechado no fim da classe (`@DirtiesContext`, desde o commit `0dd8bf4`) |
| A9 | A7 + `@Import(UserService, UserProvisioningService)` | `UserServiceTest` | fechado no fim da classe (`@DirtiesContext`, desde o commit `0dd8bf4`) |

Notas:

- `OpenApiRolesConsistencyTest`, `OpenApiRoutesConsistencyTest` e `RouteOwnershipTest` declaram
  `MOCK` explicitamente, mas `MOCK` é o padrão: dividem o contexto A1.
- A2 é o contexto da suíte Cucumber inteira. `LogoutWithoutLoginSessionIntegrationTest` tem a
  mesma configuração e reaproveita esse contexto.
- A5 e A6 diferem só no `webEnvironment` e no valor de `email-service.base-url`; por isso são dois
  contextos.
- A8 e A9 diferem só nas classes do `@Import`.
- Nenhuma classe do `app` usa `@MockitoBean` ou `@DynamicPropertySource`. Desde o commit `0dd8bf4`, as classes de A5, A6, A8 e A9 usam `@DirtiesContext`: são as únicas com aquela configuração.

Subidas "na mão" (fora do cache):

| Onde | O que sobe | Banco |
|---|---|---|
| `EmailServiceClientIntegrationTest.appDoesNotStartWithAnApiKeyEmailServiceRejects` e `appFailsFastWhenEmailServiceIsUnreachable` (método `startAppWith`) | a aplicação inteira (`JogoAcoesApplication`), com outra chave ou outro endereço do `email-service`; o teste espera que ela **não** suba | sim: Flyway conecta antes de a falha esperada acontecer |
| `ConfigServerClientIntegrationTest` (classes `@Nested`) | uma aplicação vazia (`NoBeans`, sem web), só para ler o `config-server` | não |

### Conexões no Postgres do `app`

Antes do commit `0dd8bf4`, os nove contextos ficavam vivos até o fim e os pools somavam até 90
conexões. As duas subidas "na mão" do `EmailServiceClientIntegrationTest` precisam de mais
conexões para o Flyway. Quando elas rodavam depois de quase todos os contextos já estarem em
cache, o limite de 100 estourava e o Postgres respondia `FATAL: sorry, too many clients already`.
Foi o que aconteceu no CI da PR #196 e dos pushes em `development` desde a PR #192. A ordem das
classes varia entre máquinas, e por isso a suíte passava aqui na sessão.

Depois do commit, só A1, A2, A3, A4 e A7 ficam até o fim (até 50 conexões). A classe que estiver
rodando soma mais um contexto (até 10) e, no `EmailServiceClientIntegrationTest`, a subida "na
mão" soma até mais 10: até 70 no pior caso.

## `email-service`

Banco: Postgres `db-email-service` (`localhost:5433`).

| # | Configuração | Classes | Vida |
|---|---|---|---|
| E1 | `@SpringBootTest`, `MOCK`, sem propriedades | `ApiKeyIntegrationVerificationTest`, `SchemaLayoutTest`, `DisposableDomainImportSchedulerTest`, `RecipientDomainVerifierTest`, `SesTemplateClientIntegrationTest`, `SesTemplateReconcilerIntegrationTest` | até o fim da suíte |
| E2 | `@SpringBootTest`, `RANDOM_PORT`, sem propriedades | `CucumberSpringConfiguration` (todos os cenários de `RunCucumberTest`), `DeadLetterBurstIntegrationTest`, `EmailQueuePublisherConcurrencyTest` | fechado depois de `DeadLetterBurstIntegrationTest` e depois de `EmailQueuePublisherConcurrencyTest` (`@DirtiesContext`); quem usar depois sobe outro |
| E3 | `@SpringBootTest`, `RANDOM_PORT`, `spring.config.location` apontando para o `application.yml` real, credenciais do `config-server` | `SwaggerUiAccessTest` | fechado no fim da classe (`@DirtiesContext`, desde o commit `0dd8bf4`) |
| E4 | E3 + `springdoc.swagger-ui.enabled=false` e `springdoc.api-docs.enabled=false` | `SwaggerUiSwitchedOffTest` | fechado no fim da classe (`@DirtiesContext`, desde o commit `0dd8bf4`) |
| E5 | `@SpringBootTest`, `MOCK`, `email.disposable-domains.max-size=10MB` | `ImportDisposableDomainsJobTest` | fechado no fim da classe (`@DirtiesContext`, desde o commit `0dd8bf4`) |
| E6 | `@SpringBootTest`, `MOCK`, `email.dlq-name=email-service-dead-letter-consumer-test-dlq`, `@DynamicPropertySource` com o SES numa porta livre (SES fora do ar) | `DeadLetterConsumerIntegrationTest` | fechado no fim da classe (`@DirtiesContext`) |
| E7 | `@SpringBootTest`, `RANDOM_PORT`, `@DynamicPropertySource` | `SesUnavailableIntegrationTest` | fechado no fim da classe (`@DirtiesContext`) |

Notas:

- E2 é dividido entre a suíte Cucumber e duas classes com `@DirtiesContext`. Cada uma dessas duas
  fecha o contexto no fim. Dependendo da ordem, o contexto do Cucumber sobe mais de uma vez.
- E5 repete o valor padrão de `max-size` (`10MB`, do `application.yml`). Mesmo assim, por ter
  `properties`, é um contexto à parte.
- Nenhuma classe do `email-service` usa `@MockitoBean`, `@ActiveProfiles` ou `@Import`.

Subidas "na mão" (fora do cache):

| Onde | O que sobe | Banco |
|---|---|---|
| `EmailQueuePublisherConcurrencyTest` (`secondInstance`) | uma segunda instância do `email-service`, ao lado do contexto E2, para provar que duas instâncias não publicam o mesmo envio; fechada depois de cada teste | sim |
| `SesTemplateReconcilerIntegrationTest` (método `restart`) | o `email-service` de novo, uma ou mais vezes por teste, para testar a reconciliação dos templates no SES na subida; os contextos são guardados numa lista e fechados pelo teste | sim |
| `ConfigServerClientIntegrationTest` (classes `@Nested`) | uma aplicação vazia (`NoBeans`, sem web), só para ler o `config-server` | não |

Desde o commit `0dd8bf4`, só E1 e E2 ficam em cache até o fim; os outros fecham no fim da própria
classe. Bem abaixo do limite de 100 conexões.

## `config-server`

Sem banco.

| # | Configuração | Classes | Vida |
|---|---|---|---|
| C1 | `@SpringBootTest`, `RANDOM_PORT`, `CONFIG_REPO_LOCATION=file:../config-repo/`, usuário e senha de teste | `ConfigServerTest` | até o fim da suíte |

Subida "na mão": `ConfigServerCredentialsTest` sobe o `config-server` com `SpringApplicationBuilder`
para conferir que ele não sobe sem credenciais.

## Resumo

| Módulo | Contextos em cache | Com banco | Subidas "na mão" |
|---|---|---|---|
| `app` | 9 (5 até o fim da suíte) | 9 | 2 com banco, 1 sem |
| `email-service` | 7 (2 até o fim da suíte) | 7 | 2 com banco, 1 sem |
| `config-server` | 1 | 0 | 1 sem banco |
