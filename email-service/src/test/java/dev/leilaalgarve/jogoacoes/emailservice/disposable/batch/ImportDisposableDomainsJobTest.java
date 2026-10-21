package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImport;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-048: the import job against the real PostgreSQL and a list served by the S3 of the
 * Compose LocalStack -- a list the size of the real ones, and an import stopped in the middle and
 * asked for again. The list cap is raised here; the Cucumber suite keeps a small one.
 */
@SpringBootTest(properties = "email.disposable-domains.max-size=10MB")
// The only class with this configuration: closing its context frees its database connections
// for the rest of the suite (each cached context keeps a pool of its own).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportDisposableDomainsJobTest {

    private static final String LIST_URL_PREFIX = "http://localhost:4566/disposable-lists/job-test-";
    private static final int LINES = 100_000;

    @Autowired
    private DisposableDomainImportService importService;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void deleteSources() {
        jdbcTemplate.update("DELETE FROM email_service.disposable_domain_source WHERE url LIKE ?", LIST_URL_PREFIX + "%");
    }

    @Test
    void aListOfOneHundredThousandLinesIsImportedInAFewSeconds() throws Exception {
        long sourceId = registerSource(uploadList(LINES));

        Instant start = Instant.now();
        DisposableDomainImport finished = awaitEnd(startImport(sourceId));

        assertThat(finished.getStatus()).as(String.valueOf(finished.getExitMessage())).isEqualTo("COMPLETED");
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(30));
        assertThat(finished.getRead()).isEqualTo(LINES);
        assertThat(finished.getAdded()).isEqualTo(LINES);
        assertThat(domainCount(sourceId)).isEqualTo(LINES);
    }

    /**
     * The service going down in the middle is played by {@link JobOperator#stop}, not by killing
     * the JVM (plan.md, "Riscos e trade-offs"): the execution ends STOPPED with part of the list
     * written, and a new request finishes the work without duplicates.
     */
    @Test
    void anImportStoppedInTheMiddleIsFinishedByTheNextOne() throws Exception {
        long sourceId = registerSource(uploadList(LINES));
        jdbcTemplate.update(
                "INSERT INTO email_service.disposable_domain (source_id, domain, last_seen_execution_id) VALUES (?, 'gone.test', 0)",
                sourceId);

        long stoppedId = startImport(sourceId);
        awaitImportStepWrote(stoppedId);
        jobOperator.stop(stoppedId);
        DisposableDomainImport stopped = awaitEnd(stoppedId);
        assertThat(stopped.getStatus()).isEqualTo("STOPPED");
        assertThat(domainCount(sourceId)).isLessThan(LINES + 1);
        assertThat(domainExists(sourceId, "gone.test")).as("nothing is removed before the import ends").isTrue();

        DisposableDomainImport finished = awaitEnd(startImport(sourceId));

        assertThat(finished.getStatus()).as(String.valueOf(finished.getExitMessage())).isEqualTo("COMPLETED");
        assertThat(finished.getRemoved()).isEqualTo(1);
        assertThat(domainCount(sourceId)).isEqualTo(LINES);
        assertThat(domainExists(sourceId, "gone.test")).isFalse();
    }

    private long startImport(long sourceId) {
        return importService.start(sourceId).getImports().getFirst().getExecutionId();
    }

    private void awaitImportStepWrote(long executionId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            Integer written = jdbcTemplate.queryForObject(
                    "SELECT coalesce(max(write_count), 0) FROM email_service.batch_step_execution"
                            + " WHERE job_execution_id = ? AND step_name = 'importStep'",
                    Integer.class, executionId);
            if (written != null && written > 0) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("importStep of execution " + executionId + " never wrote a chunk");
    }

    private DisposableDomainImport awaitEnd(long executionId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(60);
        while (true) {
            DisposableDomainImport execution = importService.get(executionId);
            if (!Set.of("STARTING", "STARTED", "STOPPING").contains(execution.getStatus())
                    || Instant.now().isAfter(deadline)) {
                return execution;
            }
            Thread.sleep(50);
        }
    }

    private String uploadList(int lines) throws IOException, InterruptedException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            content.append("disposable-").append(i).append(".test\n");
        }
        String url = LIST_URL_PREFIX + UUID.randomUUID() + ".conf";
        put("http://localhost:4566/disposable-lists", "");
        put(url, content.toString());
        return url;
    }

    private void put(String url, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private long registerSource(String url) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO email_service.disposable_domain_source (url, description, created_at)"
                        + " VALUES (?, 'Job test', now()) RETURNING id",
                Long.class, url);
        return id == null ? -1 : id;
    }

    private int domainCount(long sourceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_service.disposable_domain WHERE source_id = ?", Integer.class, sourceId);
        return count == null ? 0 : count;
    }

    private boolean domainExists(long sourceId, String domain) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM email_service.disposable_domain WHERE source_id = ? AND domain = ?)",
                Boolean.class, sourceId, domain));
    }
}
