-- Runs once, on first container startup, before the app/Flyway ever connects.
--
-- jogo_acoes_admin (POSTGRES_USER below) owns the `jogo_acoes` schema and is the only role that runs
-- migrations (Flyway). jogo_acoes_app is the limited runtime role: no DDL rights, and
-- automatically gets SELECT/INSERT/UPDATE/DELETE on every table jogo_acoes_admin creates
-- from now on (ALTER DEFAULT PRIVILEGES applies at CREATE TABLE time, so this covers
-- tables added by later migrations too, without editing this script again).
--
-- In staging/production this same split applies, but a separate team provisions the
-- roles and runs migrations by hand — the app there only ever holds jogo_acoes_app.

-- Own schema, never public (specs/05-032-schema-proprio-por-servico): created empty here so the
-- runtime role's privileges can point at it before any migration runs; Flyway then finds it empty
-- and only adds its history table. search_path makes hand-written queries (Adminer, psql, the
-- caderno de testes' SELECTs) find the tables without a schema prefix; the app itself sets its
-- schema through spring.datasource.hikari.schema and doesn't depend on this.

CREATE ROLE jogo_acoes_app WITH LOGIN PASSWORD 'jogo_acoes_app';

CREATE SCHEMA jogo_acoes AUTHORIZATION jogo_acoes_admin;

GRANT CONNECT ON DATABASE jogo_acoes TO jogo_acoes_app;
GRANT USAGE ON SCHEMA jogo_acoes TO jogo_acoes_app;

ALTER DEFAULT PRIVILEGES FOR ROLE jogo_acoes_admin IN SCHEMA jogo_acoes
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jogo_acoes_app;

ALTER DEFAULT PRIVILEGES FOR ROLE jogo_acoes_admin IN SCHEMA jogo_acoes
    GRANT USAGE, SELECT ON SEQUENCES TO jogo_acoes_app;

ALTER ROLE jogo_acoes_admin SET search_path = jogo_acoes;
ALTER ROLE jogo_acoes_app SET search_path = jogo_acoes;
