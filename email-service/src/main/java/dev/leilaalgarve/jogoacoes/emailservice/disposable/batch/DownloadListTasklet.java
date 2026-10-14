package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;

/**
 * First step of the import (spec 05-048): downloads the repository's list to a temporary file,
 * within {@code email.disposable-domains.download-timeout} and {@code max-size}, and records the
 * download time. Ends {@value ImportContextKeys#UNCHANGED} when the list is the one that updated
 * the database last (same SHA-256), so the job stops there. Any failure throws
 * {@link DisposableListDownloadException} before anything of the repository changes.
 */
@Component
public class DownloadListTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final Duration timeout;
    private final long maxBytes;
    private final HttpClient http;

    public DownloadListTasklet(JdbcTemplate jdbcTemplate,
                               @Value("${email.disposable-domains.download-timeout:30s}") Duration timeout,
                               @Value("${email.disposable-domains.max-size:10MB}") DataSize maxSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeout = timeout;
        this.maxBytes = maxSize.toBytes();
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        Map<String, Object> parameters = chunkContext.getStepContext().getJobParameters();
        long sourceId = (Long) parameters.get(ImportContextKeys.SOURCE_ID);
        Map<String, Object> source = jdbcTemplate.queryForMap(
                "SELECT url, content_sha256 FROM disposable_domain_source WHERE id = ?", sourceId);
        String url = (String) source.get("url");

        Path file = Files.createTempFile("disposable-list-", ".conf");
        String sha256;
        try {
            sha256 = download(url, file);
            if (!hasAnyEntry(file)) {
                throw new DisposableListDownloadException("The list is empty: " + url);
            }
        } catch (RuntimeException | IOException e) {
            Files.deleteIfExists(file);
            throw e;
        }

        jdbcTemplate.update("UPDATE disposable_domain_source SET last_downloaded_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC), sourceId);

        if (sha256.equals(source.get("content_sha256"))) {
            Files.deleteIfExists(file);
            contribution.setExitStatus(new ExitStatus(ImportContextKeys.UNCHANGED));
            return RepeatStatus.FINISHED;
        }
        ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
        jobContext.putString(ImportContextKeys.LIST_FILE, file.toString());
        jobContext.putString(ImportContextKeys.SHA256, sha256);
        jobContext.putLong(ImportContextKeys.DOMAINS_BEFORE, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM disposable_domain WHERE source_id = ?", Long.class, sourceId));
        return RepeatStatus.FINISHED;
    }

    /** Streams the body to {@code file}, never more than the limit, and returns its SHA-256. */
    private String download(String url, Path file) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new DisposableListDownloadException(
                            "The list download answered HTTP status " + response.statusCode() + ": " + url);
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (OutputStream out = new DigestOutputStream(Files.newOutputStream(file), digest)) {
                    copyWithinLimit(body, out, url);
                }
                return HexFormat.of().formatHex(digest.digest());
            }
        } catch (HttpTimeoutException e) {
            throw new DisposableListDownloadException(
                    "The list download timed out after " + timeout.toSeconds() + "s: " + url, e);
        } catch (IOException | IllegalArgumentException e) {
            throw new DisposableListDownloadException("The list could not be downloaded from " + url + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DisposableListDownloadException("The list download was interrupted: " + url, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void copyWithinLimit(InputStream in, OutputStream out, String url) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new DisposableListDownloadException(
                        "The list is larger than the limit of " + DataSize.ofBytes(maxBytes).toKilobytes() + " KB: " + url);
            }
            out.write(buffer, 0, read);
        }
    }

    /** A list with nothing but blank lines and comments is refused: importing it would empty the table. */
    private static boolean hasAnyEntry(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.map(line -> line.replace("﻿", "").strip())
                    .anyMatch(line -> !line.isEmpty() && !line.startsWith("#"));
        }
    }
}
