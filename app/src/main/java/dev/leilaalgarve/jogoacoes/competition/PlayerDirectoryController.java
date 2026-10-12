package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.PlayerDirectoryApi;
import dev.leilaalgarve.jogoacoes.api.model.PlayerPage;
import dev.leilaalgarve.jogoacoes.api.model.PlayerSearchRequest;
import dev.leilaalgarve.jogoacoes.api.model.PlayerSummary;
import org.springframework.data.domain.Page;
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

    /** The body is optional: none means no filters and the contract's default page. */
    @Override
    public ResponseEntity<PlayerPage> searchPlayers(PlayerSearchRequest request) {
        PlayerSearchRequest filters = request != null ? request : new PlayerSearchRequest();
        CompetitionType competitionType = filters.getCompetitionType() != null
                ? CompetitionType.valueOf(filters.getCompetitionType().name())
                : null;
        Page<PlayerSummaryView> page = playerDirectoryService.search(competitionType, filters.getEmailDomain(),
                filters.getOwner(), filters.getPage(), filters.getSize());
        return ResponseEntity.ok(new PlayerPage()
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .items(page.getContent().stream().map(this::toApiModel).toList()));
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
