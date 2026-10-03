package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.email.SentEmail;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import dev.leilaalgarve.jogoacoes.user.UserService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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

    SentEmail record(EmailRequest request) {
        SentEmail sentEmail = new SentEmail();
        if (request.userId() != null) {
            sentEmail.setUser(userService.getById(request.userId()));
        }
        sentEmail.setEmail(request.email());
        sentEmail.setLink(request.link());
        sentEmail.setTemplate(request.template());
        sentEmail.setSentAt(LocalDateTime.now());
        return sentEmailRepository.save(sentEmail);
    }
}
