package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.EmailServiceApplication;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.SendQueue;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.TemplateCleanupHooks;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import software.amazon.awssdk.services.ses.SesClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Spec 05-046: two email-service instances publishing from the same PostgreSQL never put the same
 * send on the queue twice ({@code FOR UPDATE SKIP LOCKED}). The sends pile up as pending while
 * the queue is gone, then both instances publish them once it is back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailQueuePublisherConcurrencyTest {

    private static final String TEST_KEY = "dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU";
    private static final String CLIENT = "jogo-acoes";
    private static final int SENDS = 50;

    @LocalServerPort
    private int port;

    @Autowired
    private SendQueue sendQueue;

    @Autowired
    private ClientSenderRepository senderRepository;

    @Autowired
    private EmailSendRepository sendRepository;

    @Autowired
    private EmailTemplateRepository templateRepository;

    @Autowired
    private SesClient sesClient;

    private ConfigurableApplicationContext secondInstance;

    @BeforeEach
    void setUp() {
        cleanUp();
        sendQueue.ensureExists();
        sendQueue.drain();
        if (!senderRepository.existsById(CLIENT)) {
            senderRepository.save(new ClientSender(CLIENT, "no-reply@jogo-acoes.example", Instant.now()));
        }
    }

    @AfterEach
    void cleanUp() {
        if (secondInstance != null) {
            secondInstance.close();
            secondInstance = null;
        }
        sendQueue.ensureExists();
        new TemplateCleanupHooks(sendRepository, templateRepository, sesClient).cleanUp();
    }

    @Test
    void twoInstancesPublishEverySendExactlyOnce() {
        request().body(Map.of("name", "welcome", "subject", "Welcome", "body", "Hi {{name}}"))
                .when().post("/templates").then().statusCode(201);
        sendQueue.delete();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < SENDS; i++) {
            ids.add(request().body(Map.of("templateName", "welcome", "recipientEmail", "player" + i + "@example.com"))
                    .when().post("/emails").then().statusCode(202).body("status", equalTo("PENDING"))
                    .extract().jsonPath().getString("id"));
        }
        secondInstance = new SpringApplicationBuilder(EmailServiceApplication.class).run("--server.port=0");

        sendQueue.ensureExists();

        List<String> published = new ArrayList<>();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            sendQueue.receiveAll(1).forEach(m -> published.add(m.get("correlationId").asString()));
            assertThat(published).hasSizeGreaterThanOrEqualTo(SENDS);
        });
        // Nothing left behind: a duplicate would show up here.
        sendQueue.receiveAll(2).forEach(m -> published.add(m.get("correlationId").asString()));
        assertThat(published).hasSize(SENDS).containsExactlyInAnyOrderElementsOf(ids);
    }

    private RequestSpecification request() {
        return RestAssured.given().port(port).basePath("/api").contentType("application/json")
                .header("X-API-Key", TEST_KEY);
    }
}
