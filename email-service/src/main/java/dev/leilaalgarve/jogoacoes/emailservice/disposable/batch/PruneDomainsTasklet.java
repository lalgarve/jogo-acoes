package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainRepository;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainSourceRepository;
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

    private final DisposableDomainSourceRepository sources;
    private final DisposableDomainRepository disposableDomains;

    public PruneDomainsTasklet(DisposableDomainSourceRepository sources, DisposableDomainRepository disposableDomains) {
        this.sources = sources;
        this.disposableDomains = disposableDomains;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
        long sourceId = (Long) chunkContext.getStepContext().getJobParameters().get(ImportContextKeys.SOURCE_ID);
        long executionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
        ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();

        long domainsAfterImport = disposableDomains.countBySource(sourceId);
        long added = domainsAfterImport - jobContext.getLong(ImportContextKeys.DOMAINS_BEFORE);
        long removed = disposableDomains.deleteNotSeenIn(sourceId, executionId);

        if (added > 0 || removed > 0) {
            sources.markUpdated(sourceId, LocalDateTime.now(ZoneOffset.UTC), jobContext.getString(ImportContextKeys.SHA256));
        } else {
            sources.updateContentHash(sourceId, jobContext.getString(ImportContextKeys.SHA256));
        }
        jobContext.putLong(ImportContextKeys.ADDED, added);
        jobContext.putLong(ImportContextKeys.REMOVED, removed);
        Files.deleteIfExists(Path.of(jobContext.getString(ImportContextKeys.LIST_FILE)));
        return RepeatStatus.FINISHED;
    }
}
