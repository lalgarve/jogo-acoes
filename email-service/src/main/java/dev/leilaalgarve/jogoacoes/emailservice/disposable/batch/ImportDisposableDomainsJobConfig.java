package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.mapping.PassThroughLineMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.batch.autoconfigure.BatchTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Spring Batch job that imports one repository's list (spec 05-048, the course's batch
 * requirement): {@code downloadStep} (tasklet) → {@code importStep} (chunks of
 * {@value #CHUNK_SIZE} lines: {@link FlatFileItemReader} → {@link DisposableDomainLineProcessor}
 * → {@link JdbcBatchItemWriter}) → {@code pruneStep} (tasklet). A list identical to the last
 * imported one ends the job right after the download.
 *
 * <p>The writer upserts, so writing the same list again never duplicates a domain: an execution
 * that stopped in the middle is finished by the next request.
 */
@Configuration
public class ImportDisposableDomainsJobConfig {

    static final int CHUNK_SIZE = 1000;

    @Bean
    public Job importDisposableDomainsJob(JobRepository jobRepository, Step downloadStep, Step importStep,
                                          Step pruneStep) {
        return new JobBuilder(ImportContextKeys.JOB_NAME, jobRepository)
                .start(downloadStep).on(ImportContextKeys.UNCHANGED).end()
                .from(downloadStep).on("FAILED").fail()
                .from(downloadStep).on("*").to(importStep).next(pruneStep)
                .end()
                .listener(deleteListFileAfterJob())
                .build();
    }

    @Bean
    public Step downloadStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             DownloadListTasklet tasklet) {
        return new StepBuilder(ImportContextKeys.DOWNLOAD_STEP, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Step importStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                           FlatFileItemReader<String> disposableListReader,
                           JdbcBatchItemWriter<String> disposableDomainWriter) {
        return new ChunkOrientedStepBuilder<String, String>(ImportContextKeys.IMPORT_STEP, jobRepository, CHUNK_SIZE)
                .reader(disposableListReader)
                .processor(new DisposableDomainLineProcessor())
                .writer(disposableDomainWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skip(InvalidDisposableDomainLineException.class)
                .skipLimit(Long.MAX_VALUE)
                .build();
    }

    @Bean
    public Step pruneStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                          PruneDomainsTasklet tasklet) {
        return new StepBuilder(ImportContextKeys.PRUNE_STEP, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    /** Every line as it is: blank lines and comments are the processor's call, not the reader's. */
    @Bean
    @StepScope
    public FlatFileItemReader<String> disposableListReader(
            @Value("#{jobExecutionContext['" + ImportContextKeys.LIST_FILE + "']}") String listFile) {
        FlatFileItemReader<String> reader = new FlatFileItemReader<>(
                new FileSystemResource(listFile), new PassThroughLineMapper());
        reader.setName("disposableListReader");
        reader.setEncoding(StandardCharsets.UTF_8.name());
        reader.setComments(new String[0]);
        return reader;
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<String> disposableDomainWriter(
            DataSource dataSource,
            @Value("#{jobParameters['" + ImportContextKeys.SOURCE_ID + "']}") Long sourceId,
            @Value("#{stepExecution.jobExecutionId}") Long executionId) {
        JdbcBatchItemWriter<String> writer = new JdbcBatchItemWriter<>();
        writer.setDataSource(dataSource);
        writer.setSql("INSERT INTO disposable_domain (source_id, domain, last_seen_execution_id) VALUES (?, ?, ?)"
                + " ON CONFLICT (source_id, domain) DO UPDATE SET last_seen_execution_id = EXCLUDED.last_seen_execution_id");
        writer.setItemPreparedStatementSetter((domain, statement) -> {
            statement.setLong(1, sourceId);
            statement.setString(2, domain);
            statement.setLong(3, executionId);
        });
        return writer;
    }

    /** Imports run in the background: the request answers 202 as soon as the execution exists. */
    @Bean
    @BatchTaskExecutor
    public TaskExecutor disposableDomainImportTaskExecutor() {
        return new SimpleAsyncTaskExecutor("disposable-domain-import-");
    }

    /** The temporary list file is gone whatever way the execution ended. */
    private static JobExecutionListener deleteListFileAfterJob() {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                String listFile = jobExecution.getExecutionContext().getString(ImportContextKeys.LIST_FILE, null);
                if (listFile != null) {
                    try {
                        Files.deleteIfExists(Path.of(listFile));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        };
    }
}
