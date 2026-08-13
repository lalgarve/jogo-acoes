-- Runs once, on first container startup, before the app/Flyway ever connects.
--
-- jogo_acoes_admin (POSTGRES_USER below) owns the schema and is the only role that runs
-- migrations (Flyway). jogo_acoes_app is the limited runtime role: no DDL rights, and
-- automatically gets SELECT/INSERT/UPDATE/DELETE on every table jogo_acoes_admin creates
-- from now on (ALTER DEFAULT PRIVILEGES applies at CREATE TABLE time, so this covers
-- tables added by later migrations too, without editing this script again).
--
-- In homologacao/producao this same split applies, but a separate team provisions the
-- roles and runs migrations by hand — the app there only ever holds jogo_acoes_app.

CREATE ROLE jogo_acoes_app WITH LOGIN PASSWORD 'jogo_acoes_app';

GRANT CONNECT ON DATABASE jogo_acoes TO jogo_acoes_app;
GRANT USAGE ON SCHEMA public TO jogo_acoes_app;

ALTER DEFAULT PRIVILEGES FOR ROLE jogo_acoes_admin IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jogo_acoes_app;

ALTER DEFAULT PRIVILEGES FOR ROLE jogo_acoes_admin IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO jogo_acoes_app;
