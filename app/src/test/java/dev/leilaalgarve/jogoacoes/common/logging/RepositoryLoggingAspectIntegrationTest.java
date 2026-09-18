package dev.leilaalgarve.jogoacoes.common.logging;

import dev.leilaalgarve.jogoacoes.competition.CompetitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-011 (T008): a real repository call through the full Spring context (not a plain
 * {@code @DataJpaTest}, which wouldn't wire {@link RepositoryLoggingAspect}'s AOP
 * infrastructure the same way the app itself boots it) -- confirms it gets logged. Expected red
 * until T010, same reasoning as {@link ControllerLoggingAspectIntegrationTest}.
 */
@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RepositoryLoggingAspectIntegrationTest {

    @Autowired
    private CompetitionRepository competitionRepository;

    @Test
    void logsARepositoryCall(CapturedOutput output) {
        competitionRepository.findAll();

        assertThat(output).contains("--> CompetitionRepository.findAll()");
        assertThat(output).contains("<-- CompetitionRepository.findAll returned");
    }
}
