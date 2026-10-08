package dev.leilaalgarve.jogoacoes.email.exception;

/**
 * email-service could not be reached, timed out or answered 5xx (spec 05-034). The caller may
 * try again later; nothing on email-service's side is known to have changed.
 */
public class EmailServiceUnavailableException extends RuntimeException {

    public EmailServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
