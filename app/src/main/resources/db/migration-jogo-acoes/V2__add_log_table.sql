CREATE TABLE log (
    id BIGSERIAL PRIMARY KEY,
    related_object_id BIGINT NOT NULL,
    user_id BIGINT REFERENCES app_user (id),
    created_at TIMESTAMP NOT NULL,
    log_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL
);

-- jogo_acoes_app gets SELECT/INSERT/UPDATE/DELETE on every new table by default (see
-- docker/postgres/init/01-roles.sql); narrow it here to SELECT/INSERT only, keeping the
-- audit log immutable to the application.
REVOKE UPDATE, DELETE ON log FROM jogo_acoes_app;
