package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.PlayerDirectoryApi;
import dev.leilaalgarve.jogoacoes.api.model.PlayerSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerDirectoryController implements PlayerDirectoryApi {

    private final PlayerDirectoryService playerDirectoryService;

    public PlayerDirectoryController(PlayerDirectoryService playerDirectoryService) {
        this.playerDirectoryService = playerDirectoryService;
    }

    @Override
    public ResponseEntity<PlayerSummary> getPlayer(Long userId) {
        return ResponseEntity.of(playerDirectoryService.getPlayer(userId).map(this::toApiModel));
    }

    @Override
    public ResponseEntity<PlayerSummary> findPlayerByEmail(String email) {
        return ResponseEntity.of(playerDirectoryService.findByEmail(email).map(this::toApiModel));
    }

    private PlayerSummary toApiModel(PlayerSummaryView view) {
        return new PlayerSummary()
                .userId(view.userId())
                .name(view.name())
                .email(view.email())
                .ownedCount(view.ownedCount())
                .publicCount(view.publicCount())
                .privateCount(view.privateCount());
    }
}
