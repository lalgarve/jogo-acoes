package dev.leilaalgarve.jogoacoes.web;

import dev.leilaalgarve.jogoacoes.api.CompetitionsApi;
import dev.leilaalgarve.jogoacoes.api.model.Competition;
import dev.leilaalgarve.jogoacoes.api.model.CompetitionCreateRequest;
import dev.leilaalgarve.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import dev.leilaalgarve.jogoacoes.service.CompetitionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompetitionsController implements CompetitionsApi {

    private final CompetitionService competitionService;

    public CompetitionsController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @Override
    public ResponseEntity<Competition> createCompetition(CompetitionCreateRequest competitionCreateRequest) {
        dev.leilaalgarve.jogoacoes.domain.Competition created = competitionService.create(competitionCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toApiModel(created));
    }

    @Override
    public ResponseEntity<Void> decideInviteEmailTiming(Long competitionId, DecideInviteEmailTimingRequest decideInviteEmailTimingRequest) {
        competitionService.decideInviteEmailTiming(competitionId, decideInviteEmailTimingRequest.getTiming());
        return ResponseEntity.noContent().build();
    }

    private static Competition toApiModel(dev.leilaalgarve.jogoacoes.domain.Competition competition) {
        return new Competition()
                .id(competition.getId())
                .name(competition.getName())
                .type(dev.leilaalgarve.jogoacoes.api.model.CompetitionType.valueOf(competition.getType().name()))
                .startDate(competition.getStartDate())
                .durationDays(competition.getDurationDays())
                .recurring(competition.isRecurring())
                .buyFee(competition.getBuyFee().doubleValue())
                .sellFee(competition.getSellFee().doubleValue())
                .status(dev.leilaalgarve.jogoacoes.api.model.CompetitionStatus.valueOf(competition.getStatus().name()))
                .creatorId(competition.getCreator().getId());
    }
}
