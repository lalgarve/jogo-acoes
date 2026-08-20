CREATE TABLE sent_email (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_user (id),
    email VARCHAR(255) NOT NULL,
    link VARCHAR(2048) NOT NULL,
    template VARCHAR(50) NOT NULL,
    sent_at TIMESTAMP NOT NULL
);

-- No REVOKE here (unlike db/migration/V4) — see V2's note.
