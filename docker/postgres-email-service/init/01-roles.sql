-- Same pattern as docker/postgres/init/01-roles.sql (app's own Postgres), applied to
-- email-service's database instead -- own container and roles here, though since spec 05-032 the
-- `email_service` schema could just as well live in the same instance/database as app's.
--
-- email_service_admin (POSTGRES_USER below) owns the `email_service` schema and is the only role that runs
-- migrations (Flyway). email_service_app is the limited runtime role: no DDL rights, and
-- automatically gets SELECT/INSERT/UPDATE/DELETE on every table email_service_admin creates
-- from now on (ALTER DEFAULT PRIVILEGES applies at CREATE TABLE time, so this covers tables
-- added by later migrations too, without editing this script again).

-- Own schema, never public (specs/05-032-schema-proprio-por-servico): created empty here so the
-- runtime role's privileges can point at it before any migration runs; Flyway then finds it empty
-- and only adds its history table. search_path makes hand-written queries (Adminer, psql, the
-- caderno de testes' SELECTs) find the tables without a schema prefix; the app itself sets its
-- schema through spring.datasource.hikari.schema and doesn't depend on this.

CREATE ROLE email_service_app WITH LOGIN PASSWORD 'email_service_app';

CREATE SCHEMA email_service AUTHORIZATION email_service_admin;

GRANT CONNECT ON DATABASE email_service TO email_service_app;
GRANT USAGE ON SCHEMA email_service TO email_service_app;

ALTER DEFAULT PRIVILEGES FOR ROLE email_service_admin IN SCHEMA email_service
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO email_service_app;

ALTER DEFAULT PRIVILEGES FOR ROLE email_service_admin IN SCHEMA email_service
    GRANT USAGE, SELECT ON SEQUENCES TO email_service_app;

ALTER ROLE email_service_admin SET search_path = email_service;
ALTER ROLE email_service_app SET search_path = email_service;
