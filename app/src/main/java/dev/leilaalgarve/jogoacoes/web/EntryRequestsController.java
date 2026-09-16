package dev.leilaalgarve.jogoacoes.web;

import dev.leilaalgarve.jogoacoes.api.EntryRequestsApi;
import dev.leilaalgarve.jogoacoes.api.model.EntryRequest;
import dev.leilaalgarve.jogoacoes.api.model.Participation;
import dev.leilaalgarve.jogoacoes.service.EntryRequestService;
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
            dev.leilaalgarve.jogoacoes.domain.Participation participation = entryRequestService.confirmEntry(competitionId);
            return ResponseEntity.ok(ParticipationMapper.toApiModel(participation));
        }
        entryRequestService.requestEntry(competitionId, entryRequest);
        return ResponseEntity.status(202).build();
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}
