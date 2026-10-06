package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.email.client.EmailServiceGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends through email-service (spec 05-034): POST /emails with one of the templates
 * EmailTemplateSynchronizer registered, and SES renders it at send time. app no longer renders
 * e-mails nor talks to the queue -- both belong to email-service now (spec 05-031).
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
    public void send(EmailRequest request) {
        UUID emailServiceId = gateway.sendEmail(templateNameFor(request), request.email(), templateDataFor(request));
        sentEmailRecorder.record(request, emailServiceId);
    }

    /**
     * Which of the templates in EmailTemplateSynchronizer.TEMPLATE_NAMES this request maps to --
     * same choice the Thymeleaf renderer made before spec 05-034: LOGIN_LINK covers three, told
     * apart by competitionName and origin.
     */
    static String templateNameFor(EmailRequest request) {
        return switch (request.template()) {
            case INVITE -> "invite";
            case REGISTRATION_LINK -> "registration-link";
            case LOGIN_LINK -> {
                if (request.competitionName() == null) {
                    yield "login-link";
                }
                yield request.origin() == RequestType.INVITE ? "login-link-invite" : "login-link-request";
            }
        };
    }

    /** The variables the templates use; absent values are left out rather than sent as null. */
    static Map<String, Object> templateDataFor(EmailRequest request) {
        Map<String, Object> data = new HashMap<>();
        putIfPresent(data, "name", request.name());
        putIfPresent(data, "competitionName", request.competitionName());
        putIfPresent(data, "link", request.link());
        return data;
    }

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
