-- specs/05-003-desacoplamento-login-link: the login link mechanism becomes a generic
-- LinkRecord any module can use (LinkRouter dispatches by service_key). userId/email stay
-- real columns (queryable/indexable, unlike a fully opaque payload), but neither has a FK
-- anymore -- link doesn't know what app_user or participation are. Whatever a specific
-- LinkHandler needs beyond userId/email (e.g. a participation id) goes into extra_json,
-- opaque to this table. No data migration needed -- the system never reached production.

ALTER TABLE login_link RENAME TO link_record;

ALTER TABLE link_record DROP CONSTRAINT IF EXISTS login_link_user_id_fkey;
ALTER TABLE link_record DROP CONSTRAINT IF EXISTS login_link_participation_id_fkey;
ALTER TABLE link_record DROP COLUMN participation_id;

ALTER TABLE link_record ADD COLUMN service_key VARCHAR(64) NOT NULL DEFAULT 'login';
ALTER TABLE link_record ALTER COLUMN service_key DROP DEFAULT;
ALTER TABLE link_record ADD COLUMN extra_json TEXT;

ALTER TABLE login_session RENAME COLUMN login_link_id TO link_record_id;
ALTER TABLE login_session DROP CONSTRAINT IF EXISTS login_session_login_link_id_fkey;
ALTER TABLE login_session ADD CONSTRAINT login_session_link_record_id_fkey FOREIGN KEY (link_record_id) REFERENCES link_record (id);

-- LOGIN_SESSION lives in the `link` module too (spec 05-002) -- its user_id loses the FK to
-- app_user for the same reason LinkRecord's does: `link` doesn't know what `login`'s User even
-- is. LoginLinkSessionService (in `login`) is the only writer, and already resolves/validates
-- the id before saving.
ALTER TABLE login_session DROP CONSTRAINT IF EXISTS login_session_user_id_fkey;
