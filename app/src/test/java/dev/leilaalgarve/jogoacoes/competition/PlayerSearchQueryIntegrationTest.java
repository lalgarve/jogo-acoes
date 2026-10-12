package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-039 (T003): {@link ParticipationRepository#searchPlayerSummaries} against the real
 * Postgres. The database is shared, so every test scopes its users with its own e-mail domain;
 * the competition-type and ownership filters are checked combined with that domain.
 */
@SpringBootTest
class PlayerSearchQueryIntegrationTest {

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMother userMother;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    private final String domain = "search-" + UUID.randomUUID().toString().substring(0, 8) + ".test";

    @Test
    void withoutFiltersEveryUserMatches() {
        player("Someone");

        Page<PlayerSummaryView> page = participationRepository.searchPlayerSummaries(null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(userRepository.count());
    }

    @Test
    void competitionTypeKeepsOnlyConfirmedEntriesOfThatType() {
        User publicPlayer = player("Public");
        participate(publicPlayer, CompetitionType.PUBLIC, ParticipationStatus.IN_COMPETITION);
        User privatePlayer = player("Private");
        participate(privatePlayer, CompetitionType.PRIVATE, ParticipationStatus.IN_COMPETITION);
        User pendingPlayer = player("Pending");
        participate(pendingPlayer, CompetitionType.PUBLIC, ParticipationStatus.LINK_CLICKED);

        assertThat(search(CompetitionType.PUBLIC, domain, null)).containsExactly(publicPlayer.getId());
        assertThat(search(CompetitionType.PRIVATE, domain, null)).containsExactly(privatePlayer.getId());
    }

    @Test
    void emailDomainIsExactAndIgnoresLetterCase() {
        User lower = player("Lower");
        User upper = userMother.registeredPlayer("Upper", "upper@" + domain.toUpperCase(Locale.ROOT));
        userMother.registeredPlayer("Subdomain", "sub@sub." + domain);

        assertThat(search(null, domain.toUpperCase(Locale.ROOT), null))
                .containsExactlyInAnyOrder(lower.getId(), upper.getId());
    }

    @Test
    void ownerFilterSplitsCreatorsFromEveryoneElse() {
        User creator = player("Creator");
        competitionFixtures.createdBy(creator, CompetitionType.PUBLIC, CompetitionStatus.OPEN);
        User nonCreator = player("Non-creator");

        assertThat(search(null, domain, true)).containsExactly(creator.getId());
        assertThat(search(null, domain, false)).containsExactly(nonCreator.getId());
    }

    @Test
    void pagesAreOrderedByNameThenIdAndAPagePastTheEndIsEmpty() {
        User b = player("B");
        User a1 = player("A");
        User a2 = player("A");

        Page<PlayerSummaryView> first = participationRepository.searchPlayerSummaries(null, domain, null, PageRequest.of(0, 2));
        Page<PlayerSummaryView> second = participationRepository.searchPlayerSummaries(null, domain, null, PageRequest.of(1, 2));
        Page<PlayerSummaryView> past = participationRepository.searchPlayerSummaries(null, domain, null, PageRequest.of(5, 2));

        assertThat(first.getContent()).extracting(PlayerSummaryView::userId).containsExactly(a1.getId(), a2.getId());
        assertThat(second.getContent()).extracting(PlayerSummaryView::userId).containsExactly(b.getId());
        assertThat(past.getContent()).isEmpty();
        assertThat(past.getTotalElements()).isEqualTo(3);
    }

    @Test
    void itemsCarryTheSameCountsAsTheSingleSummary() {
        User player = player("Counted");
        participate(player, CompetitionType.PUBLIC, ParticipationStatus.IN_COMPETITION);
        participate(player, CompetitionType.PRIVATE, ParticipationStatus.IN_COMPETITION);

        PlayerSummaryView fromSearch = participationRepository
                .searchPlayerSummaries(null, domain, null, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(fromSearch).isEqualTo(participationRepository.findPlayerSummaryById(player.getId()).orElseThrow());
    }

    private java.util.List<Long> search(CompetitionType type, String emailDomain, Boolean owner) {
        return participationRepository.searchPlayerSummaries(type, emailDomain, owner, PageRequest.of(0, 100))
                .getContent().stream().map(PlayerSummaryView::userId).toList();
    }

    private User player(String name) {
        return userMother.registeredPlayer(name, UUID.randomUUID() + "@" + domain);
    }

    private void participate(User user, CompetitionType type, ParticipationStatus status) {
        Participation participation = new Participation();
        participation.setCompetition(competitionFixtures.custom(type, CompetitionStatus.OPEN));
        participation.setUser(user);
        participation.setEmail(user.getEmail());
        participation.setStatus(status);
        participation.setRequestType(type == CompetitionType.PUBLIC ? RequestType.REQUEST : RequestType.INVITE);
        participation.setJoinedAt(status == ParticipationStatus.IN_COMPETITION ? LocalDate.now() : null);
        participationRepository.save(participation);
    }
}
