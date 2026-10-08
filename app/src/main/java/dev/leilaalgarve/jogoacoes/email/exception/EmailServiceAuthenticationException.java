package dev.leilaalgarve.jogoacoes.email.exception;

/**
 * email-service answered 401 (spec 05-034): app's X-API-Key is missing, unknown, expired or
 * revoked. A configuration problem, not a transient one -- never retried. The key itself never
 * goes into the message.
 */
public class EmailServiceAuthenticationException extends RuntimeException {

    public EmailServiceAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
