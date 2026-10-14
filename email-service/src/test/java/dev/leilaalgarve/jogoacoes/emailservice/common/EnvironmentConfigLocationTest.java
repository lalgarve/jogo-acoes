package dev.leilaalgarve.jogoacoes.emailservice.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-042: per-environment configuration is served by config-server from config-repo/, never
 * packaged in the jar -- a profile file left here would silently apply on top of it.
 */
class EnvironmentConfigLocationTest {

    @ParameterizedTest
    @ValueSource(strings = {"application-docker.yml", "application-staging.yml", "application-production.yml"})
    void environmentProfileFileIsNotPackaged(String fileName) {
        assertThat(getClass().getClassLoader().getResource(fileName))
                .as("%s belongs in config-repo/, not on the classpath", fileName)
                .isNull();
    }
}
