CREATE TABLE sent_email (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user (id),
    email VARCHAR(255) NOT NULL,
    link VARCHAR(2048) NOT NULL,
    template VARCHAR(50) NOT NULL,
    sent_at TIMESTAMP NOT NULL
);

-- Same rationale as V2's log table: append-only send record, immutable to the application.
REVOKE UPDATE, DELETE ON sent_email FROM jogo_acoes_app;
