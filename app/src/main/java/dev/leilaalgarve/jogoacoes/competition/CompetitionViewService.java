package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.model.AccessLevel;
import dev.leilaalgarve.jogoacoes.api.model.CompetitionDetail;
import dev.leilaalgarve.jogoacoes.api.model.CompetitionSummary;
import dev.leilaalgarve.jogoacoes.api.model.MyCompetitions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Read-side of the competition resource (spec 05-004) — discovery, "my competitions", and
 * detail with a computed access level. Kept separate from {@link CompetitionService} (creation/
 * invite timing, administrator-only) and {@link EntryRequestService} (requesting entry).
 */
@Service
public class CompetitionViewService {

    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;

    public CompetitionViewService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
    }

    public List<CompetitionSummary> listPublicCompetitions() {
        return competitionRepository.findByTypeAndStatus(CompetitionType.PUBLIC, CompetitionStatus.OPEN).stream()
                .map(CompetitionViewService::toSummary)
                .toList();
    }

    /** Three groups, always present even when empty — never a flat list (spec 05-004). */
    public MyCompetitions listMyCompetitions(Long userId) {
        MyCompetitions result = new MyCompetitions();
        for (Participation participation : participationRepository.findByUser_Id(userId)) {
            Competition competition = participation.getCompetition();
            CompetitionSummary summary = toSummary(competition);
            if (participation.getStatus() == ParticipationStatus.IN_COMPETITION) {
                if (competition.getStatus() == CompetitionStatus.OPEN) {
                    result.addParticipatingItem(summary);
                } else {
                    result.addPastParticipationsItem(summary);
                }
            } else {
                result.addPendingConfirmationItem(summary);
            }
        }
        return result;
    }

    /** Empty means denied (no relationship, not an administrator) or the competition doesn't exist — 404 either way. */
    public Optional<CompetitionDetail> getCompetitionDetail(Long competitionId, Long userId, boolean isAdministrator) {
        Optional<Competition> maybeCompetition = competitionRepository.findById(competitionId);
        if (maybeCompetition.isEmpty()) {
            return Optional.empty();
        }
        Competition competition = maybeCompetition.get();
        Participation participation = userId == null ? null
                : participationRepository.findByCompetition_IdAndUser_Id(competitionId, userId).orElse(null);

        return CompetitionAccessResolver.resolve(competition, participation, isAdministrator)
                .map(accessLevel -> toDetail(competition, accessLevel, participation));
    }

    private static CompetitionSummary toSummary(Competition competition) {
        return new CompetitionSummary()
                .id(competition.getId())
                .name(competition.getName())
                .type(dev.leilaalgarve.jogoacoes.api.model.CompetitionType.valueOf(competition.getType().name()))
                .status(dev.leilaalgarve.jogoacoes.api.model.CompetitionStatus.valueOf(competition.getStatus().name()))
                .startDate(competition.getStartDate())
                .durationDays(competition.getDurationDays());
    }

    private static CompetitionDetail toDetail(Competition competition, AccessLevel accessLevel, Participation participation) {
        boolean canConfirmEntry = participation != null && participation.getStatus() != ParticipationStatus.IN_COMPETITION;
        return new CompetitionDetail()
                .id(competition.getId())
                .name(competition.getName())
                .type(dev.leilaalgarve.jogoacoes.api.model.CompetitionType.valueOf(competition.getType().name()))
                .status(dev.leilaalgarve.jogoacoes.api.model.CompetitionStatus.valueOf(competition.getStatus().name()))
                .startDate(competition.getStartDate())
                .durationDays(competition.getDurationDays())
                .recurring(competition.isRecurring())
                .buyFee(competition.getBuyFee().doubleValue())
                .sellFee(competition.getSellFee().doubleValue())
                .accessLevel(accessLevel)
                .canConfirmEntry(canConfirmEntry);
    }
}
