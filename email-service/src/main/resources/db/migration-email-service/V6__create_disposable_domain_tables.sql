-- Spec 05-048: repositories of disposable e-mail domain lists, and the domains each one brought
-- (specs/05-048-importacao-listas-email-temporario/data-model.md).
CREATE TABLE disposable_domain_source (
    id                 BIGSERIAL PRIMARY KEY,
    url                VARCHAR(2048) NOT NULL UNIQUE,
    description        VARCHAR(255)  NOT NULL,
    -- last successful download, even when the list had not changed
    last_downloaded_at TIMESTAMP     NULL,
    -- last import that added or removed a domain of this repository
    last_updated_at    TIMESTAMP     NULL,
    -- of the list that updated the database last; tells whether a new download changed
    content_sha256     CHAR(64)      NULL,
    created_at         TIMESTAMP     NOT NULL
);

CREATE TABLE disposable_domain (
    source_id              BIGINT       NOT NULL REFERENCES disposable_domain_source (id) ON DELETE CASCADE,
    domain                 VARCHAR(253) NOT NULL,
    -- import execution that wrote or saw the domain last; no FK, so cleaning Spring Batch's
    -- metadata never touches this table
    last_seen_execution_id BIGINT       NOT NULL,
    PRIMARY KEY (source_id, domain)
);

-- POST /emails looks a recipient's domain up here on every send.
CREATE INDEX disposable_domain_domain_idx ON disposable_domain (domain);

-- Spec 05-053: the initial repository (spec 05-048, "Repositório inicial") is registered by
-- db/seed-email-service/, applied only in staging and production -- docker and the tests never
-- have it, so no test depends on a URL outside this project's control.
