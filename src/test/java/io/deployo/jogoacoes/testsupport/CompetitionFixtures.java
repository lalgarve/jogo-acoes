package io.deployo.jogoacoes.testsupport;

import io.deployo.jogoacoes.domain.Competition;
import io.deployo.jogoacoes.domain.CompetitionStatus;
import io.deployo.jogoacoes.domain.CompetitionType;
import io.deployo.jogoacoes.repository.CompetitionRepository;
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
        return persist(CompetitionType.PUBLIC);
    }

    public Competition privateCompetition() {
        return persist(CompetitionType.PRIVATE);
    }

    private Competition persist(CompetitionType type) {
        Competition competition = new Competition();
        competition.setName("Fixture competition " + UUID.randomUUID());
        competition.setType(type);
        competition.setStartDate(LocalDate.now().plusDays(7));
        competition.setDurationDays(30);
        competition.setRecurring(false);
        competition.setBuyFee(new BigDecimal("0.50"));
        competition.setSellFee(new BigDecimal("0.50"));
        competition.setStatus(type == CompetitionType.PUBLIC ? CompetitionStatus.OPEN : CompetitionStatus.AWAITING_INVITES);
        competition.setCreator(userMother.administrator());
        return competitionRepository.save(competition);
    }
}
