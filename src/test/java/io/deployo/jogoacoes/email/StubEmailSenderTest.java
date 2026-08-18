package io.deployo.jogoacoes.email;

import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.SentEmail;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.repository.SentEmailRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class StubEmailSenderTest {

    @Autowired
    private SentEmailRepository sentEmailRepository;

    @Autowired
    private UserRepository userRepository;

    private EmailSender emailSender;

    @Test
    void recordsTheSendWithTheAssociatedUser() {
        emailSender = new StubEmailSender(sentEmailRepository, userRepository);
        User user = userRepository.save(newUser("alice@example.com"));

        emailSender.send(user.getId(), "alice@example.com", "https://jogo-acoes.example/login/abc123", EmailTemplate.LOGIN_LINK);

        List<SentEmail> sent = sentEmailRepository.findAll();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getUser().getId()).isEqualTo(user.getId());
        assertThat(sent.get(0).getEmail()).isEqualTo("alice@example.com");
        assertThat(sent.get(0).getLink()).isEqualTo("https://jogo-acoes.example/login/abc123");
        assertThat(sent.get(0).getTemplate()).isEqualTo(EmailTemplate.LOGIN_LINK);
        assertThat(sent.get(0).getSentAt()).isNotNull();
    }

    @Test
    void recordsTheSendWithoutAUserWhenRecipientHasNoAccountYet() {
        emailSender = new StubEmailSender(sentEmailRepository, userRepository);

        emailSender.send(null, "bob@example.com", "https://jogo-acoes.example/entry/xyz789", EmailTemplate.REGISTRATION_LINK);

        List<SentEmail> sent = sentEmailRepository.findAll();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getUser()).isNull();
        assertThat(sent.get(0).getTemplate()).isEqualTo(EmailTemplate.REGISTRATION_LINK);
    }

    @Test
    void rejectsAnUnknownUserId() {
        emailSender = new StubEmailSender(sentEmailRepository, userRepository);

        assertThatThrownBy(() ->
                emailSender.send(999L, "carol@example.com", "https://jogo-acoes.example/invite/qqq", EmailTemplate.INVITE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static User newUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setRegistered(true);
        return user;
    }
}
