package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.model.AccessLevel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated test for the pure access matrix (spec 05-004) — no Spring context. */
class CompetitionAccessResolverTest {

    @Test
    void participantInAnOpenCompetitionGetsReadWrite() {
        Competition competition = competitionWithStatus(CompetitionStatus.OPEN);
        Participation participation = participationWithStatus(ParticipationStatus.IN_COMPETITION);

        Optional<AccessLevel> access = CompetitionAccessResolver.resolve(competition, participation, false);

        assertThat(access).contains(AccessLevel.READ_WRITE);
    }

    @Test
    void pastParticipantOfAClosedCompetitionGetsReadOnly() {
        Competition competition = competitionWithStatus(CompetitionStatus.CLOSED);
        Participation participation = participationWithStatus(ParticipationStatus.IN_COMPETITION);

        Optional<AccessLevel> access = CompetitionAccessResolver.resolve(competition, participation, false);

        assertThat(access).contains(AccessLevel.READ);
    }

    @Test
    void invitedButNotConfirmedGetsReadOnly() {
        Competition competition = competitionWithStatus(CompetitionStatus.OPEN);
        Participation participation = participationWithStatus(ParticipationStatus.EMAIL_SENT);

        Optional<AccessLevel> access = CompetitionAccessResolver.resolve(competition, participation, false);

        assertThat(access).contains(AccessLevel.READ);
    }

    @Test
    void administratorWithNoParticipationGetsReadOnly() {
        Competition competition = competitionWithStatus(CompetitionStatus.OPEN);

        Optional<AccessLevel> access = CompetitionAccessResolver.resolve(competition, null, true);

        assertThat(access).contains(AccessLevel.READ);
    }

    @Test
    void nonAdministratorWithNoRelationshipIsDenied() {
        Competition competition = competitionWithStatus(CompetitionStatus.OPEN);

        Optional<AccessLevel> access = CompetitionAccessResolver.resolve(competition, null, false);

        assertThat(access).isEmpty();
    }

    private Competition competitionWithStatus(CompetitionStatus status) {
        Competition competition = new Competition();
        competition.setId(1L);
        competition.setStatus(status);
        return competition;
    }

    private Participation participationWithStatus(ParticipationStatus status) {
        Participation participation = new Participation();
        participation.setId(1L);
        participation.setStatus(status);
        return participation;
    }
}
