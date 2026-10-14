package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Reads (and, to simulate time passing, backdates) the delivery state of an {@code email_send}
 * row straight from PostgreSQL (spec 05-046): status, attempts, last error type, whether the
 * template data is still kept. Plain SQL so the steps check the columns themselves.
 */
@Component
public class EmailSendRows {

    private final JdbcTemplate jdbcTemplate;

    public EmailSendRows(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> state(UUID id) {
        return jdbcTemplate.queryForMap(
                "select status, attempts, last_error_type, last_error, template_data is not null as keeps_template_data"
                        + " from email_send where id = ?", id);
    }

    /** The send was requested {@code hours} ago, and is due for another attempt now. */
    public void requestedHoursAgo(UUID id, int hours) {
        jdbcTemplate.update("update email_send set created_at = created_at - make_interval(hours => ?),"
                + " next_attempt_at = now() where id = ?", hours, id);
    }
}
