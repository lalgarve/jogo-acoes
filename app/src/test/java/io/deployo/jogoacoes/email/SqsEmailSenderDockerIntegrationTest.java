package io.deployo.jogoacoes.email;

import io.deployo.jogoacoes.domain.EmailTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;

import io.awspring.cloud.sqs.operations.SqsTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises SqsEmailSender against the real LocalStack queue docker-compose.yml starts, not a
 * mock (docs/context/iteracao-4.md, "Produtor: EmailSender real"). @ActiveProfiles("docker")
 * forces the profile that wires SqsEmailSender + the real Postgres datasource regardless of
 * what profile the rest of the Maven run uses -- so, same spirit as email-lambda's
 * EmailSendHandlerTest, this needs both Postgres and LocalStack reachable and is skipped (not
 * failed) otherwise: a plain @SpringBootTest here would fail during context startup (Postgres
 * connection) before any try/catch in a @Test method could run, so the reachability check
 * happens in @BeforeAll, before Spring ever tries to build the context.
 *
 * <p>The queue isn't exclusive to this test: with email.sender: sqs active for the whole docker
 * profile, every Cucumber scenario that sends an e-mail (invites, entry requests, ...) also
 * publishes to it for real through the same cached Spring context. A real CI run caught this:
 * receiving "the next message" assumed FIFO/exclusive ownership and instead picked up a
 * leftover message from an unrelated scenario. Fixed by tagging the request with a unique
 * recipient and scanning (not just taking the first) received message for it, tolerating
 * whatever else is sitting in the queue.
 */
@SpringBootTest
@ActiveProfiles("docker")
class SqsEmailSenderDockerIntegrationTest {

    @BeforeAll
    static void requiresDockerCompose() {
        assumeTrue(reachable("localhost", 5432) && reachable("localhost", 4566),
                "Postgres and/or LocalStack not reachable on localhost -- skipping, this profile needs "
                        + "`docker compose up -d db localstack`");
    }

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private SqsTemplate sqsTemplate;

    @Value("${email.queue-name}")
    private String queueName;

    @Test
    void publishesAMessageThatCanBeReceivedFromTheRealQueue() {
        String recipientEmail = "player-" + UUID.randomUUID() + "@example.com";
        EmailRequest request = new EmailRequest(null, recipientEmail, null, "Copa Verão", null,
                "https://jogo-acoes.example/login-links/abc", EmailTemplate.REGISTRATION_LINK);

        emailSender.send(request);

        EmailMessage message = receiveUntil(recipientEmail, Duration.ofSeconds(20));

        assertThat(message.schemaVersion()).isEqualTo("1");
        assertThat(message.subject()).isEqualTo("Finalize seu cadastro em Copa Verão");
        assertThat(message.body()).contains("Copa Verão", "https://jogo-acoes.example/login-links/abc");
    }

    /** Other tests in the same run publish to this same queue -- keep draining until the message
     * tagged with recipientEmail turns up, instead of assuming it's the very next one. */
    private EmailMessage receiveUntil(String recipientEmail, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Collection<Message<EmailMessage>> received = sqsTemplate.receiveMany(
                    to -> to.queue(queueName).maxNumberOfMessages(10).pollTimeout(Duration.ofSeconds(5)),
                    EmailMessage.class);
            Optional<EmailMessage> match = received.stream()
                    .map(Message::getPayload)
                    .filter(m -> m.recipientEmail().equals(recipientEmail))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        throw new AssertionError("No message for " + recipientEmail + " arrived on " + queueName + " within " + timeout);
    }

    private static boolean reachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
