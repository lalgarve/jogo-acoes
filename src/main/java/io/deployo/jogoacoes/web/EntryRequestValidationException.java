package io.deployo.jogoacoes.web;

/** A business-rule validation failure on an entry request (e.g. missing e-mail). Maps to HTTP 400. */
public class EntryRequestValidationException extends RuntimeException {

    public EntryRequestValidationException(String message) {
        super(message);
    }
}
