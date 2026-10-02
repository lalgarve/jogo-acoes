-- See specs/05-025-servico-email-templates/data-model.md for field-by-field rationale.
CREATE TABLE email_template (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    variables_schema JSONB,
    ses_template_name VARCHAR(510) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (client_id, name)
);
