package io.deployo.jogoacoes.web;

import io.deployo.jogoacoes.api.EntryRequestsApi;
import io.deployo.jogoacoes.api.model.EntryRequest;
import io.deployo.jogoacoes.api.model.Participation;
import io.deployo.jogoacoes.api.model.ParticipationStatus;
import io.deployo.jogoacoes.service.EntryRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntryRequestsController implements EntryRequestsApi {

    private final EntryRequestService entryRequestService;

    public EntryRequestsController(EntryRequestService entryRequestService) {
        this.entryRequestService = entryRequestService;
    }

    @Override
    public ResponseEntity<Participation> requestOrConfirmEntry(Long competitionId, EntryRequest entryRequest) {
        if (isAuthenticated()) {
            io.deployo.jogoacoes.domain.Participation participation = entryRequestService.confirmEntry(competitionId);
            return ResponseEntity.ok(toApiModel(participation));
        }
        entryRequestService.requestEntry(competitionId, entryRequest);
        return ResponseEntity.status(202).build();
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static Participation toApiModel(io.deployo.jogoacoes.domain.Participation participation) {
        Participation model = new Participation()
                .id(participation.getId())
                .competitionId(participation.getCompetition().getId())
                .email(participation.getEmail())
                .status(ParticipationStatus.valueOf(participation.getStatus().name()))
                .requestType(Participation.RequestTypeEnum.valueOf(participation.getRequestType().name()));
        if (participation.getUser() != null) {
            model.userId(participation.getUser().getId());
        }
        if (participation.getFirstEmailSentDate() != null) {
            model.firstEmailSentDate(participation.getFirstEmailSentDate());
        }
        if (participation.getJoinedAt() != null) {
            model.joinedAt(participation.getJoinedAt());
        }
        return model;
    }
}
