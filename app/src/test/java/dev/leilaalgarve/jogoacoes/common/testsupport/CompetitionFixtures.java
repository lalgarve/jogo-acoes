package dev.leilaalgarve.jogoacoes.common.testsupport;

import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.competition.CompetitionStatus;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
import dev.leilaalgarve.jogoacoes.competition.CompetitionRepository;
import dev.leilaalgarve.jogoacoes.user.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persists a Competition entity directly, bypassing the HTTP API -- mirrors "is on the
 * competition creation screen" being a no-op: request_competition_entry.feature's "clicked
 * on a public/private competition" steps are UI navigation, not the thing under test, so the
 * target competition is just set up as a given fact.
 */
@Component
public class CompetitionFixtures {

    private final CompetitionRepository competitionRepository;
    private final UserMother userMother;

    public CompetitionFixtures(CompetitionRepository competitionRepository, UserMother userMother) {
        this.competitionRepository = competitionRepository;
        this.userMother = userMother;
    }

    public Competition publicCompetition() {
        return persist(CompetitionType.PUBLIC, CompetitionStatus.OPEN);
    }

    public Competition privateCompetition() {
        return persist(CompetitionType.PRIVATE, CompetitionStatus.AWAITING_INVITES);
    }

    public Competition closedCompetition() {
        return persist(CompetitionType.PUBLIC, CompetitionStatus.CLOSED);
    }

    /** For combinations the three named factories above don't cover (e.g. an open private competition, invites already sent). */
    public Competition custom(CompetitionType type, CompetitionStatus status) {
        return persist(type, status);
    }

    /** Spec 05-038: a competition owned by a given creator (the other factories always create a new administrator). */
    public Competition createdBy(User creator, CompetitionType type, CompetitionStatus status) {
        return persist(type, status, creator);
    }

    private Competition persist(CompetitionType type, CompetitionStatus status) {
        return persist(type, status, userMother.administrator());
    }

    private Competition persist(CompetitionType type, CompetitionStatus status, User creator) {
        Competition competition = new Competition();
        competition.setName("Fixture competition " + UUID.randomUUID());
        competition.setType(type);
        competition.setStartDate(LocalDate.now().plusDays(7));
        competition.setDurationDays(30);
        competition.setRecurring(false);
        competition.setBuyFee(new BigDecimal("0.50"));
        competition.setSellFee(new BigDecimal("0.50"));
        competition.setStatus(status);
        competition.setCreator(creator);
        return competitionRepository.save(competition);
    }
}
