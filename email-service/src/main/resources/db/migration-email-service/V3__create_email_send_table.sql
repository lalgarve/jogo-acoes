-- Spec 05-031: one row per accepted POST /emails, written in the same transaction that publishes
-- to the send queue. id is the message's correlationId (and the SES message tag), so SES events
-- (Iteration 6) can be tied back to it. templateData is deliberately not stored: it may carry
-- personal data.
CREATE TABLE email_send (
    id UUID PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    template_id BIGINT NOT NULL REFERENCES email_template (id),
    recipient_email VARCHAR(320) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
