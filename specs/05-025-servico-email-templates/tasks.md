# Tasks: Serviço de E-mail — cadastro de templates

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (stack do módulo, banco dedicado, cliente SES, parsing do
`TestRenderTemplate`, esqueleto de API-KEY) já estão resolvidas em `plan.md` — esta lista só
quebra a implementação em passos.

Issue-épico: [#93](https://github.com/lalgarve/jogo-acoes/issues/93) — cada linha abaixo é um
item de checklist nela, ou vira Issue própria quando grande o bastante para PR isolada (mesmo
critério de sempre).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `email-service/pom.xml`: parent `spring-boot-starter-parent` 4.1.0, `groupId dev.leilaalgarve.jogoacoes`, `java.version` 21 (mesmos valores de `app`/`email-lambda`/`blackbox-proxy`); dependências `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-flyway`/`flyway-core`/`flyway-database-postgresql`, `postgresql` (runtime), `h2` (test), `spring-boot-starter-test`; registrar o módulo em `pom.xml` (raiz), `<modules>` | — | [P] | #93 |
| T002 | Adicionar a `email-service/pom.xml`: BOM `spring-cloud-aws-dependencies` 4.1.0 (mesma versão de `app/pom.xml`) e dependência `spring-cloud-aws-starter-ses` | T001 | [P] | #93 |
| T003 | Adicionar a `email-service/pom.xml`: `jakarta.mail:jakarta.mail-api` + `org.eclipse.angus:angus-mail` (versões exatas confirmadas contra o Maven Central nesta tarefa, ver `plan.md` nota de risco) | T001 | [P] | #93 |
| T004 | Adicionar a `email-service/pom.xml`: `cucumber-bom`/`cucumber-java`/`cucumber-spring`/`junit-platform-suite` (mesmas versões de `app/pom.xml`: `cucumber.version`/`junit-platform.version`) e `rest-assured` (`rest-assured.version`, test scope) | T001 | [P] | #93 |
| T005 | Adicionar a `email-service/pom.xml`: `spring-boot-maven-plugin`, `openapi-generator-maven-plugin` 7.24.0 apontando `inputSpec` para `../docs/openapi-email-service.yaml` (`apiPackage=dev.leilaalgarve.jogoacoes.emailservice.api`, `modelPackage=...api.model`, `interfaceOnly=true`, `useTags=true`, `dateLibrary=java8`, `useSpringBoot3=true` — mesma configuração de `app/pom.xml`), `jacoco-maven-plugin` (mesmo padrão de `app/pom.xml`) | T001 | [P] | #93 |
| T006 | `EmailServiceApplication.java` (classe `@SpringBootApplication`, pacote `dev.leilaalgarve.jogoacoes.emailservice`) | T001 | | #93 |
| T007 | `db/migration/V1__create_email_template_table.sql` (Postgres) — tabela `email_template` conforme `data-model.md` (`id`, `client_id`, `name`, `subject`, `body`, `variables_schema jsonb`, `ses_template_name`, `created_at`, `updated_at`, `UNIQUE (client_id, name)`) | T001 | [P] | #93 |
| T008 | `db/migration-h2/V1__create_email_template_table.sql` — mesmo schema em dialeto H2 (mesmo padrão dual de `app`/`deployo-api-key`) | T001 | [P] | #93 |
| T009 | `EmailTemplate.java` (entidade JPA) + `EmailTemplateRepository.java` (`findByClientIdAndName`, `findAllByClientId`, `existsByClientIdAndName`) | T007, T008 | | #93 |
| T010 | `ApiKeyAuthenticationFilter.java` — rejeita com 401 (corpo `Error`) quando `X-API-Key` está ausente ou vazio; deixa passar caso contrário | T006 | [P] | #93 |
| T011 | `ClientIdentityResolver.java` (`@RequestScope`) — lê `X-API-Key` direto e devolve seu valor como id do cliente (esqueleto, ver `spec.md`/`plan.md`) | T006 | [P] | #93 |
| T012 | `SesTemplateClient.java` — encapsula `createTemplate`/`updateTemplate`/`testRenderTemplate` do `SesClient` autoconfigurado; namespacing `<client_id>__<name>`; mapeia `InvalidTemplateException`/`AlreadyExistsException`/`LimitExceededException`/`TemplateDoesNotExistException`/`InvalidRenderingParameterException`/`MissingRenderingAttributeException`/`SesException` (nomes verificados em `plan.md`) para as exceções de domínio de T014 | T002 | | #93 |
| T013 | `MimeRenderedTemplateParser.java` — recebe a string crua de `TestRenderTemplate`, monta um `MimeMessage` (Jakarta Mail) e extrai assunto/corpo renderizados | T003 | [P] | #93 |
| T014 | Exceções de domínio (`TemplateAlreadyExistsException`, `TemplateNotFoundException`, `TemplateRejectedBySesException`, `TemplateRenderFailedException`) + `ApiExceptionHandler.java` (`@RestControllerAdvice`, mesmo padrão de `app`) mapeando cada uma para 409/404/422/422 com o modelo `Error` gerado | T005 | [P] | #93 |
| T015 | `TemplateService.java` — orquestra: cadastrar (checa duplicidade → `SesTemplateClient.createTemplate` → persiste só se a chamada ao SES suceder), atualizar (busca por `client_id`+`name` → `updateTemplate` → persiste), listar, buscar uma, pré-visualizar (busca → `testRenderTemplate` → `MimeRenderedTemplateParser`) | T009, T012, T013, T014 | | #93 |
| T016 | `TemplateController.java` — implementa a interface gerada a partir de `docs/openapi-email-service.yaml`, usa `ClientIdentityResolver` + `TemplateService` | T011, T015 | | #93 |
| T017 | `application.yml` (perfil padrão/sandbox, H2) + `application-docker.yml` (Postgres `db-email-service` + `spring.cloud.aws.ses.endpoint` apontando pro LocalStack) + `application-production.yml` (sem *endpoint override*, cadeia de credenciais real) — mesma estrutura de perfis de `app` | T006 | | #93 |
| T018 | `CucumberSpringConfiguration.java` (`@SpringBootTest(webEnvironment=RANDOM_PORT)`, mesmo padrão de `app`) + configuração de teste com Testcontainers LocalStack real (módulo SES) sobrescrevendo `spring.cloud.aws.ses.endpoint` para a suíte de aceite | T017 | | #93 |
| T019 | `RegisterTemplatesSteps.java` — step definitions dos 19 Scenarios de `register_templates.feature`, via RestAssured contra o servidor real + `EmailTemplateRepository` para as asserções que olham o banco | T016, T018 | | #93 |
| T020 | Rodar `mvn -pl email-service -am test` — confirmar os 19 Scenarios verdes, incluindo os que dependem do SES de verdade rejeitar sintaxe inválida | T019 | | #93 |
| T021 | `docker/postgres-email-service/init/01-roles.sql` — mesmo padrão de `docker/postgres/init/01-roles.sql`, papéis `email_service_admin`/`email_service_app` | — | [P] | #93 |
| T022 | `docker-compose.yml`: novo serviço `db-email-service` (Postgres 16, mesmo padrão de `db`, usando o init script de T021) e novo serviço `email-service` (`depends_on: db-email-service, localstack`; `SPRING_PROFILES_ACTIVE=docker`; `SPRING_CLOUD_AWS_SES_ENDPOINT=http://localstack:4566`) | T017, T021 | | #93 |
| T023 | `README.md` — nova seção: como subir `email-service`, exemplo de `curl` cadastrando um template com `X-API-Key` (qualquer valor, esqueleto), nota explícita de que a validação da chave ainda não é real | T022 | | #93 |
| T024 | `docker compose config -q` contra o `docker-compose.yml` revisado — validar sintaxe sem precisar do daemon | T022 | [P] | #93 |
| T025 | Rodar a suíte completa de `app` (`mvn -pl app -am test`) — confirmar verde, nenhum teste existente alterado; esta spec não toca `app/src` | — | [P] | #93 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`feat`).
- T020/T024 dependem de Docker disponível no ambiente de implementação (mesma limitação já
  registrada em `specs/05-014-.../tasks.md`, `specs/05-021-.../tasks.md`) — se não estiver
  disponível, registrar explicitamente o que não pôde ser verificado e validar o que der (T001–
  T017, T021, T023, que não precisam de `docker compose up`/Testcontainers completo).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
