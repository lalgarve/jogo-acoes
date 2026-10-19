package dev.leilaalgarve.jogoacoes.email.exception;

/**
 * email-service refused the send because the recipient's e-mail domain does not accept e-mail
 * (422 on POST /emails, spec 05-047). Sending to the same address again gets the same answer.
 */
public class RecipientDomainRejectedException extends RuntimeException {

    public RecipientDomainRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
