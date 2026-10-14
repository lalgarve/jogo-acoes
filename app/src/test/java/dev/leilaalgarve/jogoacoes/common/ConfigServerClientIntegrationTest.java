package dev.leilaalgarve.jogoacoes.common;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.client.ConfigClientFailFastException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-042: the app's real src/main/resources/application.yml (target/classes, which the test
 * resources' own application.yml otherwise shadows) against docker-compose.yml's real
 * config-server -- the way the app starts outside the test suite. No profile set: the default,
 * docker, must be the one asked for.
 */
class ConfigServerClientIntegrationTest {

    /** The packaged application.yml, not the suite's; Surefire runs from the module directory. */
    private static final String MAIN_APPLICATION_YML = "spring.config.location=file:target/classes/application.yml";
    /** docker-compose.yml's test credentials for config-server; never a real secret. */
    private static final String USERNAME = "CONFIG_SERVER_USERNAME=config";
    private static final String PASSWORD = "CONFIG_SERVER_PASSWORD=config-docker-not-a-secret";

    @Nested
    @SpringBootTest(properties = {MAIN_APPLICATION_YML, USERNAME, PASSWORD})
    class WhenConfigServerIsUp {

        @Autowired
        private ConfigurableEnvironment environment;

        @Test
        void dockerConfigurationComesFromConfigServer() {
            // Skips "configurationProperties", Spring Boot's own view over every other source.
            PropertySource<?> source = StreamSupport.stream(environment.getPropertySources().spliterator(), false)
                    .filter(candidate -> !candidate.getName().equals("configurationProperties"))
                    .filter(candidate -> candidate.containsProperty("links.base-url"))
                    .findFirst()
                    .orElseThrow();

            assertThat(source.getName()).startsWith("configserver:").endsWith("jogo-acoes-docker.yml");
            assertThat(environment.getProperty("links.base-url")).isEqualTo("http://localhost:8080/api");
        }
    }

    @Test
    void appDoesNotStartWhenConfigServerIsUnreachable() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .properties(MAIN_APPLICATION_YML, USERNAME, PASSWORD,
                        // Nothing listens on port 1; one attempt keeps the test short.
                        "CONFIG_SERVER_URL=http://localhost:1",
                        "spring.cloud.config.retry.max-attempts=1");

        assertThatThrownBy(() -> application.run().close())
                .isInstanceOf(ConfigClientFailFastException.class)
                .hasMessageContaining("Could not locate PropertySource");
    }

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {
    }
}
