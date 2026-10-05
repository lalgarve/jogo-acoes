package dev.leilaalgarve.jogoacoes.emailservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The api-key library ships no auto-configuration (spec 05-030, plan.md, "Contexto técnico"):
 * its packages are registered here by name. {@code @EntityScan}/{@code @EnableJpaRepositories}
 * replace the default scan instead of adding to it, so this service's own package is listed too.
 * {@code ApiKey} lives in schema {@code api_key}, set in META-INF/orm.xml.
 */
@SpringBootApplication(scanBasePackages = {
        "dev.leilaalgarve.jogoacoes.emailservice",
        "dev.leilaalgarve.apikey.core",
        "dev.leilaalgarve.apikey.validation"
})
@EntityScan({"dev.leilaalgarve.jogoacoes.emailservice", "dev.leilaalgarve.apikey.core"})
@EnableJpaRepositories({"dev.leilaalgarve.jogoacoes.emailservice", "dev.leilaalgarve.apikey.core"})
public class EmailServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailServiceApplication.class, args);
    }
}
