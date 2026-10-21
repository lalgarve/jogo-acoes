package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImport;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImportSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-054: the imports run one at a time. Importing every repository used to start one job
 * per repository at once, and PostgreSQL now and then cancelled one of them while Spring Batch
 * recorded a step. The lists come from the S3 of the Compose LocalStack, like the other import
 * tests. Several rounds, each with a changed list, because the overlap that broke it depended on
 * timing. Starts and ends without repositories, like the Cucumber suite.
 */
@SpringBootTest
class DisposableDomainImportQueueTest {

    private static final String LIST_URL_PREFIX = "http://localhost:4566/disposable-lists/queue-test-";
    private static final int REPOSITORIES = 3;
    private static final int ROUNDS = 5;

    @Autowired
    private DisposableDomainImportService importService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    @AfterEach
    void noRepositories() {
        jdbcTemplate.update("DELETE FROM email_service.disposable_domain_source");
    }

    @Test
    void importingEveryRepositoryRunsOneImportAtATimeAndEveryOneCompletes() throws Exception {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < REPOSITORIES; i++) {
            urls.add(LIST_URL_PREFIX + UUID.randomUUID() + ".conf");
            registerSource(urls.get(i));
        }

        for (int round = 0; round < ROUNDS; round++) {
            for (String url : urls) {
                put(url, "round-" + round + ".test\nalways.test\n");
            }

            List<Long> executionIds = importService.start(null).getImports().stream()
                    .map(DisposableDomainImportSummary::getExecutionId)
                    .toList();

            assertThat(executionIds).hasSize(REPOSITORIES);
            for (long executionId : executionIds) {
                DisposableDomainImport finished = awaitEnd(executionId);
                assertThat(finished.getStatus()).as("round %d, execution %d: %s", round, executionId,
                        finished.getExitMessage()).isEqualTo("COMPLETED");
            }
            assertNoneStartedBeforeThePreviousEnded(round, executionIds);
        }
    }

    private void assertNoneStartedBeforeThePreviousEnded(int round, List<Long> executionIds) {
        List<Timestamp[]> runs = new ArrayList<>();
        for (long executionId : executionIds) {
            runs.add(jdbcTemplate.queryForObject(
                    "SELECT start_time, end_time FROM email_service.batch_job_execution WHERE job_execution_id = ?",
                    (row, n) -> new Timestamp[] {row.getTimestamp("start_time"), row.getTimestamp("end_time")},
                    executionId));
        }
        runs.sort((a, b) -> a[0].compareTo(b[0]));
        for (int i = 1; i < runs.size(); i++) {
            assertThat(runs.get(i)[0]).as("round %d: an import started before the previous one ended", round)
                    .isAfterOrEqualTo(runs.get(i - 1)[1]);
        }
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

    private void put(String url, String body) throws IOException, InterruptedException {
        send(HttpRequest.newBuilder(URI.create("http://localhost:4566/disposable-lists"))
                .PUT(HttpRequest.BodyPublishers.noBody()));
        send(HttpRequest.newBuilder(URI.create(url)).PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    private void send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private void registerSource(String url) {
        jdbcTemplate.update("INSERT INTO email_service.disposable_domain_source (url, description, created_at)"
                + " VALUES (?, 'Queue test', now())", url);
    }
}
