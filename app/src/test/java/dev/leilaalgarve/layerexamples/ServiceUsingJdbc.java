package dev.leilaalgarve.layerexamples;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Breaks onlyRepositoriesUseDatabaseApis, from a service. */
@Service
public class ServiceUsingJdbc {

    final JdbcTemplate jdbc;

    public ServiceUsingJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
