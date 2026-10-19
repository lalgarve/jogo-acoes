package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Spec 05-048: the scheduled import starts every repository, and when one is still being
 * imported it logs and waits for the next run instead of failing. The cron itself is off in the
 * suite ("-"), so the method is called directly. Starts without repositories, like the Cucumber
 * suite (spec.md, "Repositório inicial").
 */
@SpringBootTest
class DisposableDomainImportSchedulerTest {

    @Autowired
    private DisposableDomainImportScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer heldServer;

    @AfterEach
    void releaseAndCleanUp() throws InterruptedException {
        release.countDown();
        if (heldServer != null) {
            heldServer.stop(0);
        }
        Instant deadline = Instant.now().plusSeconds(30);
        while (running() > 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        jdbcTemplate.update("DELETE FROM email_service.disposable_domain_source");
    }

    @Test
    void aScheduledRunWhileAnImportIsRunningStartsNothingAndDoesNotFail() throws IOException {
        jdbcTemplate.update("DELETE FROM email_service.disposable_domain_source");
        long sourceId = registerSource(heldServerUrl());

        scheduler.importEveryRepository();
        assertThatCode(scheduler::importEveryRepository).doesNotThrowAnyException();

        assertThat(executionsOf(sourceId)).isEqualTo(1);
    }

    private String heldServerUrl() throws IOException {
        heldServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        heldServer.createContext("/", exchange -> {
            try {
                release.await(30, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(404, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        heldServer.start();
        return "http://localhost:" + heldServer.getAddress().getPort() + "/held.conf";
    }

    private long registerSource(String url) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO email_service.disposable_domain_source (url, description, created_at)"
                        + " VALUES (?, 'Scheduler test', now()) RETURNING id", Long.class, url);
        return id == null ? -1 : id;
    }

    private int executionsOf(long sourceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_service.batch_job_execution_params"
                        + " WHERE parameter_name = 'sourceId' AND parameter_value = ?",
                Integer.class, String.valueOf(sourceId));
        return count == null ? 0 : count;
    }

    private int running() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_service.batch_job_execution WHERE status IN ('STARTING', 'STARTED', 'STOPPING')",
                Integer.class);
        return count == null ? 0 : count;
    }
}
