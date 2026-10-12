package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.loginsession.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Spec 05-038: player summaries. The "a player only sees their own" rule depends on the path
 * value, not only on the role, so it lives here rather than in a security matcher: another
 * user's id comes back empty, exactly like a nonexistent one, and the controller turns both into
 * the same 404.
 */
@Service
public class PlayerDirectoryService {

    private final ParticipationRepository participationRepository;
    private final CurrentUserService currentUserService;

    public PlayerDirectoryService(ParticipationRepository participationRepository,
                                  CurrentUserService currentUserService) {
        this.participationRepository = participationRepository;
        this.currentUserService = currentUserService;
    }

    public Optional<PlayerSummaryView> getPlayer(Long userId) {
        if (!currentUserService.currentUserIsAdministrator() && !userId.equals(currentUserService.currentUser().getId())) {
            return Optional.empty();
        }
        return participationRepository.findPlayerSummaryById(userId);
    }

    /** Administrator only -- enforced by {@link CompetitionSecurityConfigContributor}. */
    public Optional<PlayerSummaryView> findByEmail(String email) {
        return participationRepository.findPlayerSummaryByEmail(email);
    }

    /**
     * Spec 05-039, administrator only -- enforced by {@link CompetitionSecurityConfigContributor}.
     * Null filters don't restrict; page and size are already validated against the contract
     * (page &ge; 0, 1 &le; size &le; 100) and defaulted by the caller.
     */
    public Page<PlayerSummaryView> search(CompetitionType competitionType, String emailDomain, Boolean owner,
                                          int page, int size) {
        return participationRepository.searchPlayerSummaries(competitionType, emailDomain, owner,
                PageRequest.of(page, size));
    }
}
