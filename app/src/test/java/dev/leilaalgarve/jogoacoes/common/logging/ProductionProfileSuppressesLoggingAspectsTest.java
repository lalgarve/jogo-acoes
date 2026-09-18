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
 * {@code logging.level.dev.leilaalgarve.jogoacoes: INFO} override) against an in-memory H2
 * database instead of a real Postgres -- the profile's own datasource properties need real
 * infrastructure this environment doesn't have (unlike {@code docker}, there's no
 * {@code @BeforeAll} reachability gate for "production" anywhere else in the project either),
 * so only the datasource/flyway keys are overridden here; the logging override under test is
 * the profile file's own, untouched.
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
        "spring.datasource.url=jdbc:h2:mem:jogo_acoes_production_profile_test;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
@ActiveProfiles("production")
@ExtendWith(OutputCaptureExtension.class)
class ProductionProfileSuppressesLoggingAspectsTest {

    @Autowired
    private CompetitionRepository competitionRepository;

    @Test
    void noneOfTheThreeAspectsLogUnderTheProductionProfile(CapturedOutput output) {
        competitionRepository.findAll();

        assertThat(output).doesNotContain("ControllerLoggingAspect", "RepositoryLoggingAspect", "QueueLoggingAspect");
    }

    @AfterEach
    void restoreTheApplicationPackageLogLevel() {
        LoggingSystem.get(getClass().getClassLoader()).setLogLevel("dev.leilaalgarve.jogoacoes", LogLevel.DEBUG);
    }
}
