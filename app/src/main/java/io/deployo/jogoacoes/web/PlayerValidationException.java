package io.deployo.jogoacoes.web;

/** A business-rule validation failure editing a player (e.g. e-mail already used). Maps to HTTP 400. */
public class PlayerValidationException extends RuntimeException {

    public PlayerValidationException(String message) {
        super(message);
    }
}
