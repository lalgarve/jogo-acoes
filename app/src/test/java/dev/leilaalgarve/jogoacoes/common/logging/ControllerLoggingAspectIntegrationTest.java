package dev.leilaalgarve.jogoacoes.common.logging;

import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.competition.CompetitionsController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-011 (T007): calls a real controller bean directly (same pattern as
 * AuditLoggingIntegrationTest) -- the injected reference is already the AOP proxy, so this
 * exercises {@link ControllerLoggingAspect} exactly like an HTTP call would, without the
 * servlet overhead. Expected to stay red until T010 turns DEBUG on for the application
 * package; the root logger stays at INFO until then, so the aspect's debug() calls produce
 * nothing to capture.
 */
@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ControllerLoggingAspectIntegrationTest {

    @Autowired
    private CompetitionsController competitionsController;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Test
    void logsEntryAndExitOfAControllerMethodWithATruncatedListReturn(CapturedOutput output) {
        competitionFixtures.publicCompetition();
        competitionFixtures.publicCompetition();

        competitionsController.listPublicCompetitions();

        assertThat(output).contains("--> CompetitionsController.listPublicCompetitions()");
        assertThat(output).containsPattern("<-- CompetitionsController\\.listPublicCompetitions returned .*\\+\\[1]");
    }
}
