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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises SqsEmailSender against the real LocalStack queue docker-compose.yml starts, not a
 * mock (docs/iteracao-4.md, "Produtor: EmailSender real"). @ActiveProfiles("docker") forces the
 * profile that wires SqsEmailSender + the real Postgres datasource regardless of what profile
 * the rest of the Maven run uses -- so, same spirit as email-lambda's EmailSendHandlerTest,
 * this needs both Postgres and LocalStack reachable and is skipped (not failed) otherwise: a
 * plain @SpringBootTest here would fail during context startup (Postgres connection) before any
 * try/catch in a @Test method could run, so the reachability check happens in @BeforeAll,
 * before Spring ever tries to build the context.
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
        EmailRequest request = new EmailRequest(null, "player@example.com", null, "Copa Verão", null,
                "https://jogo-acoes.example/login-links/abc", EmailTemplate.REGISTRATION_LINK);

        emailSender.send(request);

        Optional<Message<EmailMessage>> received = sqsTemplate.receive(
                to -> to.queue(queueName).pollTimeout(Duration.ofSeconds(10)), EmailMessage.class);

        assertThat(received).isPresent();
        EmailMessage message = received.get().getPayload();
        assertThat(message.schemaVersion()).isEqualTo("1");
        assertThat(message.recipientEmail()).isEqualTo("player@example.com");
        assertThat(message.subject()).isEqualTo("Finalize seu cadastro em Copa Verão");
        assertThat(message.body()).contains("Copa Verão", "https://jogo-acoes.example/login-links/abc");
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
