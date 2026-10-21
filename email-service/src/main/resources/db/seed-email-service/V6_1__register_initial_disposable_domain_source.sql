-- Spec 05-048, "Repositório inicial": the list every staging and production database starts with.
-- Spec 05-053: only there -- applied together with db/migration-email-service by whoever applies
-- staging/production's migrations (config-repo/email-service-staging.yml, -production.yml), never
-- in docker or in the tests. Versioned right after V6, which creates the table, and sharing its
-- Flyway history. ON CONFLICT: a database migrated before spec 05-053 already has it (from V6).
INSERT INTO disposable_domain_source (url, description, created_at)
VALUES ('https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/main/disposable_email_blocklist.conf',
        'disposable-email-domains, lista principal', CURRENT_TIMESTAMP)
ON CONFLICT (url) DO NOTHING;
