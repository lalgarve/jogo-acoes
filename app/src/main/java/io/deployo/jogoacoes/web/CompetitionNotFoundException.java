package io.deployo.jogoacoes.web;

/** No competition with the given id. Maps to HTTP 404. */
public class CompetitionNotFoundException extends RuntimeException {

    public CompetitionNotFoundException(Long competitionId) {
        super("No such competition: " + competitionId);
    }
}
