package io.deployo.jogoacoes.web;

/** The submitted captcha token failed real ALTCHA verification. Maps to HTTP 400. */
public class CaptchaInvalidException extends RuntimeException {

    public CaptchaInvalidException(String message) {
        super(message);
    }
}
