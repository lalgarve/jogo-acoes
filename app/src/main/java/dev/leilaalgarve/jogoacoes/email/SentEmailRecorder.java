package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.email.SentEmail;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import dev.leilaalgarve.jogoacoes.user.UserService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared by every {@link EmailSender} implementation: recording the send in {@code sent_email}
 * doesn't depend on how (or whether) the e-mail is actually dispatched.
 */
@Component
class SentEmailRecorder {

    private final SentEmailRepository sentEmailRepository;
    private final UserService userService;

    SentEmailRecorder(SentEmailRepository sentEmailRepository, UserService userService) {
        this.sentEmailRepository = sentEmailRepository;
        this.userService = userService;
    }

    SentEmail record(OutgoingEmail email) {
        return record(email, null);
    }

    /** Records a send email-service accepted, with the id it gave it (spec 05-034). */
    SentEmail record(OutgoingEmail email, UUID emailServiceId) {
        SentEmail sentEmail = new SentEmail();
        sentEmail.setEmailServiceId(emailServiceId);
        if (email.userId() != null) {
            sentEmail.setUser(userService.getById(email.userId()));
        }
        sentEmail.setEmail(email.recipient());
        sentEmail.setLink(email.link());
        sentEmail.setTemplate(email.templateName());
        sentEmail.setSentAt(LocalDateTime.now());
        return sentEmailRepository.save(sentEmail);
    }
}
