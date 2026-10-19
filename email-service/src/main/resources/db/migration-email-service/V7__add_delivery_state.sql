-- Spec 05-046: POST /emails only records the send; email-service publishes it to the send queue
-- in the background and handles delivery errors from the dead-letter queue. The row is the work
-- item: status, attempts, last error, next attempt. sender_address/ses_template_name are copied
-- when the send is accepted, so publishing uses what was valid then. template_data is kept only
-- while the send is PENDING (it may carry personal data, spec 05-031).
ALTER TABLE email_send
    ADD COLUMN status VARCHAR(16),
    ADD COLUMN sender_address VARCHAR(320),
    ADD COLUMN ses_template_name VARCHAR(510),
    ADD COLUMN template_data JSONB,
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error_type VARCHAR(32),
    ADD COLUMN last_error TEXT,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD COLUMN queued_at TIMESTAMP;

-- Every send before this migration was published inside its own request (spec 05-031).
UPDATE email_send s
SET status = 'QUEUED',
    sender_address = COALESCE((SELECT c.address FROM client_sender c WHERE c.client_id = s.client_id), ''),
    ses_template_name = (SELECT t.ses_template_name FROM email_template t WHERE t.id = s.template_id),
    next_attempt_at = s.created_at,
    queued_at = s.created_at;

ALTER TABLE email_send
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN sender_address SET NOT NULL,
    ALTER COLUMN ses_template_name SET NOT NULL,
    ALTER COLUMN next_attempt_at SET NOT NULL,
    ADD CONSTRAINT email_send_status_check CHECK (status IN ('PENDING', 'QUEUED', 'FAILED'));

CREATE INDEX email_send_pending_due_idx ON email_send (next_attempt_at) WHERE status = 'PENDING';

-- Spec 05-046: when email-service last recreated the template on SES after a TEMPLATE_MISSING
-- delivery error -- a burst of such errors within the recreate window recreates it once.
ALTER TABLE email_template ADD COLUMN ses_recreated_at TIMESTAMP;
