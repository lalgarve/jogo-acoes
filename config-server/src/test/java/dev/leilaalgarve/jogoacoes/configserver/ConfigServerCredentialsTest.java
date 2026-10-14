package dev.leilaalgarve.jogoacoes.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-042: outside the tests, the credentials come from CONFIG_SERVER_USERNAME/PASSWORD with
 * no default -- config-server must not start without them, rather than start open or with a
 * generated password nobody knows.
 */
class ConfigServerCredentialsTest {

    @Test
    void doesNotStartWithoutCredentials() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(ConfigServerApplication.class)
                .properties("server.port=0", "CONFIG_REPO_LOCATION=file:../config-repo/");

        assertThatThrownBy(() -> application.run().close())
                .hasStackTraceContaining("Could not resolve placeholder 'CONFIG_SERVER_");
    }
}
