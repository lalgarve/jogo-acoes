package io.deployo.jogoacoes.web;

/** A business-rule validation failure on competition creation. Maps to HTTP 400. */
public class CompetitionValidationException extends RuntimeException {

    public CompetitionValidationException(String message) {
        super(message);
    }
}
