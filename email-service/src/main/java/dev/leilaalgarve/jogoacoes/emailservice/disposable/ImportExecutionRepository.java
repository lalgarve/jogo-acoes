package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import dev.leilaalgarve.jogoacoes.emailservice.disposable.batch.ImportContextKeys;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;

/**
 * The one query on Spring Batch's own tables (specs 05-048 and 05-063); everything else about an
 * execution goes through Spring Batch's API.
 */
@Repository
public class ImportExecutionRepository {

    private final JdbcClient jdbc;

    public ImportExecutionRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** The latest import execution of a repository; empty before the first. Spring Batch's API would load every instance. */
    public Optional<Long> latestExecutionIdOf(long sourceId) {
        return jdbc.sql("SELECT max(e.job_execution_id) FROM batch_job_execution e"
                        + " JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id"
                        + " JOIN batch_job_execution_params p ON p.job_execution_id = e.job_execution_id"
                        + " WHERE i.job_name = ? AND p.parameter_name = ? AND p.parameter_value = ?")
                .params(ImportContextKeys.JOB_NAME, ImportContextKeys.SOURCE_ID, String.valueOf(sourceId))
                .query(Long.class).list().stream()
                .filter(Objects::nonNull)
                .findFirst();
    }
}
