-- Official schema-postgresql.sql shipped inside spring-session-jdbc 4.1.0
-- (org/springframework/session/jdbc/schema-postgresql.sql) — persists the HttpSession
-- itself (including Spring Security's Authentication inside it) so a login survives an
-- app restart instead of vanishing with the default in-memory session store.

CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

-- jogo_acoes_app needs to read/write sessions directly (Spring Session, not JPA/Hibernate,
-- talks to these tables) — ALTER DEFAULT PRIVILEGES from 01-roles.sql already covers this
-- (full CRUD), but that only applies going forward from when it was set, so grant
-- explicitly here too in case this migration ever runs before that default is in place.
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session, spring_session_attributes TO jogo_acoes_app;
