-- Fixed role catalog (see docs/diagrams/der.md) — ROLE is a table, not an enum, so new
-- roles can be added later without a code change, but today's two are known up front.
INSERT INTO role (name) VALUES ('ADMINISTRATOR'), ('PLAYER');
