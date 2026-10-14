-- Spec 05-047: recipient domains known to accept e-mail -- one list for the whole service, not per
-- client. A domain lands here when DNS approves it (approved_by = the record that did: MX, A or
-- AAAA) and is never looked up again. 253 = the longest domain name. SEED rows come from this
-- migration, not from DNS.
CREATE TABLE approved_domain (
    domain VARCHAR(253) PRIMARY KEY,
    approved_by VARCHAR(8) NOT NULL,
    approved_at TIMESTAMP NOT NULL
);

-- The SES mailbox simulator (success@, bounce@, ...), used by the tests in every environment --
-- in docker the test DNS server does not know it (Leila, 2026-10-14).
INSERT INTO approved_domain (domain, approved_by, approved_at)
VALUES ('simulator.amazonses.com', 'SEED', CURRENT_TIMESTAMP);
