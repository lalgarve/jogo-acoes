package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.domain.EmailTemplate;
import dev.leilaalgarve.jogoacoes.domain.SentEmail;
import dev.leilaalgarve.jogoacoes.domain.User;
import dev.leilaalgarve.jogoacoes.repository.SentEmailRepository;
import dev.leilaalgarve.jogoacoes.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Without this, @DataJpaTest swaps in an embedded H2 database regardless of the active
// profile, but flyway.locations still points at the Postgres-specific migrations (with
// GRANT/REVOKE) -- those fail against H2. Keep using whatever datasource the active
// profile configures (H2 in sandbox, real Postgres in docker/CI).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
class StubEmailSenderTest {

    @Autowired
    private SentEmailRepository sentEmailRepository;

    @Autowired
    private UserRepository userRepository;

    private EmailSender emailSender;

    // sent_email is insert-only (no UPDATE/DELETE grant in production, see
    // memory/constitution.md) and, in the H2 sandbox profile, lives in one fixed named
    // in-memory database (jdbc:h2:mem:jogo_acoes) shared by every test class in the same
    // Maven run -- Cucumber's @SpringBootTest scenarios hit real HTTP endpoints and commit
    // for real, unlike this @DataJpaTest's own rolled-back transaction. So the table can
    // already hold rows from other tests by the time these run; assertions below look up the
    // exact row this test created (by its own unique link) and check the count grew by
    // exactly one, instead of assuming the table starts empty.
    @Test
    void recordsTheSendWithTheAssociatedUser() {
        emailSender = newStubEmailSender();
        User user = userRepository.save(newUser("alice@example.com"));
        long before = sentEmailRepository.count();
        String link = "https://jogo-acoes.example/login/" + UUID.randomUUID();

        emailSender.send(new EmailRequest(user.getId(), "alice@example.com", user.getName(), null, null,
                link, EmailTemplate.LOGIN_LINK));

        assertThat(sentEmailRepository.count()).isEqualTo(before + 1);
        SentEmail sent = sentEmailRepository.findByLink(link).orElseThrow();
        assertThat(sent.getUser().getId()).isEqualTo(user.getId());
        assertThat(sent.getEmail()).isEqualTo("alice@example.com");
        assertThat(sent.getLink()).isEqualTo(link);
        assertThat(sent.getTemplate()).isEqualTo(EmailTemplate.LOGIN_LINK);
        assertThat(sent.getSentAt()).isNotNull();
    }

    @Test
    void recordsTheSendWithoutAUserWhenRecipientHasNoAccountYet() {
        emailSender = newStubEmailSender();
        long before = sentEmailRepository.count();
        String link = "https://jogo-acoes.example/entry/" + UUID.randomUUID();

        emailSender.send(new EmailRequest(null, "bob@example.com", null, "Copa Jogo de Ações", null,
                link, EmailTemplate.REGISTRATION_LINK));

        assertThat(sentEmailRepository.count()).isEqualTo(before + 1);
        SentEmail sent = sentEmailRepository.findByLink(link).orElseThrow();
        assertThat(sent.getUser()).isNull();
        assertThat(sent.getTemplate()).isEqualTo(EmailTemplate.REGISTRATION_LINK);
    }

    @Test
    void rejectsAnUnknownUserId() {
        emailSender = newStubEmailSender();

        // Long.MAX_VALUE, not a low fixed ID like 999L: this shared H2 database accumulates
        // rows across the whole test run (see class-level note above), so a low ID could
        // eventually collide with a real user once enough tests have run before this one.
        assertThatThrownBy(() -> emailSender.send(new EmailRequest(Long.MAX_VALUE, "carol@example.com", null,
                "Copa Jogo de Ações", null, "https://jogo-acoes.example/invite/qqq", EmailTemplate.INVITE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EmailSender newStubEmailSender() {
        return new StubEmailSender(new SentEmailRecorder(sentEmailRepository, userRepository));
    }

    private static User newUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setRegistered(true);
        return user;
    }
}
