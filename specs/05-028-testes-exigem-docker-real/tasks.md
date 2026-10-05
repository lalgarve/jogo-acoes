# Tasks: Testes automatizados exigem Docker real (Postgres + LocalStack)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `app/src/test/resources/application.yml`: trocar `datasource` de H2 pelos valores reais de Postgres (`jdbc:postgresql://localhost:5432/jogo_acoes`, `jogo_acoes_admin`/`jogo_acoes_admin`, `org.postgresql.Driver`, `flyway.locations: classpath:db/migration`) | — | [P] | #<n> |
| ~~T002~~ | `email-service/src/test/resources/application.yml`: mesma troca (`jdbc:postgresql://localhost:5433/email_service`, `email_service_admin`/`email_service_admin`, `db/migration`) + endpoint fixo `spring.cloud.aws.ses.endpoint=http://localhost:4566` | — | [P] | #<n> |
| ~~T002a~~ | `app/src/main/resources/application-sandbox.yml`: trocar `datasource`/`flyway.locations` de H2 pelos mesmos valores de `application-docker.yml` (`jdbc:postgresql://localhost:5432/jogo_acoes`, `jogo_acoes_admin`/`jogo_acoes_admin`, `db/migration`); atualizar o comentário do arquivo (não é mais "No Docker/Postgres available here") | — | [P] | #<n> |
| ~~T002b~~ | `email-service/src/main/resources/application-sandbox.yml`: mesma troca (`jdbc:postgresql://localhost:5433/email_service`, `email_service_admin`/`email_service_admin`, `db/migration`) | — | [P] | #<n> |
| ~~T002c~~ | Remover `app/src/main/resources/db/migration-h2/` e `email-service/src/main/resources/db/migration-h2/` — sem perfil nenhum usando H2, essas migrations ficam mortas | T002a, T002b | | #<n> |
| ~~T002d~~ | No ambiente sandbox (com o Postgres nativo de pé): rodar `SPRING_PROFILES_ACTIVE=sandbox mvn -pl app spring-boot:run` e o equivalente em `email-service` — confirmar que sobe normalmente contra o Postgres real (Flyway aplica `db/migration` sem erro) | T002a, T002b, T002c | [P] | #<n> |
| ~~T003~~ | `email-service/src/test/java/.../CucumberSpringConfiguration.java`: remover `LocalStackContainer`/`@DynamicPropertySource`; manter só `@CucumberContextConfiguration` + `@SpringBootTest` | T002 | | #<n> |
| ~~T004~~ | `email-service/pom.xml`: remover dependências `org.testcontainers:testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:localstack` | T003 | | #<n> |
| ~~T004a~~ | `email-service/src/test/java/.../emailservice/common/testsupport/TemplateCleanupHooks.java`: hook `@After` do Cucumber que roda depois de cada cenário — `EmailTemplateRepository.deleteAll()` (limpa a tabela) + `SesClient.listTemplates()`/`deleteTemplate()` pra cada um (limpa o LocalStack). Necessário porque Postgres e LocalStack agora são persistentes entre execuções (antes, H2 em memória + Testcontainers já começavam limpos a cada `mvn test`) | T003, T004 | | #<n> |
| T005 | ~~`email-service/src/test/resources/features/register_templates.feature`: adicionar a tag `@requires-docker` no topo da Feature (propaga pra todos os Scenarios)~~ — substituída: a regra de testes que dependem de container agora está em `memory/constitution.md` (seção "Testes que dependem de infraestrutura em container") e a implementação para todos os módulos fica na spec 05-034 | — | | — |
| T006 | ~~`email-service/pom.xml`: propriedade `cucumber.filter.tags` = `not @requires-docker` por padrão; `<profile>` `docker-tests` ativado por `env.SPRING_PROFILES_ACTIVE=docker` que zera essa propriedade; `maven-surefire-plugin` passando `cucumber.filter.tags` como `systemPropertyVariable`~~ — substituída: a regra de testes que dependem de container agora está em `memory/constitution.md` (seção "Testes que dependem de infraestrutura em container") e a implementação para todos os módulos fica na spec 05-034 | — | | — |
| T007 | ~~Aplicar `@RequiresDocker` nas 13 classes `@SpringBootTest` de `app`~~ — descartado: Postgres é considerado sempre disponível (sandbox tem o nativo, aqui/CI tem o do compose); essas classes não ganham nenhuma anotação/guarda nova, continuam rodando direto | — | | — |
| T008 | ~~Aplicar `@RequiresDocker` no Cucumber de `email-service`~~ — substituído pelo mecanismo de tag (T005/T006), que depois também foi substituído (ver T005); `@EnabledIfEnvironmentVariable` não tem efeito nenhum sobre a suíte Cucumber (motor diferente do Jupiter), confirmado empiricamente | — | | — |
| ~~T009~~ | Rodar `docker compose up -d --wait db localstack` + `SPRING_PROFILES_ACTIVE=docker mvn -pl app -am verify` — confirmar que continua verde (nada deveria mudar aqui, já era o padrão de CI) | T001 | [P] | #<n> |
| ~~T010~~ | Rodar `docker compose up -d --wait db-email-service localstack` + `SPRING_PROFILES_ACTIVE=docker mvn -pl email-service -am verify` **duas vezes seguidas**, sem derrubar os containers entre as duas — confirmar os 19 Scenarios verdes nas duas rodadas (prova de que o T004a resolveu a repetibilidade, não só que passou uma vez) | T004, T004a, T006 | | #<n> |
| T011 | ~~Rodar `mvn test` (sem `SPRING_PROFILES_ACTIVE`) em `email-service`, **sem** Postgres/LocalStack de pé — confirmar `BUILD SUCCESS`/`Skipped: 14` (não erro de conexão)~~ — substituída: a regra de testes que dependem de container agora está em `memory/constitution.md` (seção "Testes que dependem de infraestrutura em container") e a implementação para todos os módulos fica na spec 05-034 | — | | — |
| T012 | `.github/workflows/ci.yml`: novo step pra `email-service`, mesmo padrão do de `app` (sobe `db-email-service`+`localstack`, `SPRING_PROFILES_ACTIVE=docker mvn -B -pl email-service -am verify`, derruba no final) | T010 | | #<n> |
| T013 | Atualizar `specs/05-025-servico-email-templates/tasks.md` — marcar T020 como resolvido (ou linkar pra esta spec como a forma como foi resolvido), já que a suíte passa a rodar contra o LocalStack do compose em vez de Testcontainers | T010 | | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`test`/`chore`, conforme o caso).
- T009, T010, T011, T012 dependem de Docker disponível no ambiente de implementação — se não
  estiver disponível (ex.: sessão da Claude sem Docker), registrar explicitamente o que não pôde
  ser verificado, mesmo padrão já usado em `specs/05-025-servico-email-templates/tasks.md`
  ("T020 — registro da verificação"). Não é motivo para reintroduzir H2/Testcontainers como
  substituto — ver `spec.md`, "Requisitos funcionais".
- T002d depende do Postgres nativo do ambiente sandbox (não de Docker) — só verificável rodando
  de fato dentro desse ambiente, não na máquina Windows de desenvolvimento local.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.

## Conferência contra o `master` (2026-10-05)

As tasks abaixo foram marcadas depois de conferir o código e os registros de verificação
de outras specs; nenhuma delas foi feita nesta revisão.

- **T001/T002**: os `application.yml` de teste de `app` e `email-service` apontam para o
  Postgres real (5432/5433) e o do `email-service` fixa o SES em `http://localhost:4566`. As
  pastas de migration depois mudaram de nome (spec 05-032).
- **T002a/T002b**: os dois `application-sandbox.yml` usam o Postgres nativo, com o comentário
  já atualizado.
- **T002c**: não existe mais nenhuma pasta `db/migration-h2` nem dependência de H2 nos POMs.
- **T002d**: coberta pela T011 da spec 05-032, que subiu `app` e `email-service` com
  `SPRING_PROFILES_ACTIVE=sandbox` contra o Postgres nativo.
- **T003/T004**: `CucumberSpringConfiguration` só tem `@CucumberContextConfiguration` e
  `@SpringBootTest`; o `email-service/pom.xml` não tem mais Testcontainers.
- **T004a**: `common/testsupport/TemplateCleanupHooks.java` existe.
- **T009**: coberta pela T009 da spec 05-032 (`mvn -pl app -am verify` com o perfil `docker`,
  176 testes, 0 falhas).
- **T010**: coberta pela T013 da spec 05-030 (`mvn -pl email-service -am verify` duas vezes
  seguidas sem derrubar os containers, 29 testes e 0 falhas nas duas).

Continuam pendentes:

- **T012**: o `.github/workflows/ci.yml` só tem os steps de `app` e `email-lambda`.
- **T013**: a T020 da spec 05-025 tem o registro da verificação, mas continua sem tachado.

Substituídas (2026-10-05):

- **T005/T006/T011**: o mecanismo de skip desta spec (tag `@requires-docker` +
  `cucumber.filter.tags` por variável de ambiente) nunca foi mesclado e foi substituído pela
  regra de `memory/constitution.md`: teste marcado explicitamente, skip decidido só pelo perfil
  (`sandbox`), erro no perfil `docker` quando a infraestrutura não responde, nunca checagem em
  tempo de execução. A spec 05-034 aplica essa regra a `app`, `email-service` e `email-lambda`,
  incluindo trocar os `assumeTrue(reachable(...))` de `SqsEmailSenderDockerIntegrationTest` e
  `QueueLoggingAspectIntegrationTest`, que esta spec tinha decidido manter.
