package dev.leilaalgarve.jogoacoes.competition;

/** No participation with the given id in the given competition. Maps to HTTP 404. */
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(Long competitionId, Long participationId) {
        super("No such player " + participationId + " in competition " + competitionId);
    }
}
