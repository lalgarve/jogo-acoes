-- Spec 05-031: one sender address per client, set by operations (scripts/set-email-sender.sh),
-- never through the HTTP API. client_id is the same client name email_template uses (the
-- --client an API key was issued for, spec 05-030). 320 = the longest valid e-mail address.
CREATE TABLE client_sender (
    client_id VARCHAR(255) PRIMARY KEY,
    address VARCHAR(320) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
