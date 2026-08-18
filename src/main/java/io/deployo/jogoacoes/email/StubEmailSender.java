package io.deployo.jogoacoes.email;

import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.SentEmail;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.repository.SentEmailRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Doesn't actually send anything — records the send in {@code sent_email} instead.
 * Rendering the template text (Thymeleaf) and dispatching for real is a later iteration.
 */
@Service
public class StubEmailSender implements EmailSender {

    private final SentEmailRepository sentEmailRepository;
    private final UserRepository userRepository;

    public StubEmailSender(SentEmailRepository sentEmailRepository, UserRepository userRepository) {
        this.sentEmailRepository = sentEmailRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void send(Long userId, String email, String link, EmailTemplate template) {
        SentEmail sentEmail = new SentEmail();
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("No such user: " + userId));
            sentEmail.setUser(user);
        }
        sentEmail.setEmail(email);
        sentEmail.setLink(link);
        sentEmail.setTemplate(template);
        sentEmail.setSentAt(LocalDateTime.now());
        sentEmailRepository.save(sentEmail);
    }
}
