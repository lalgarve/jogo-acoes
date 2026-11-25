-- Spec 05-059: POST /emails is idempotent with the Idempotency-Key header, per calling client.
-- The send row is kept for good, so it holds the key for as long as the spec asks (24 hours at
-- least). request_hash is the SHA-256, in hexadecimal, of the request body with its keys sorted:
-- template_data is cleared once the send is QUEUED, so the body itself can't be compared later,
-- and the hash carries no personal data.
--
-- NOT NULL from the start: the system is in pre-production (constitution), and a local database
-- with older sends is recreated (docker compose down -v) instead of back-filled.
ALTER TABLE email_send
    ADD COLUMN idempotency_key VARCHAR(255) NOT NULL,
    ADD COLUMN request_hash CHAR(64) NOT NULL,
    ADD CONSTRAINT email_send_client_idempotency_key_uq UNIQUE (client_id, idempotency_key);
