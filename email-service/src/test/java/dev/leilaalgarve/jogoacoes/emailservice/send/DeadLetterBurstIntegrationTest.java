package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.DeadLetterQueue;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.SendQueue;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.TemplateCleanupHooks;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.DeleteTemplateRequest;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Spec 05-046: a burst of TEMPLATE_MISSING errors for the same template -- the invites of a
 * private competition, say -- recreates the template on SES once, not once per e-mail, and every
 * one of those e-mails goes back on the send queue.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class DeadLetterBurstIntegrationTest {

    /** The versioned test key of client jogo-acoes (docker/postgres-email-service/test-data/README.md). */
    private static final String TEST_KEY = "dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU";
    private static final String CLIENT = "jogo-acoes";
    private static final int BURST = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private SendQueue sendQueue;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    @Autowired
    private ClientSenderRepository senderRepository;

    @Autowired
    private EmailSendRepository sendRepository;

    @Autowired
    private EmailTemplateRepository templateRepository;

    @Autowired
    private SesClient sesClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        sendQueue.ensureExists();
        sendQueue.drain();
        deadLetterQueue.ensureExists();
        if (!senderRepository.existsById(CLIENT)) {
            senderRepository.save(new ClientSender(CLIENT, "no-reply@jogo-acoes.example", Instant.now()));
        }
    }

    @AfterEach
    void cleanUp() {
        new TemplateCleanupHooks(sendRepository, templateRepository, sesClient).cleanUp();
    }

    @Test
    void recreatesTheTemplateOnceForABurstOfTemplateMissingErrors(CapturedOutput output) {
        request().body(Map.of("name", "burst", "subject", "Invite", "body", "Hi {{name}}"))
                .when().post("/templates").then().statusCode(201);
        for (int i = 0; i < BURST; i++) {
            request().body(Map.of("templateName", "burst", "recipientEmail", "player" + i + "@example.com"))
                    .when().post("/emails").then().statusCode(202);
        }
        List<JsonNode> queued = receive(BURST);
        sesClient.deleteTemplate(DeleteTemplateRequest.builder().templateName(CLIENT + "__burst").build());

        queued.forEach(message -> deadLetterQueue.send(message.toString(), "TEMPLATE_MISSING"));

        List<JsonNode> requeued = receive(BURST);
        assertThat(requeued).extracting(m -> m.get("correlationId").asString())
                .containsExactlyInAnyOrderElementsOf(queued.stream().map(m -> m.get("correlationId").asString()).toList());
        assertThat(count(output.getOut(), "Recreated template " + CLIENT + "__burst on SES")).isEqualTo(1);
    }

    private List<JsonNode> receive(int expected) {
        List<JsonNode> received = new ArrayList<>();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            received.addAll(sendQueue.receiveAll(1));
            assertThat(received).hasSize(expected);
        });
        return received;
    }

    private static int count(String text, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private RequestSpecification request() {
        return RestAssured.given().port(port).basePath("/api").contentType("application/json")
                .header("X-API-Key", TEST_KEY);
    }
}
