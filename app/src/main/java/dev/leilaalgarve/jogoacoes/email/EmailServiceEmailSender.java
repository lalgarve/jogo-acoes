package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.email.client.EmailServiceGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sends through email-service (spec 05-034): POST /emails with one of the templates
 * EmailTemplateSynchronizer registered, and SES renders it at send time. app no longer renders
 * e-mails nor talks to the queue -- both belong to email-service now (spec 05-031).
 *
 * <p>Which template and which variables is the client's choice (spec 05-061, {@link OutgoingEmail}).
 *
 * <p>The send comes first and the {@code sent_email} row after, with the id email-service gave
 * it: the table is append-only, so the id has to be known at insert time. A failed send leaves
 * no row and propagates, rolling back whatever business transaction asked for the e-mail.
 *
 * <p>Active wherever {@code email.sender: email-service} is set, which also needs
 * {@code email-service.base-url} (EmailServiceGateway).
 */
@Service
@ConditionalOnProperty(name = "email.sender", havingValue = "email-service")
class EmailServiceEmailSender implements EmailSender {

    private final EmailServiceGateway gateway;
    private final SentEmailRecorder sentEmailRecorder;

    EmailServiceEmailSender(EmailServiceGateway gateway, SentEmailRecorder sentEmailRecorder) {
        this.gateway = gateway;
        this.sentEmailRecorder = sentEmailRecorder;
    }

    @Override
    public void send(OutgoingEmail email) {
        // Spec 05-059: one key per e-mail, the same in every attempt the gateway makes to send it.
        UUID emailServiceId = gateway.sendEmail(email.templateName(), email.recipient(), email.templateData(),
                UUID.randomUUID().toString());
        sentEmailRecorder.record(email, emailServiceId);
    }
}
