# Spec: Um schema próprio por serviço no PostgreSQL

**Status:** rascunho
**Issue:** [#111](https://github.com/lalgarve/jogo-acoes/issues/111)
**Iteração:** iteration-5

## Resumo

Cada serviço que tem tabelas (`app`, `email-service` e, de fora deste repositório, a CLI do
`api-key`) passa a usar um schema PostgreSQL próprio, com o nome do serviço, e nenhum usa mais
o schema `public`. As migrations Flyway de cada um ficam numa pasta com o nome do serviço, e o
histórico do Flyway numa tabela também com o nome dele. A regra vai para
`memory/constitution.md`, valendo para qualquer serviço futuro.

## Motivação

Hoje `app` e `email-service` usam os padrões do Flyway e do PostgreSQL: migrations em
`classpath:db/migration`, histórico em `flyway_schema_history` e tabelas no schema `public`.
Isso só funciona enquanto cada serviço tem um banco inteiro só para si. Assim que dois donos de
tabelas se encontram no mesmo classpath ou no mesmo banco, os padrões colidem:

- **Pasta de migrations:** o jar de `api-key-core` 1.0.0 trazia o próprio
  `db/migration/V1__create_api_keys_table.sql`; um serviço que varre `classpath:db/migration`
  achava dois `V1` e o Flyway recusava subir. O `api-key` corrigiu do lado dele na 1.0.1
  (`db/migration-api-key`), mas o mesmo problema volta com qualquer biblioteca ou serviço que
  use o padrão.
- **Tabela de histórico:** dois Flyway no mesmo schema com o mesmo `flyway_schema_history`
  misturam o histórico um do outro.
- **Schema `public` compartilhado:** o segundo Flyway a rodar encontra tabelas que não são
  dele e precisa de `baseline-on-migrate`; o comportamento passa a depender da ordem.

Há também um objetivo de operação: **poder colocar todos os serviços numa única instância do
PostgreSQL, e até num único banco**, quando for conveniente. No início, o custo de memória de
subir várias instâncias pesa, e ter várias instâncias rodando é um ponto negativo. Instâncias
separadas continuam possíveis, mas nenhum serviço pode depender de ter um banco só para si.
Decisão da Leila em 2026-10-04 (thread da spec 05-030), já aplicada ao desenho da spec
[05-030](../05-030-validacao-api-key-servico-email/plan.md).

## Cenários (comportamento esperado)

Sem cenário Gherkin novo: nenhum comportamento observável por quem usa a API muda. O
critério de aceite é estrutural, verificado por testes de verificação de schema (ver
`tasks.md`, T001/T002) e pelas suítes existentes, que precisam continuar verdes a partir de
bancos vazios:

- `app/src/test/resources/features/` (todas)
- `email-service/src/test/resources/features/register_templates.feature`

## Requisitos funcionais

- Cada serviço tem um schema próprio com o nome dele: `jogo_acoes` (`app`), `email_service`
  (`email-service`), `api_key` (CLI do `api-key`, já assim desde a spec 05-030).
- Nenhuma tabela, sequência ou histórico de Flyway de nenhum serviço fica no schema `public`.
- As migrations de cada serviço ficam em `classpath:db/migration-<serviço>`:
  `db/migration-jogo-acoes`, `db/migration-email-service` (o `api-key` já usa
  `db/migration-api-key`). Nenhuma pasta de migrations fica dentro de `db/migration`.
- O histórico do Flyway de cada serviço fica na tabela `<schema>_schema_history`, dentro do
  próprio schema: `jogo_acoes_schema_history`, `email_service_schema_history` (mesmo padrão do
  `api_key_schema_history` do `api-key`).
- Mover um serviço para outro banco ou outra instância é só trocar a URL de conexão — nenhum
  nome de schema, tabela ou histórico depende do nome do banco.
- Os dois serviços deste repositório conseguem rodar migrations e testes no mesmo banco, lado a
  lado, sem nenhuma colisão.
- Nos papéis de runtime (`jogo_acoes_app`, `email_service_app`), as permissões que hoje são
  dadas no `public` passam para o schema do serviço.

## Requisitos não-funcionais

- Nenhuma mudança de contrato HTTP, de comportamento de negócio ou de mensagem de fila.
- Os testes continuam contra PostgreSQL real (spec 05-028), sem H2 nem mock.
- `staging`/`production` continuam sem rodar Flyway sozinhos (`flyway.enabled: false`): os
  nomes novos (schema, histórico) ficam documentados para a equipe que aplica as migrations à
  mão.

## Fora de escopo

- Trocar os dois containers PostgreSQL do `docker-compose.yml` (`db`, `db-email-service`) por
  um só. Esta spec torna isso possível; fazer é uma decisão separada.
- Migrar dados existentes: o sistema está em pré-produção (`memory/constitution.md`, "Status do
  sistema"); recriar os volumes do Docker é suficiente.
- Qualquer mudança no repositório `api-key`, que já segue a regra desde a 1.0.1.
- A integração da biblioteca `api-key-validation` no `email-service` (spec 05-030) — só a parte
  de schema/migrations do `email-service` que as duas specs têm em comum (ver `plan.md`,
  "Relação com a spec 05-030").

## Decisões em aberto

- Nenhuma de requisito.
