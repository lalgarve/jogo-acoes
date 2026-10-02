-- Same pattern as docker/postgres/init/01-roles.sql (app's own Postgres), applied to
-- email-service's dedicated database instead -- own container, own roles, never shared with
-- app's db (plan.md, "Banco de dados dedicado").
--
-- email_service_admin (POSTGRES_USER below) owns the schema and is the only role that runs
-- migrations (Flyway). email_service_app is the limited runtime role: no DDL rights, and
-- automatically gets SELECT/INSERT/UPDATE/DELETE on every table email_service_admin creates
-- from now on (ALTER DEFAULT PRIVILEGES applies at CREATE TABLE time, so this covers tables
-- added by later migrations too, without editing this script again).

CREATE ROLE email_service_app WITH LOGIN PASSWORD 'email_service_app';

GRANT CONNECT ON DATABASE email_service TO email_service_app;
GRANT USAGE ON SCHEMA public TO email_service_app;

ALTER DEFAULT PRIVILEGES FOR ROLE email_service_admin IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO email_service_app;

ALTER DEFAULT PRIVILEGES FOR ROLE email_service_admin IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO email_service_app;
