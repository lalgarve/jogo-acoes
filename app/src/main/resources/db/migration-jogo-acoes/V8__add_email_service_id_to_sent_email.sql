-- Spec 05-034: the id email-service returns when it accepts a send (202), so a send can be
-- followed from app's record to email-service's own without either service reading the other's
-- database. Null only for EmailSenders that don't go through email-service (stub, and SQS until
-- spec 05-031 replaces it). sent_email stays append-only (V4): the id is known before the insert.
ALTER TABLE sent_email ADD COLUMN email_service_id UUID;
