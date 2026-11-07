-- Spec 05-059: POST /emails takes an Idempotency-Key, unique per client. The key lives on the
-- send it created, as long as the send does (at least the 24 hours a send may take, spec 05-046).
-- request_hash (SHA-256 of templateName, recipientEmail and templateData) tells a repeat from a
-- different request with the same key; template_data itself is cleared once queued.
-- NOT NULL without a default: pre-production, a local database with older sends is recreated.
ALTER TABLE email_send
    ADD COLUMN idempotency_key VARCHAR(255) NOT NULL,
    ADD COLUMN request_hash CHAR(64) NOT NULL,
    ADD CONSTRAINT email_send_client_idempotency_key_uk UNIQUE (client_id, idempotency_key);
