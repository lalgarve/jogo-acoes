package dev.leilaalgarve.jogoacoes.common.logging;

import dev.leilaalgarve.jogoacoes.email.EmailRequest;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.email.EmailTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Spec 05-011 (T009): same conditional-skip shape as {@code SqsEmailSenderDockerIntegrationTest}
 * -- {@code @ActiveProfiles("docker")} is what actually wires {@code SqsEmailSender} (only
 * active when {@code email.sender: sqs}), so this needs Postgres+LocalStack reachable, checked
 * in {@code @BeforeAll} before Spring tries to build the context. Expected red until T010, same
 * reasoning as the other two aspect integration tests.
 */
@SpringBootTest
@ActiveProfiles("docker")
@ExtendWith(OutputCaptureExtension.class)
class QueueLoggingAspectIntegrationTest {

    @BeforeAll
    static void requiresDockerCompose() {
        assumeTrue(reachable("localhost", 5432) && reachable("localhost", 4566),
                "Postgres and/or LocalStack not reachable on localhost -- skipping, this profile needs "
                        + "`docker compose up -d db localstack`");
    }

    @Autowired
    private EmailSender emailSender;

    @Test
    void logsTheMessageSentThroughTheQueue(CapturedOutput output) {
        String recipientEmail = "player-" + UUID.randomUUID() + "@example.com";
        EmailRequest request = new EmailRequest(null, recipientEmail, null, "Copa Verão", null,
                "https://jogo-acoes.example/login-links/abc", EmailTemplate.REGISTRATION_LINK);

        emailSender.send(request);

        assertThat(output).contains("--> queue message").contains(recipientEmail);
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
