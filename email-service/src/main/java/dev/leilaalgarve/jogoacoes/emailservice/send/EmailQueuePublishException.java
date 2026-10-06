package dev.leilaalgarve.jogoacoes.emailservice.send;

public class EmailQueuePublishException extends RuntimeException {

    public EmailQueuePublishException(Throwable cause) {
        super("The e-mail could not be queued; nothing was sent, try again", cause);
    }
}
