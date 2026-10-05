package dev.leilaalgarve.jogoacoes.email.exception;

/**
 * email-service refused a well-formed call (400/422 -- e.g. SES rejected a template's syntax),
 * spec 05-034. Repeating the same call gets the same answer, so it is never retried.
 */
public class EmailServiceRejectedException extends RuntimeException {

    public EmailServiceRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
