package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Last step of the import (spec 05-048), only after the whole list was written: removes the
 * repository's domains this execution did not see, and records the list's SHA-256 and -- when a
 * domain came or went -- the time of the last database update. Until here nothing was removed,
 * so an execution that stops earlier only ever adds.
 */
@Component
public class PruneDomainsTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    public PruneDomainsTasklet(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        long sourceId = (Long) chunkContext.getStepContext().getJobParameters().get(ImportContextKeys.SOURCE_ID);
        long executionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
        ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();

        long domainsAfterImport = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM disposable_domain WHERE source_id = ?", Long.class, sourceId);
        long added = domainsAfterImport - jobContext.getLong(ImportContextKeys.DOMAINS_BEFORE);
        long removed = jdbcTemplate.update(
                "DELETE FROM disposable_domain WHERE source_id = ? AND last_seen_execution_id <> ?", sourceId, executionId);

        if (added > 0 || removed > 0) {
            jdbcTemplate.update("UPDATE disposable_domain_source SET last_updated_at = ?, content_sha256 = ? WHERE id = ?",
                    LocalDateTime.now(ZoneOffset.UTC), jobContext.getString(ImportContextKeys.SHA256), sourceId);
        } else {
            jdbcTemplate.update("UPDATE disposable_domain_source SET content_sha256 = ? WHERE id = ?",
                    jobContext.getString(ImportContextKeys.SHA256), sourceId);
        }
        jobContext.putLong(ImportContextKeys.ADDED, added);
        jobContext.putLong(ImportContextKeys.REMOVED, removed);
        Files.deleteIfExists(Path.of(jobContext.getString(ImportContextKeys.LIST_FILE)));
        return RepeatStatus.FINISHED;
    }
}
