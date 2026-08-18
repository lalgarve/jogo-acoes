CREATE TABLE log (
    id BIGSERIAL PRIMARY KEY,
    related_object_id BIGINT NOT NULL,
    user_id BIGINT REFERENCES app_user (id),
    created_at TIMESTAMP NOT NULL,
    log_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL
);

-- No REVOKE here (unlike db/migration/V2): H2 in this profile has a single connection
-- role, not the jogo_acoes_admin/jogo_acoes_app split that Postgres environments use.
