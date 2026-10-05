# Plan: Um schema próprio por serviço no PostgreSQL

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `app` (Spring Boot 4.1.0): 7 migrations em `src/main/resources/db/migration` (`V1` a `V7`,
  nenhuma cita `public` explicitamente), `spring.flyway.locations: classpath:db/migration` em
  `application-docker.yml`, `application-sandbox.yml` e `src/test/resources/application.yml`.
  Spring Session JDBC (`spring.session.store-type: jdbc`) faz SQL próprio, sem schema, contra
  `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` (criadas pela `V3`). `SpringSessionSmokeTest` usa
  `JdbcTemplate` com SQL sem schema.
- `email-service`: 1 migration (`V1__create_email_template_table.sql`), mesma configuração de
  Flyway por perfil.
- `src/test/resources/application.yml` de cada módulo **substitui** o `application.yml`
  principal durante os testes (não soma a ele) — o que for configuração comum precisa estar
  nos dois.
- `docker/postgres/init/01-roles.sql` e `docker/postgres-email-service/init/01-roles.sql`
  criam o papel de runtime e dão `USAGE`/`ALTER DEFAULT PRIVILEGES` no `public`. Só rodam num
  volume novo.
- `staging`/`production`: URL vem da equipe que cuida do banco, `flyway.enabled: false`.
- Sandbox da Claude: Postgres nativo, papéis criados pelo script de setup do ambiente (fora do
  repositório), mesmos usuários admin dos perfis `docker`.
- `docs/disciplina/caderno-de-testes.md` tem `SELECT`s sem schema, rodados no Adminer contra o
  `db`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Nome do schema e da tabela de histórico | Schema com o nome do serviço em snake_case (`jogo_acoes`, `email_service`); histórico `<schema>_schema_history` | resolvida (Leila, 2026-10-04) | Mesmo padrão já adotado pelo `api-key` 1.0.1 (`api_key`, `api_key_schema_history`): olhando para um banco compartilhado, cada tabela de histórico diz de quem é. |
| Pasta das migrations | `classpath:db/migration-<serviço>` em kebab-case (`db/migration-jogo-acoes`, `db/migration-email-service`) | resolvida (Issue #111) | Mesmo padrão do `db/migration-api-key`. Fora de `db/migration` porque a varredura do Flyway é recursiva: um subdiretório dele seria encontrado por quem ainda usa o padrão. |
| Onde a aplicação diz qual é o schema da conexão | `spring.datasource.hikari.schema: <schema>` no `application.yml` comum (e no de teste), não `currentSchema` na URL | resolvida | A URL é definida por perfil e, em `staging`/`production`, fornecida de fora — o schema ficaria dependendo de quem escreve a URL. `hikari.schema` vale para todos os perfis e todas as conexões do pool, inclusive as do Flyway e do Spring Session JDBC (o driver do PostgreSQL transforma `setSchema` em `search_path`). A confirmar na implementação pelos testes T001/T002 e pelo `SpringSessionSmokeTest`. |
| Schema das entidades JPA | `spring.jpa.properties.hibernate.default_schema: <schema>` | resolvida | O `ddl-auto: validate` passa a procurar as tabelas no schema certo de forma explícita, sem depender do `search_path`. Entidades de outro dono (ex.: `ApiKey`, spec 05-030) continuam mapeadas para o schema delas via `orm.xml`. |
| Flyway | `spring.flyway.schemas: <schema>` (cria o schema se não existir e o usa como padrão), `spring.flyway.table: <schema>_schema_history`, `spring.flyway.locations: classpath:db/migration-<serviço>` — tudo no `application.yml` comum; os perfis deixam de repetir `locations` | resolvida | Um lugar só, igual para todos os perfis, inclusive `staging`/`production` (onde o Flyway não roda, mas os nomes ficam registrados para a equipe que roda à mão). |
| Quem cria o schema | No `docker`, o script de init cria o schema vazio com dono admin (`CREATE SCHEMA ... AUTHORIZATION <admin>`), para poder dar as permissões do papel de runtime nele; o Flyway encontra o schema vazio e só cria o histórico. No sandbox e nos testes, o Flyway cria (`createSchemas`, padrão). Em `staging`/`production`, a equipe do banco | resolvida | `ALTER DEFAULT PRIVILEGES ... IN SCHEMA x` exige que o schema já exista quando o init roda — antes de qualquer migration. Um schema vazio não pede `baseline` ao Flyway. |
| `search_path` dos papéis | `ALTER ROLE <admin> SET search_path = <schema>` e o mesmo para o papel de runtime, no script de init | resolvida | Quem consulta à mão (Adminer, `psql`, os `SELECT`s do caderno de testes) continua escrevendo `SELECT * FROM users` sem prefixo. Não é o que a aplicação usa (`hikari.schema` cobre isso); é conveniência de operação. |
| Bloquear o `public` | Não revogar nada no `public` | resolvida | O PostgreSQL 15+ já não deixa papéis comuns criarem objetos no `public`; o que garante a regra é o teste de verificação (T001/T002), não uma permissão a mais para manter em cada ambiente. |
| Spring Session JDBC | Sem `spring.session.jdbc.table-name` qualificado | resolvida | As tabelas vêm de uma migration do próprio `app` (`V3`), então ficam no schema `jogo_acoes`, e o SQL sem schema do Spring Session acha as tabelas pelo `search_path` da conexão (`hikari.schema`). |
| Verificação de que tudo cabe num banco só | Teste manual registrado no `tasks.md`: rodar a suíte do `email-service` apontando para o banco do `app` (5432, `jogo_acoes`), depois da suíte do `app` | resolvida | Prova o objetivo da spec sem mudar o `docker-compose.yml` (fora de escopo). |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

```
memory/constitution.md                          # + seção "Banco de dados: um schema por serviço"
app/src/main/resources/
  application.yml                               # + hikari.schema, flyway.schemas/table/locations,
                                                #   hibernate.default_schema (jogo_acoes)
  application-docker.yml, application-sandbox.yml  # - flyway.locations
  application-staging.yml, application-production.yml  # comentário com schema/histórico
  db/migration-jogo-acoes/V1..V7                # movidas de db/migration, sem mudar conteúdo
app/src/test/resources/application.yml          # mesmas propriedades do application.yml
app/src/test/java/.../common/SchemaLayoutTest.java   # novo (T001)
email-service/src/main/resources/...            # mesmo padrão, schema email_service
email-service/src/test/java/.../SchemaLayoutTest.java # novo (T002)
docker/postgres/init/01-roles.sql               # CREATE SCHEMA jogo_acoes, grants, search_path
docker/postgres-email-service/init/01-roles.sql # CREATE SCHEMA email_service, grants, search_path
```

## Relação com a spec 05-030

A T004 da spec [05-030](../05-030-validacao-api-key-servico-email/tasks.md) já previa mover as
migrations do `email-service` e trocar o schema dele. Esta spec faz isso para os dois módulos
de uma vez; a T004 da 05-030 passa a ser só conferir que já foi feito (ou fazer, se a 05-030
for implementada antes). A 05-030 previa `currentSchema` na URL; o mecanismo passa a ser
`hikari.schema` desta spec, com o mesmo efeito.

## Riscos e trade-offs

- **Volumes antigos do Docker**: os scripts de init só rodam num volume novo. Um volume criado
  antes desta spec continua com as tabelas no `public` e sem o schema novo — o Flyway criaria
  o schema e aplicaria tudo de novo, deixando as tabelas antigas órfãs no `public`. Mitigado:
  documentar `docker compose down -v` no README e na descrição da PR (pré-produção, sem dado a
  preservar).
- **Sandbox**: os papéis vêm de um script de setup fora do repositório, então o `ALTER ROLE ...
  SET search_path` não é aplicado lá. A aplicação não depende disso (`hikari.schema`); só a
  consulta manual sem prefixo deixa de achar as tabelas.
- **SQL escrito à mão com `public.`**: nenhum encontrado (migrations, Java, testes, scripts). O
  teste de verificação pega uma tabela criada no lugar errado, mas não uma consulta com prefixo
  errado — essa só falha quando executada.
- **`hikari.schema` amarra a configuração ao pool padrão do Spring Boot**: se o projeto trocar
  de pool, a propriedade muda de nome. Aceito: o Hikari é o padrão e não há plano de troca.
