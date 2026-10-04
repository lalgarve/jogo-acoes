# Tasks: Um schema próprio por serviço no PostgreSQL

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Testes de verificação primeiro.** T001 e T002 nascem vermelhos: hoje as tabelas estão no
`public` e o histórico é `flyway_schema_history`. É o que prova que eles checam algo. As
tarefas seguintes os deixam verdes. Tudo vai na mesma PR, mesclada só com o build verde.

Issue: [#111](https://github.com/lalgarve/jogo-acoes/issues/111) — cada linha abaixo é um item de
checklist nela.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `app`: `common/SchemaLayoutTest` (`@SpringBootTest`, Postgres real) consultando `information_schema`/`pg_catalog` depois do Flyway: nenhuma tabela nem sequência no `public`; todas as tabelas das migrations no schema `jogo_acoes`; histórico `jogo_acoes.jogo_acoes_schema_history` existe e não existe `flyway_schema_history` em schema nenhum. Rodar e registrar a falha | — | [P] | #111 |
| T002 | `email-service`: mesmo teste para o schema `email_service` e `email_service_schema_history`. Rodar e registrar a falha | — | [P] | #111 |
| T003 | `memory/constitution.md`: nova seção "Banco de dados: um schema por serviço" — schema com o nome do serviço, nada no `public`, `db/migration-<serviço>`, histórico `<schema>_schema_history`, schema definido pela aplicação (não pela URL); motivos (rodar tudo numa instância só, o `V1` duplicado do `api-key-core` 1.0.0). Atualizar o resumo do `CLAUDE.md` se couber | — | [P] | #111 |
| T004 | `app`: mover `src/main/resources/db/migration/` para `db/migration-jogo-acoes/` (`git mv`, conteúdo intacto); no `application.yml` comum e no `src/test/resources/application.yml`: `spring.datasource.hikari.schema`, `spring.flyway.schemas`, `spring.flyway.table`, `spring.flyway.locations`, `spring.jpa.properties.hibernate.default_schema`; remover `flyway.locations` de `application-docker.yml`/`application-sandbox.yml` | T001 | | #111 |
| T005 | `docker/postgres/init/01-roles.sql`: `CREATE SCHEMA jogo_acoes AUTHORIZATION jogo_acoes_admin`; `USAGE` e `ALTER DEFAULT PRIVILEGES` do `jogo_acoes_app` nesse schema em vez do `public`; `ALTER ROLE ... SET search_path = jogo_acoes` para os dois papéis | T004 | [P] | #111 |
| T006 | `email-service`: o mesmo do T004 com `db/migration-email-service/`, schema `email_service` e `email_service_schema_history` (cobre a T004 da spec 05-030 — se a 05-030 já tiver feito, só conferir) | T002 | [P] | #111 |
| T007 | `docker/postgres-email-service/init/01-roles.sql`: o mesmo do T005 para `email_service`/`email_service_app` | T006 | [P] | #111 |
| T008 | `application-staging.yml`/`application-production.yml` dos dois módulos: comentário com o schema e a tabela de histórico que a equipe do banco precisa criar/usar | T004, T006 | [P] | #111 |
| T009 | `docker compose down -v` + `docker compose up -d --wait db db-email-service localstack`; `SPRING_PROFILES_ACTIVE=docker mvn -pl app -am verify` e `mvn -pl email-service -am verify` — T001/T002 verdes, `SpringSessionSmokeTest` e todas as suítes verdes a partir de bancos vazios | T005, T007 | | #111 |
| T010 | Verificação "tudo num banco só": depois do T009, rodar a suíte do `email-service` com `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jogo_acoes` e o usuário admin do `app`, e conferir no `\dn`/`\dt *.*` os schemas `jogo_acoes` e `email_service` lado a lado, sem nada no `public`. Registrar o resultado aqui | T009 | | #111 |
| T011 | Sandbox (Postgres nativo): `SPRING_PROFILES_ACTIVE=sandbox` subindo `app` e `email-service` sem erro de Flyway — confirma que `hikari.schema` basta sem o `search_path` no papel | T004, T006 | [P] | #111 |
| T012 | `docs/disciplina/caderno-de-testes.md`: conferir que os `SELECT`s continuam rodando no Adminer sem prefixo (`search_path` do T005); se não, ajustar o texto (respeitando `docs/disciplina/CLAUDE.md`). README: nota de `docker compose down -v` ao atualizar | T005 | [P] | #111 |
| T013 | `specs/05-030-validacao-api-key-servico-email/tasks.md`: T004 aponta para esta spec | T006 | [P] | #111 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue #111 — label `iteration-5`, além do label de
  tipo (`refactor`).
- T009, T010 dependem de Docker; T011 do Postgres nativo do sandbox. Se algum não estiver
  disponível, registrar explicitamente o que não pôde ser verificado (mesmo padrão de
  `specs/05-028-testes-exigem-docker-real/tasks.md`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
