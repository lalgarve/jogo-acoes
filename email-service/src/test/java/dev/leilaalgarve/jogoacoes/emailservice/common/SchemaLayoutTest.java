package dev.leilaalgarve.jogoacoes.emailservice.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every table this module owns lives in its own schema, never in {@code public}, with Flyway's
 * history named after the schema (spec 05-032) -- so every service can share one PostgreSQL
 * instance, even one database, without any name colliding. Checked against the real database
 * after Flyway ran, not against configuration: a table created in the wrong place fails here.
 *
 * <p>A docker volume created before spec 05-032 still has the old tables in {@code public};
 * recreate it ({@code docker compose down -v}) if this test complains about leftovers there.
 */
@SpringBootTest
class SchemaLayoutTest {

    private static final String SCHEMA = "email_service";
    private static final String HISTORY_TABLE = "email_service_schema_history";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tablesLiveInTheModuleSchema() {
        assertThat(tablesIn(SCHEMA))
                .contains(HISTORY_TABLE, "email_template", "client_sender", "email_send");
    }

    @Test
    void nothingIsCreatedInThePublicSchema() {
        assertThat(tablesIn("public")).isEmpty();
        assertThat(jdbcTemplate.queryForList(
                "select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace"
                        + " where n.nspname = 'public' and c.relkind = 'S'",
                String.class)).isEmpty();
    }

    @Test
    void flywayHistoryUsesTheSchemaNamedTableOnly() {
        assertThat(jdbcTemplate.queryForList(
                "select table_schema || '.' || table_name from information_schema.tables"
                        + " where table_name = 'flyway_schema_history'",
                String.class)).isEmpty();
    }

    private List<String> tablesIn(String schema) {
        return jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = ?",
                String.class, schema);
    }
}
