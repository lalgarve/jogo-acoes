-- specs/05-010-gestao-sessoes-ativas: revoking a LOGIN_SESSION needs to invalidate the real
-- HTTP session (Spring Session JDBC's spring_session.session_id), not just mark our own
-- bookkeeping row as ended -- otherwise the device stays authenticated until the cookie
-- expires. NOT NULL directly, no default/migration concern -- system in pre-production, see
-- memory/constitution.md.
-- VARCHAR, not CHAR(36) like spring_session.session_id itself -- Hibernate's schema
-- validation (ddl-auto: validate) expects a plain String column to be VARCHAR(255) unless
-- told otherwise, and there's no real reason to fight that default here.
ALTER TABLE login_session ADD COLUMN http_session_id VARCHAR(255) NOT NULL;
