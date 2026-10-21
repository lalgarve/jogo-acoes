package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import io.cucumber.java.After;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Spec 05-048: after each scenario, lets every import still running end (a held server is
 * released, so it answers or the download times out) and deletes every disposable domain
 * repository with its domains -- the suite starts each scenario without repositories. The initial
 * one (spec.md, "Repositório inicial") is never there: since spec 05-053 only staging and
 * production register it. Guarded by the tables'
 * existence only so the scenarios written before the migration fail for their own reason.
 */
public class DisposableDomainCleanupHooks {

    private static final Duration MAX_WAIT = Duration.ofSeconds(60);

    private final JdbcTemplate jdbcTemplate;
    private final DisposableListFiles listFiles;

    public DisposableDomainCleanupHooks(JdbcTemplate jdbcTemplate, DisposableListFiles listFiles) {
        this.jdbcTemplate = jdbcTemplate;
        this.listFiles = listFiles;
    }

    @After
    public void cleanUp() throws InterruptedException {
        listFiles.releaseHeldServers();
        if (exists("email_service.batch_job_execution")) {
            Instant deadline = Instant.now().plus(MAX_WAIT);
            while (running() > 0 && Instant.now().isBefore(deadline)) {
                Thread.sleep(100);
            }
        }
        if (exists("email_service.disposable_domain_source")) {
            jdbcTemplate.update("DELETE FROM email_service.disposable_domain_source");
        }
    }

    private int running() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_service.batch_job_execution WHERE status IN ('STARTING', 'STARTED', 'STOPPING')",
                Integer.class);
        return count == null ? 0 : count;
    }

    private boolean exists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
    }
}
