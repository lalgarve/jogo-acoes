package dev.leilaalgarve.layerexamples;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

/** Breaks onlyRepositoriesUseDatabaseApis, from a controller. */
@RestController
public class ControllerUsingJdbc {

    final JdbcTemplate jdbc;

    public ControllerUsingJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
