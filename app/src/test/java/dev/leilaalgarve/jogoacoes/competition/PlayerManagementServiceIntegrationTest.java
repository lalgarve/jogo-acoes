package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.competition.exception.PlayerNotFoundException;
import dev.leilaalgarve.jogoacoes.log.LogRepository;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-036: removing a player through a competition the participation doesn't belong to,
 * against the real Postgres of the docker profile. Not {@code @Transactional}: the service's own
 * transaction rolls back on the exception, and the assertions read what was actually committed.
 */
@SpringBootTest
class PlayerManagementServiceIntegrationTest {

    @Autowired
    private PlayerManagementService playerManagementService;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Autowired
    private UserMother userMother;

    @Test
    void removingAPlayerThroughACompetitionTheyAreNotInFindsNoPlayerAndRemovesNothing() {
        Competition competition1 = competitionFixtures.privateCompetition();
        Competition competition2 = competitionFixtures.privateCompetition();
        Participation playerAInCompetition1 = participationRepository.save(
                emailSentParticipation(competition1, userMother.registeredPlayer()));

        assertThatThrownBy(() -> playerManagementService.removePlayer(
                competition2.getId(), playerAInCompetition1.getId()))
                .isInstanceOf(PlayerNotFoundException.class);

        assertThat(participationRepository.findById(playerAInCompetition1.getId()))
                .get()
                .satisfies(kept -> {
                    assertThat(kept.getCompetition().getId()).isEqualTo(competition1.getId());
                    assertThat(kept.getStatus()).isEqualTo(ParticipationStatus.EMAIL_SENT);
                });
        assertThat(participationRepository.findByCompetition_Id(competition2.getId())).isEmpty();
        assertThat(logRepository.findAll())
                .noneMatch(log -> log.getLogType() == LogType.PARTICIPATION_STATUS_CHANGED
                        && playerAInCompetition1.getId().equals(log.getRelatedObjectId()));
    }

    private static Participation emailSentParticipation(Competition competition, User player) {
        Participation participation = new Participation();
        participation.setCompetition(competition);
        participation.setUser(player);
        participation.setEmail(player.getEmail());
        participation.setRequestType(RequestType.INVITE);
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participation.setFirstEmailSentDate(LocalDate.now());
        return participation;
    }
}
