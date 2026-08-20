package io.deployo.jogoacoes.web;

import io.deployo.jogoacoes.api.PlayersApi;
import io.deployo.jogoacoes.api.model.InvitePlayersRequest;
import io.deployo.jogoacoes.api.model.Participation;
import io.deployo.jogoacoes.api.model.ParticipationStatus;
import io.deployo.jogoacoes.api.model.ResendPlayerInviteEmailsRequest;
import io.deployo.jogoacoes.api.model.UpdatePlayerEmailRequest;
import io.deployo.jogoacoes.service.PlayerManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlayersController implements PlayersApi {

    private final PlayerManagementService playerManagementService;

    public PlayersController(PlayerManagementService playerManagementService) {
        this.playerManagementService = playerManagementService;
    }

    @Override
    public ResponseEntity<List<Participation>> listPlayers(Long competitionId, ParticipationStatus status) {
        io.deployo.jogoacoes.domain.ParticipationStatus statusFilter = status != null
                ? io.deployo.jogoacoes.domain.ParticipationStatus.valueOf(status.name())
                : null;
        List<Participation> players = playerManagementService.listPlayers(competitionId, statusFilter).stream()
                .map(ParticipationMapper::toApiModel)
                .toList();
        return ResponseEntity.ok(players);
    }

    @Override
    public ResponseEntity<Void> invitePlayers(Long competitionId, InvitePlayersRequest invitePlayersRequest) {
        playerManagementService.invitePlayers(competitionId, invitePlayersRequest.getEmails());
        return ResponseEntity.status(202).build();
    }

    @Override
    public ResponseEntity<Participation> updatePlayerEmail(Long competitionId, Long participationId, UpdatePlayerEmailRequest updatePlayerEmailRequest) {
        io.deployo.jogoacoes.domain.Participation updated =
                playerManagementService.updateEmail(competitionId, participationId, updatePlayerEmailRequest.getEmail());
        return ResponseEntity.ok(ParticipationMapper.toApiModel(updated));
    }

    @Override
    public ResponseEntity<Void> removePlayer(Long competitionId, Long participationId) {
        playerManagementService.removePlayer(competitionId, participationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resendPlayerInviteEmail(Long competitionId, Long participationId) {
        playerManagementService.resendInviteEmail(competitionId, participationId);
        return ResponseEntity.status(202).build();
    }

    @Override
    public ResponseEntity<Void> resendPlayerInviteEmails(Long competitionId, ResendPlayerInviteEmailsRequest resendPlayerInviteEmailsRequest) {
        playerManagementService.resendInviteEmails(competitionId, resendPlayerInviteEmailsRequest.getParticipationIds());
        return ResponseEntity.status(202).build();
    }
}
