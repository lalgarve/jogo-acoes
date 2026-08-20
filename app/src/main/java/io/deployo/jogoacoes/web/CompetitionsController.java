package io.deployo.jogoacoes.web;

import io.deployo.jogoacoes.api.CompetitionsApi;
import io.deployo.jogoacoes.api.model.Competition;
import io.deployo.jogoacoes.api.model.CompetitionCreateRequest;
import io.deployo.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import io.deployo.jogoacoes.service.CompetitionService;
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
        io.deployo.jogoacoes.domain.Competition created = competitionService.create(competitionCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toApiModel(created));
    }

    @Override
    public ResponseEntity<Void> decideInviteEmailTiming(Long competitionId, DecideInviteEmailTimingRequest decideInviteEmailTimingRequest) {
        competitionService.decideInviteEmailTiming(competitionId, decideInviteEmailTimingRequest.getTiming());
        return ResponseEntity.noContent().build();
    }

    private static Competition toApiModel(io.deployo.jogoacoes.domain.Competition competition) {
        return new Competition()
                .id(competition.getId())
                .name(competition.getName())
                .type(io.deployo.jogoacoes.api.model.CompetitionType.valueOf(competition.getType().name()))
                .startDate(competition.getStartDate())
                .durationDays(competition.getDurationDays())
                .recurring(competition.isRecurring())
                .buyFee(competition.getBuyFee().doubleValue())
                .sellFee(competition.getSellFee().doubleValue())
                .status(io.deployo.jogoacoes.api.model.CompetitionStatus.valueOf(competition.getStatus().name()))
                .creatorId(competition.getCreator().getId());
    }
}
