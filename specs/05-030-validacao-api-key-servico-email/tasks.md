# Tasks: Serviço de E-mail — validação real de API-KEY

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Já entregue junto com esta spec** (fora da tabela): a chave de teste do cliente `jogo-acoes`
gerada pela CLI v1.0.0 e atualizada para a v1.0.1, o dump do schema `api_key`
(`docker/postgres-email-service/test-data/`), `scripts/test-api-key.sh` (`dump`/`restore`) e a
variável `EMAIL_SERVICE_API_KEY` do serviço `app` em `docker-compose.yml`. Verificado nesta
sessão contra o `db-email-service` real: o hash gravado bate com HMAC-SHA256(chave, pepper de
teste); `restore` num volume novo recria a linha e pode rodar mais de uma vez; `list` da CLI
reconhece o schema restaurado como já migrado.

**Testes de arquitetura e de verificação primeiro.** T001 e T002 nascem vermelhos (a
dependência e a configuração ainda não existem) — é o que prova que eles checam algo. As
tarefas seguintes os deixam verdes.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T000 | ~~Distribuição da biblioteca~~ — resolvida (download da release com cache no `~/.m2`). Falta confirmar `orm.xml` vs. `search_path` com os dois schemas, a partir do resultado do T002 — commit `decision:` atualizando a tabela de `plan.md` | T002 | | #108 |
| T001 | `email-service`: adicionar ArchUnit (escopo `test`) e `ArchitectureTest` com a regra: só o pacote `auth` (e `EmailServiceApplication`, pela configuração de varredura) depende de `dev.leilaalgarve.apikey..`; nenhuma classe fora de `auth` lê o header `X-API-Key` | — | [P] | #108 |
| T002 | `email-service`: teste de verificação `ApiKeyIntegrationVerificationTest` (`@SpringBootTest`, Postgres real 5433, schema `api_key` restaurado por `scripts/test-api-key.sh restore`): o contexto sobe (Flyway do serviço sem conflito de versão, `ddl-auto: validate` aceita `api_key.api_keys`) e `ApiKeyValidator.validate` devolve `Valid("jogo-acoes")` para a chave de teste e `Invalid(NOT_FOUND)` para uma chave bem formada desconhecida. Rodar vermelho antes do T003 e registrar a falha | — | [P] | #108 |
| T003 | `scripts/install-api-key-lib.sh` (baixa da release `v1.0.1` os jars de `api-key-core` e `api-key-validation` e o POM pai da tag, instala os três no `~/.m2` com `mvn install:install-file`, e não faz nada se já estiverem lá) e dependência `dev.leilaalgarve.apikey:api-key-validation:1.0.1` em `email-service/pom.xml` | T000 | | #108 |
| T004 | Mover `email-service/src/main/resources/db/migration/` para `db/migration-email-service/`; nos `application-*.yml` (inclusive o de teste), conexão com `currentSchema=email_service`, `spring.flyway.schemas: email_service`, `spring.flyway.table: email_service_schema_history`, `spring.flyway.locations: classpath:db/migration-email-service` e `hibernate.default_schema: email_service`; `docker/postgres-email-service/init/01-roles.sql` com os privilégios no schema `email_service` em vez do `public` (regra da Issue #111; pular o que a #111 já tiver feito) | T003 | | #108 |
| T005 | `EmailServiceApplication`: `scanBasePackages`, `@EntityScan` e `@EnableJpaRepositories` com os pacotes do serviço e `dev.leilaalgarve.apikey.validation`/`.core`; `META-INF/orm.xml` com o schema `api_key` para `ApiKey` (ou `search_path` com os dois schemas, conforme T000). T002 verde | T004 | | #108 |
| T006 | `API_KEY_HMAC_PEPPER`: padrão de teste em `application-docker.yml`/`application-sandbox.yml` e no `application.yml` de teste; sem padrão em `application-production.yml`; `auth/HmacPepperStartupCheck` falhando a subida sem pepper, com teste | T005 | [P] | #108 |
| T007 | `register_templates.feature`: novos cenários na Rule "Every request requires an API key" (malformada, desconhecida, expirada, revogada → não autorizada); passos `a client authenticated with the API key "client-a"` passam a significar uma chave real emitida para `client-a`. Escrito antes do código do T008 e rodado vermelho | T005 | [P] | #108 |
| T008 | Steps/hooks do Cucumber: `@Before` emite as chaves dos cenários em `api_key.api_keys` (via `ApiKeyHasher` + `ApiKeyRepository`, inclusive expirada e revogada), `@After` apaga só as linhas criadas pelo cenário (nunca a chave de teste do dump) | T007 | | #108 |
| T009 | `auth/ApiKeyAuthenticationFilter` chama `ApiKeyValidator.validate`, guarda `clientName` no atributo `apiKey.clientName`, responde 401 com o corpo atual para qualquer `Invalid` e loga o motivo (nunca a chave); `ClientIdentityResolver` lê o atributo. T001, T002 e T007 verdes | T006, T008 | | #108 |
| T010 | `email-service/Dockerfile`: estágio de build roda `scripts/install-api-key-lib.sh` antes do `dependency:go-offline`, com `RUN --mount=type=cache,target=/root/.m2` para não baixar de novo a cada build; `docker-compose.yml`: `API_KEY_HMAC_PEPPER` de teste no serviço `email-service` e comentário do serviço atualizado (não é mais esqueleto); `docker compose up --build email-service` + `scripts/test-api-key.sh restore` + `curl` com a chave de teste → 2xx, com chave inválida → 401 | T009 | | #108 |
| T011 | CI: quando o step de `email-service` existir (spec 05-028, T012), rodar `scripts/install-api-key-lib.sh` e `scripts/test-api-key.sh restore` antes do `mvn verify` | T009 | [P] | #108 |
| T012 | Documentação: `README.md` (seção do Serviço de E-mail — exemplos `curl` com a chave de teste, sem "esqueleto"), `specs/05-025-servico-email-templates/spec.md` (apontar a decisão adiada para esta spec) | T010 | [P] | #108 |
| T013 | Rodar `docker compose up -d --wait db-email-service localstack` + `scripts/test-api-key.sh restore` + `SPRING_PROFILES_ACTIVE=docker mvn -pl email-service -am verify` **duas vezes seguidas** — todos os cenários verdes nas duas (repetibilidade das chaves criadas pelos hooks) | T009 | | #108 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`/`test`/`chore`, conforme o caso).
- T002, T010 e T013 dependem de Docker (ou do Postgres nativo do sandbox na 5433) — se não
  estiver disponível, registrar explicitamente o que não pôde ser verificado (mesmo padrão de
  `specs/05-028-testes-exigem-docker-real/tasks.md`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
