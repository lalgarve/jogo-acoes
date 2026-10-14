package dev.leilaalgarve.jogoacoes.common.logging;

import dev.leilaalgarve.jogoacoes.competition.CompetitionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-011 (T011): activates the real {@code production} profile (picking up its
 * {@code logging.level.dev.leilaalgarve.jogoacoes: INFO} override). Since spec 05-042 that
 * profile's configuration lives in config-repo/jogo-acoes-production.yml, served by config-server,
 * so this test starts from the app's real src/main/resources/application.yml (target/classes,
 * which the test resources' own application.yml otherwise shadows) and its import of
 * docker-compose.yml's config-server -- the same file production reads, not a copy.
 *
 * <p>{@code production}'s own {@code spring.datasource.url} has no default (real credentials come
 * from the deploying team, never hardcoded), so it's overridden here back to the same real
 * PostgreSQL every other test already uses (docker-compose.yml's {@code db} service, already
 * migrated by the base test context) -- never H2 (specs/05-028-testes-exigem-docker-real/plan.md:
 * H2 is not a dependency of this project at all, not even for a one-off profile simulation like
 * this). {@code spring.flyway.enabled=false} matches production's own value (a separate team runs
 * migrations there, not this app) -- harmless here since the schema this connects to is already
 * migrated by every other test's own context. These test properties outrank config-server's, so
 * production's unresolved placeholders for the same keys are never read.
 *
 * <p>{@code logging.level.*} reconfigures Logback's JVM-wide {@code LoggerContext}, a global
 * singleton independent of Spring's (per-configuration) context cache -- without resetting it,
 * this test's INFO override for {@code dev.leilaalgarve.jogoacoes} silently leaks into whatever
 * test class Surefire happens to run next in the same fork, even one against a completely
 * different (cached) Spring context. Confirmed empirically: {@link
 * RepositoryLoggingAspectIntegrationTest} started failing only once this test ran before it in
 * the same suite.
 */
@SpringBootTest(properties = {
        "spring.config.location=file:target/classes/application.yml",
        // docker-compose.yml's test credentials for config-server; never a real secret.
        "CONFIG_SERVER_USERNAME=config",
        "CONFIG_SERVER_PASSWORD=config-docker-not-a-secret",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jogo_acoes",
        "spring.datasource.username=jogo_acoes_admin",
        "spring.datasource.password=jogo_acoes_admin",
        "spring.flyway.enabled=false",
        // No default in production either (spec 05-034): the real email-service
        // docker-compose.yml starts, with the test key of client jogo-acoes (spec 05-030).
        "email-service.base-url=http://localhost:8082/api",
        "email-service.api-key=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU",
        // Spec 05-041: no default in production either; production's own value.
        "links.base-url=https://jogoacoes.leilaalgarve.de/api"
})
@ActiveProfiles("production")
@ExtendWith(OutputCaptureExtension.class)
class ProductionProfileSuppressesLoggingAspectsTest {

    @Autowired
    private CompetitionRepository competitionRepository;

    @Test
    void noneOfTheThreeAspectsLogUnderTheProductionProfile(CapturedOutput output) {
        competitionRepository.findAll();

        assertThat(output).doesNotContain("ControllerLoggingAspect", "RepositoryLoggingAspect", "EmailServiceLoggingAspect");
    }

    @AfterEach
    void restoreTheApplicationPackageLogLevel() {
        LoggingSystem.get(getClass().getClassLoader()).setLogLevel("dev.leilaalgarve.jogoacoes", LogLevel.DEBUG);
    }
}
