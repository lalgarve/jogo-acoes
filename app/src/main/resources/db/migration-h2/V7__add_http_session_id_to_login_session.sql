-- H2 mirror of db/migration/V7 -- identical, no dialect differences here.
ALTER TABLE login_session ADD COLUMN http_session_id VARCHAR(255) NOT NULL;
