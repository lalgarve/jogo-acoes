package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * The only owner of the {@code disposable_domain} table (specs 05-048 and 05-063). JDBC, not JPA:
 * whole lists come in by the batch and leave in bulk, one statement each, never one entity at a
 * time.
 */
@Repository
public class DisposableDomainRepository {

    private final DataSource dataSource;
    private final JdbcClient jdbc;

    public DisposableDomainRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = JdbcClient.create(dataSource);
    }

    public long countBySource(long sourceId) {
        return jdbc.sql("SELECT count(*) FROM disposable_domain WHERE source_id = ?")
                .param(sourceId)
                .query(Long.class).single();
    }

    /** Whether any of these domains is in any repository's list. One indexed query. */
    public boolean existsAnyOf(List<String> domains) {
        String placeholders = String.join(", ", domains.stream().map(domain -> "?").toList());
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM disposable_domain WHERE domain IN ("
                        + placeholders + "))")
                .params(domains)
                .query(Boolean.class).single());
    }

    /** Removes the repository's domains the given import execution did not see; returns how many. */
    public long deleteNotSeenIn(long sourceId, long executionId) {
        return jdbc.sql("DELETE FROM disposable_domain WHERE source_id = ? AND last_seen_execution_id <> ?")
                .params(sourceId, executionId)
                .update();
    }

    /**
     * The import step's writer: upserts each domain of the repository, marked as seen by the
     * execution, so writing the same list again never duplicates a domain.
     */
    public ItemWriter<String> batchWriter(long sourceId, long executionId) {
        JdbcBatchItemWriter<String> writer = new JdbcBatchItemWriter<>();
        writer.setDataSource(dataSource);
        writer.setSql("INSERT INTO disposable_domain (source_id, domain, last_seen_execution_id) VALUES (?, ?, ?)"
                + " ON CONFLICT (source_id, domain) DO UPDATE SET last_seen_execution_id = EXCLUDED.last_seen_execution_id");
        writer.setItemPreparedStatementSetter((domain, statement) -> {
            statement.setLong(1, sourceId);
            statement.setString(2, domain);
            statement.setLong(3, executionId);
        });
        writer.afterPropertiesSet();
        return writer;
    }
}
