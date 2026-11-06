package dev.leilaalgarve.layerexamples;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Allowed: a repository uses a database API; returns entities. */
@Repository
public class JdbcExampleRepository {

    final JdbcClient jdbc;

    public JdbcExampleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    ExampleEntity find() {
        return new ExampleEntity();
    }
}
