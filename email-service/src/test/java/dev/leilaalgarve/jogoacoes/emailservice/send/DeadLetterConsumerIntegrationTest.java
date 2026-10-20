package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.DeadLetterQueue;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.EmailSendRows;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.TcpForwarder;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplate;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Spec 05-046: the dead-letter consumer's error paths the scenarios of send_email.feature do not
 * reach -- SES down while recreating a missing template, a message with no send behind it, a
 * message that is not a send queue message. SES points at a port nothing listens on, and this
 * context reads a dead-letter queue of its own, so no other test context (with SES up) takes its
 * messages. The context is closed afterwards.
 */
@SpringBootTest(properties = "email.dlq-name=email-service-dead-letter-consumer-test-dlq")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class DeadLetterConsumerIntegrationTest {

    private static final String CLIENT = "dead-letter-consumer-test";
    private static final Duration WAIT = Duration.ofSeconds(15);

    @DynamicPropertySource
    static void sesIsDown(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.ses.endpoint", () -> "http://localhost:" + TcpForwarder.freePort());
    }

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    @Autowired
    private EmailTemplateRepository templateRepository;

    @Autowired
    private EmailSendRepository sendRepository;

    @Autowired
    private EmailSendRows rows;

    @BeforeEach
    void setUp() {
        cleanUp();
        deadLetterQueue.ensureExists();
    }

    @AfterEach
    void cleanUp() {
        List<EmailTemplate> templates = templateRepository.findAllByClientId(CLIENT);
        sendRepository.deleteAll(sendRepository.findAll().stream()
                .filter(send -> CLIENT.equals(send.getClientId())).toList());
        templateRepository.deleteAll(templates);
    }

    /**
     * Requested over a day ago, so the SES_UNAVAILABLE recorded is also the give-up: the send
     * ends FAILED instead of going back to the shared send queue for another test context.
     */
    @Test
    void sesDownWhileRecreatingAMissingTemplateCountsAsSesUnavailable(CapturedOutput output) {
        EmailTemplate template = templateRepository.save(new EmailTemplate(CLIENT, "welcome", "Welcome!",
                "<p>Hi</p>", null, CLIENT + "__welcome", Instant.now()));
        EmailSend send = new EmailSend(CLIENT, template.getId(), "player@example.com", "no-reply@example.com",
                template.getSesTemplateName(), "{}", Instant.now().minus(Duration.ofHours(25)));
        send.markQueued(Instant.now());
        UUID id = sendRepository.saveAndFlush(send).getId();

        deadLetterQueue.send(body(id), "TEMPLATE_MISSING");

        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(rows.state(id)).containsEntry("status", "FAILED")
                    .containsEntry("attempts", 1)
                    .containsEntry("last_error_type", "SES_UNAVAILABLE");
        });
        assertThat(output.getOut()).contains("Could not recreate the template of send " + id);
    }

    @Test
    void dropsAMessageWithNoSendBehindIt(CapturedOutput output) {
        UUID unknown = UUID.randomUUID();

        deadLetterQueue.send(body(unknown), "SES_UNAVAILABLE");

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(output.getOut()).contains("Dead-letter message for send " + unknown + " with no record"));
        assertThat(sendRepository.findById(unknown)).isEmpty();
    }

    /** A second copy of the same delivery error: the first one already decided. */
    @Test
    void dropsAMessageForASendThatIsNoLongerQueued(CapturedOutput output) {
        EmailTemplate template = templateRepository.save(new EmailTemplate(CLIENT, "welcome", "Welcome!",
                "<p>Hi</p>", null, CLIENT + "__welcome", Instant.now()));
        EmailSend send = new EmailSend(CLIENT, template.getId(), "player@example.com", "no-reply@example.com",
                template.getSesTemplateName(), "{}", Instant.now());
        send.fail();
        UUID id = sendRepository.saveAndFlush(send).getId();

        deadLetterQueue.send(body(id), "SES_UNAVAILABLE");

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(output.getOut()).contains("Dead-letter message for send " + id + " that is FAILED"));
        assertThat(rows.state(id)).containsEntry("status", "FAILED").containsEntry("attempts", 0);
    }

    @Test
    void dropsAMessageThatIsNotASendQueueMessage(CapturedOutput output) {
        deadLetterQueue.send("not-json", null);

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(output.getOut()).contains("Dead-letter message is not a send queue message"));
    }

    private static String body(UUID id) {
        return "{\"schemaVersion\":\"1\",\"correlationId\":\"" + id + "\",\"senderAddress\":\"no-reply@example.com\","
                + "\"recipientEmail\":\"player@example.com\",\"templateName\":\"" + CLIENT + "__welcome\","
                + "\"templateData\":{}}";
    }
}
