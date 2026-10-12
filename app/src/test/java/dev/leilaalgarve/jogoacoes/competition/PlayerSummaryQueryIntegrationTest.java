package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-038 (T005): {@link ParticipationRepository}'s player summary SELECT against the real
 * Postgres -- the counting rules the Gherkin scenarios don't each pin down (closed competitions
 * count, pending entries in any status don't, another user's rows never leak in).
 */
@SpringBootTest
class PlayerSummaryQueryIntegrationTest {

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private UserMother userMother;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Test
    void userWithNothingHasAllCountsZero() {
        User player = userMother.registeredPlayer();

        PlayerSummaryView summary = participationRepository.findPlayerSummaryById(player.getId()).orElseThrow();

        assertThat(summary).isEqualTo(new PlayerSummaryView(player.getId(), player.getName(), player.getEmail(), 0, 0, 0));
    }

    @Test
    void countsOnlyConfirmedEntriesWhateverTheCompetitionStatus() {
        User player = userMother.registeredPlayer();
        participate(player, CompetitionType.PUBLIC, CompetitionStatus.OPEN, ParticipationStatus.IN_COMPETITION);
        participate(player, CompetitionType.PUBLIC, CompetitionStatus.CLOSED, ParticipationStatus.IN_COMPETITION);
        participate(player, CompetitionType.PRIVATE, CompetitionStatus.OPEN, ParticipationStatus.IN_COMPETITION);
        participate(player, CompetitionType.PUBLIC, CompetitionStatus.OPEN, ParticipationStatus.LINK_CLICKED);
        participate(player, CompetitionType.PRIVATE, CompetitionStatus.AWAITING_INVITES, ParticipationStatus.EMAIL_NOT_SENT);
        participate(player, CompetitionType.PRIVATE, CompetitionStatus.OPEN, ParticipationStatus.EMAIL_SENT);

        PlayerSummaryView summary = participationRepository.findPlayerSummaryById(player.getId()).orElseThrow();

        assertThat(summary.publicCount()).isEqualTo(2);
        assertThat(summary.privateCount()).isEqualTo(1);
        assertThat(summary.ownedCount()).isZero();
    }

    @Test
    void countsCreatedCompetitionsAndIgnoresOtherUsersRows() {
        User administrator = userMother.administrator();
        competitionFixtures.createdBy(administrator, CompetitionType.PUBLIC, CompetitionStatus.OPEN);
        competitionFixtures.createdBy(administrator, CompetitionType.PRIVATE, CompetitionStatus.CLOSED);
        User someoneElse = userMother.registeredPlayer();
        participate(someoneElse, CompetitionType.PUBLIC, CompetitionStatus.OPEN, ParticipationStatus.IN_COMPETITION);

        PlayerSummaryView summary = participationRepository.findPlayerSummaryById(administrator.getId()).orElseThrow();

        assertThat(summary.ownedCount()).isEqualTo(2);
        assertThat(summary.publicCount()).isZero();
        assertThat(summary.privateCount()).isZero();
    }

    @Test
    void findsByEmailIgnoringLetterCase() {
        User player = userMother.registeredPlayer();

        Optional<PlayerSummaryView> summary =
                participationRepository.findPlayerSummaryByEmail(player.getEmail().toUpperCase(Locale.ROOT));

        assertThat(summary).map(PlayerSummaryView::userId).contains(player.getId());
    }

    @Test
    void unknownIdOrEmailFindsNothing() {
        assertThat(participationRepository.findPlayerSummaryById(999_999_999L)).isEmpty();
        assertThat(participationRepository.findPlayerSummaryByEmail(TestEmails.unique("nobody"))).isEmpty();
    }

    private void participate(User user, CompetitionType type, CompetitionStatus competitionStatus, ParticipationStatus status) {
        Participation participation = new Participation();
        participation.setCompetition(competitionFixtures.custom(type, competitionStatus));
        participation.setUser(user);
        participation.setEmail(user.getEmail());
        participation.setStatus(status);
        participation.setRequestType(type == CompetitionType.PUBLIC ? RequestType.REQUEST : RequestType.INVITE);
        participation.setJoinedAt(status == ParticipationStatus.IN_COMPETITION ? LocalDate.now() : null);
        participationRepository.save(participation);
    }
}
