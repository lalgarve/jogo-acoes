package io.deployo.jogoacoes.web;

/** The login link doesn't exist, or is used/invalidated/expired. Maps to HTTP 400. */
public class LoginLinkInvalidException extends RuntimeException {

    public LoginLinkInvalidException(String message) {
        super(message);
    }
}
