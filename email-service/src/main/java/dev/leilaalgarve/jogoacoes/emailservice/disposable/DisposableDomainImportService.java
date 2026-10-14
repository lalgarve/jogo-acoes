package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImport;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImportStarted;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImportSummary;
import dev.leilaalgarve.jogoacoes.emailservice.disposable.batch.ImportContextKeys;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Starts and reads the import executions (spec 05-048). One execution per repository, each a
 * new job instance; everything about an execution is read back from Spring Batch's own metadata.
 * Starting is synchronized so two requests at the same moment cannot both pass the "already
 * running" check: the execution exists (STARTING) before {@link JobOperator#start} returns.
 */
@Service
public class DisposableDomainImportService {

    private final DisposableDomainSourceRepository sources;
    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job importJob;
    private final JdbcTemplate jdbcTemplate;

    public DisposableDomainImportService(DisposableDomainSourceRepository sources, JobOperator jobOperator,
                                         JobRepository jobRepository, Job importDisposableDomainsJob,
                                         JdbcTemplate jdbcTemplate) {
        this.sources = sources;
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.importJob = importDisposableDomainsJob;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param sourceId one repository, or {@code null} for every repository
     * @throws DisposableDomainSourceNotFoundException when {@code sourceId} names no repository
     * @throws DisposableDomainImportAlreadyRunningException when one of them is being imported --
     *                                                       then nothing is started
     */
    public synchronized DisposableDomainImportStarted start(Long sourceId) {
        List<DisposableDomainSource> toImport = sourceId == null
                ? sources.findAll(Sort.by("id"))
                : List.of(sources.findById(sourceId).orElseThrow(() -> new DisposableDomainSourceNotFoundException(sourceId)));

        Set<Long> running = jobRepository.findRunningJobExecutions(ImportContextKeys.JOB_NAME).stream()
                .map(DisposableDomainImportService::sourceIdOf)
                .collect(Collectors.toSet());
        toImport.stream().filter(source -> running.contains(source.getId())).findFirst().ifPresent(source -> {
            throw new DisposableDomainImportAlreadyRunningException(source.getId());
        });

        DisposableDomainImportStarted started = new DisposableDomainImportStarted();
        for (DisposableDomainSource source : toImport) {
            started.addImportsItem(summaryOf(launch(source.getId())));
        }
        return started;
    }

    /** @throws DisposableDomainImportNotFoundException when no import execution has this id */
    public DisposableDomainImport get(long executionId) {
        JobExecution execution;
        try {
            execution = jobRepository.getJobExecution(executionId);
        } catch (EmptyResultDataAccessException e) {
            // Spring Batch 6's JDBC repository throws instead of returning null
            execution = null;
        }
        if (execution == null || !ImportContextKeys.JOB_NAME.equals(execution.getJobInstance().getJobName())) {
            throw new DisposableDomainImportNotFoundException(executionId);
        }
        Optional<StepExecution> importStep = step(execution, ImportContextKeys.IMPORT_STEP);
        return new DisposableDomainImport()
                .executionId(execution.getId())
                .sourceId(sourceIdOf(execution))
                .status(execution.getStatus().name())
                .startTime(toOffset(execution.getStartTime()))
                .endTime(toOffset(execution.getEndTime()))
                .read(importStep.map(StepExecution::getReadCount).orElse(0L))
                .skipped(importStep.map(StepExecution::getProcessSkipCount).orElse(0L))
                .added(execution.getExecutionContext().getLong(ImportContextKeys.ADDED, 0L))
                .removed(execution.getExecutionContext().getLong(ImportContextKeys.REMOVED, 0L))
                .exitMessage(exitMessageOf(execution));
    }

    /**
     * The latest execution of a repository, for the repository list; empty before the first.
     * Found with one query on Spring Batch's tables (its own API would load every instance).
     */
    Optional<DisposableDomainImportSummary> lastImportOf(long sourceId) {
        List<Long> latest = jdbcTemplate.queryForList(
                "SELECT max(e.job_execution_id) FROM batch_job_execution e"
                        + " JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id"
                        + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
                        + " WHERE i.job_name = ? AND p.parameter_name = ? AND p.parameter_value = ?",
                Long.class, ImportContextKeys.JOB_NAME, ImportContextKeys.SOURCE_ID, String.valueOf(sourceId));
        return latest.stream().filter(java.util.Objects::nonNull).findFirst()
                .map(jobRepository::getJobExecution)
                .map(DisposableDomainImportService::summaryOf);
    }

    private JobExecution launch(long sourceId) {
        JobParameters parameters = new JobParametersBuilder()
                .addLong(ImportContextKeys.SOURCE_ID, sourceId)
                .addString(ImportContextKeys.REQUESTED_AT, Instant.now() + "/" + UUID.randomUUID())
                .toJobParameters();
        try {
            return jobOperator.start(importJob, parameters);
        } catch (Exception e) {
            // A new instance every time (requestedAt): none of the checked "already exists/complete"
            // cases can happen.
            throw new IllegalStateException("Could not start the import of repository " + sourceId, e);
        }
    }

    private static DisposableDomainImportSummary summaryOf(JobExecution execution) {
        return new DisposableDomainImportSummary()
                .executionId(execution.getId())
                .sourceId(sourceIdOf(execution))
                .status(execution.getStatus().name())
                .endTime(toOffset(execution.getEndTime()))
                .exitMessage(exitMessageOf(execution));
    }

    private static long sourceIdOf(JobExecution execution) {
        Long sourceId = execution.getJobParameters().getLong(ImportContextKeys.SOURCE_ID);
        return sourceId == null ? -1 : sourceId;
    }

    private static Optional<StepExecution> step(JobExecution execution, String name) {
        return execution.getStepExecutions().stream().filter(step -> step.getStepName().equals(name)).findFirst();
    }

    /**
     * Why it failed -- the first line of the failed step's exit description, which Spring Batch
     * fills with the exception's stack trace -- or UNCHANGED when the list had not changed.
     */
    private static String exitMessageOf(JobExecution execution) {
        if (step(execution, ImportContextKeys.DOWNLOAD_STEP)
                .filter(step -> ImportContextKeys.UNCHANGED.equals(step.getExitStatus().getExitCode()))
                .isPresent()) {
            return ImportContextKeys.UNCHANGED;
        }
        if (execution.getStatus() != BatchStatus.FAILED) {
            return null;
        }
        return execution.getStepExecutions().stream()
                .filter(step -> step.getStatus() == BatchStatus.FAILED)
                .map(step -> step.getExitStatus().getExitDescription())
                .filter(description -> description != null && !description.isBlank())
                .map(DisposableDomainImportService::firstLineWithoutExceptionClass)
                .findFirst()
                .orElse(execution.getExitStatus().getExitDescription());
    }

    private static String firstLineWithoutExceptionClass(String stackTrace) {
        String firstLine = stackTrace.lines().findFirst().orElse(stackTrace);
        int separator = firstLine.indexOf(": ");
        return separator < 0 ? firstLine : firstLine.substring(separator + 2);
    }

    private static OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
