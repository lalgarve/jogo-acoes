package io.deployo.jogoacoes.email;

import io.deployo.jogoacoes.domain.SentEmail;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.repository.SentEmailRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Shared by every {@link EmailSender} implementation: recording the send in {@code sent_email}
 * doesn't depend on how (or whether) the e-mail is actually dispatched.
 */
@Component
class SentEmailRecorder {

    private final SentEmailRepository sentEmailRepository;
    private final UserRepository userRepository;

    SentEmailRecorder(SentEmailRepository sentEmailRepository, UserRepository userRepository) {
        this.sentEmailRepository = sentEmailRepository;
        this.userRepository = userRepository;
    }

    SentEmail record(EmailRequest request) {
        SentEmail sentEmail = new SentEmail();
        if (request.userId() != null) {
            User user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new IllegalArgumentException("No such user: " + request.userId()));
            sentEmail.setUser(user);
        }
        sentEmail.setEmail(request.email());
        sentEmail.setLink(request.link());
        sentEmail.setTemplate(request.template());
        sentEmail.setSentAt(LocalDateTime.now());
        return sentEmailRepository.save(sentEmail);
    }
}
