package io.deployo.jogoacoes.web;

/** The link was already consumed, and this device isn't the one that consumed it. Maps to HTTP 409. */
public class LoginLinkUsedOnAnotherDeviceException extends RuntimeException {

    public LoginLinkUsedOnAnotherDeviceException(String message) {
        super(message);
    }
}
