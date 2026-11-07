package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyFormat;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.SendQueue;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.TemplateCleanupHooks;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import software.amazon.awssdk.services.ses.SesClient;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Spec 05-059: POST /emails with an Idempotency-Key, against the real server, PostgreSQL and
 * LocalStack queue. A repeat answers with the send already recorded and queues nothing new.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailSendIdempotencyIntegrationTest {

    private static final String TEST_KEY = "dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU";
    private static final String CLIENT = "jogo-acoes";
    private static final String OTHER_CLIENT = "idempotency-other-client";
    private static final SecureRandom RANDOM = new SecureRandom();

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

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    private final List<Long> issuedKeyIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        sendQueue.ensureExists();
        sendQueue.drain();
        configureSender(CLIENT);
        request(TEST_KEY).body(Map.of("name", "welcome", "subject", "Welcome", "body", "Hi {{name}}"))
                .when().post("/templates").then().statusCode(201);
    }

    @AfterEach
    void cleanUp() {
        new TemplateCleanupHooks(sendRepository, templateRepository, sesClient).cleanUp();
        apiKeyRepository.deleteAllById(issuedKeyIds);
        issuedKeyIds.clear();
        senderRepository.findById(OTHER_CLIENT).ifPresent(senderRepository::delete);
    }

    @Test
    void theSameKeyAndBodyAnswerWithTheSameSendAndQueueItOnce() {
        String key = UUID.randomUUID().toString();

        String first = send(TEST_KEY, key, welcome("player@example.com")).then().statusCode(202)
                .extract().jsonPath().getString("id");
        String second = send(TEST_KEY, key, welcome("player@example.com")).then().statusCode(202)
                .extract().jsonPath().getString("id");

        assertThat(second).isEqualTo(first);
        assertThat(sendsWithKey(CLIENT, key)).isEqualTo(1);
        List<String> published = new ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            sendQueue.receiveAll(1).forEach(m -> published.add(m.get("correlationId").asString()));
            assertThat(published).contains(first);
        });
        sendQueue.receiveAll(2).forEach(m -> published.add(m.get("correlationId").asString()));
        assertThat(published).containsExactly(first);
    }

    @Test
    void theSameTemplateDataInAnotherOrderIsTheSameRequest() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("templateName", "welcome", "recipientEmail", "player@example.com",
                "templateData", Map.of("name", "Ana", "link", "/x"));
        String reordered = "{\"templateData\":{\"link\":\"/x\",\"name\":\"Ana\"},"
                + "\"recipientEmail\":\"player@example.com\",\"templateName\":\"welcome\"}";

        String first = send(TEST_KEY, key, body).then().statusCode(202).extract().jsonPath().getString("id");
        String second = request(TEST_KEY).header("Idempotency-Key", key).body(reordered)
                .when().post("/emails").then().statusCode(202).extract().jsonPath().getString("id");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void theSameKeyWithAnotherBodyIsRejectedAndRecordsNothing() {
        String key = UUID.randomUUID().toString();
        send(TEST_KEY, key, welcome("player@example.com")).then().statusCode(202);

        Response response = send(TEST_KEY, key, welcome("someone-else@example.com"));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.jsonPath().getString("message"))
                .isEqualTo("Idempotency-Key already used with a different request");
        assertThat(sendsWithKey(CLIENT, key)).isEqualTo(1);
    }

    @Test
    void anotherClientMayUseTheSameKey() {
        String otherApiKey = issueKey(OTHER_CLIENT);
        configureSender(OTHER_CLIENT);
        request(otherApiKey).body(Map.of("name", "welcome", "subject", "Welcome", "body", "Hi {{name}}"))
                .when().post("/templates").then().statusCode(201);
        String key = UUID.randomUUID().toString();

        String mine = send(TEST_KEY, key, welcome("player@example.com")).then().statusCode(202)
                .extract().jsonPath().getString("id");
        String theirs = send(otherApiKey, key, welcome("player@example.com")).then().statusCode(202)
                .extract().jsonPath().getString("id");

        assertThat(theirs).isNotEqualTo(mine);
        assertThat(sendsWithKey(CLIENT, key)).isEqualTo(1);
        assertThat(sendsWithKey(OTHER_CLIENT, key)).isEqualTo(1);
    }

    @Test
    void aRequestWithoutTheKeyIsRejectedAndRecordsNothing() {
        Response response = request(TEST_KEY).body(welcome("player@example.com")).when().post("/emails");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("message")).isEqualTo("Missing header Idempotency-Key");
        assertThat(sendRepository.count()).isZero();
    }

    @Test
    void anEmptyOrTooLongKeyIsRejectedAndRecordsNothing() {
        for (String key : List.of("", "k".repeat(256))) {
            Response response = send(TEST_KEY, key, welcome("player@example.com"));

            assertThat(response.statusCode()).as("key of %d characters", key.length()).isEqualTo(400);
            assertThat(response.jsonPath().getString("message")).contains("idempotencyKey");
        }
        assertThat(sendRepository.count()).isZero();
    }

    @Test
    void aRejectedRequestDoesNotKeepItsKey() {
        String key = UUID.randomUUID().toString();
        send(TEST_KEY, key, Map.of("templateName", "no-such-template", "recipientEmail", "player@example.com"))
                .then().statusCode(404);

        send(TEST_KEY, key, welcome("player@example.com")).then().statusCode(202);

        assertThat(sendsWithKey(CLIENT, key)).isEqualTo(1);
    }

    @Test
    void simultaneousRequestsWithTheSameKeyRecordOneSend() throws Exception {
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Response>> requests = List.of(
                    CompletableFuture.supplyAsync(() -> sendWhen(start, key), executor),
                    CompletableFuture.supplyAsync(() -> sendWhen(start, key), executor));
            start.countDown();

            List<Response> responses = new ArrayList<>();
            for (CompletableFuture<Response> request : requests) {
                responses.add(request.get(30, TimeUnit.SECONDS));
            }
            assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(202));
            assertThat(responses.get(1).jsonPath().getString("id")).isEqualTo(responses.get(0).jsonPath().getString("id"));
        } finally {
            executor.shutdownNow();
        }
        assertThat(sendsWithKey(CLIENT, key)).isEqualTo(1);
    }

    private Response sendWhen(CountDownLatch start, String key) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return send(TEST_KEY, key, welcome("player@example.com"));
    }

    private Response send(String apiKey, String idempotencyKey, Map<String, Object> body) {
        return request(apiKey).header("Idempotency-Key", idempotencyKey).body(body).when().post("/emails");
    }

    private static Map<String, Object> welcome(String recipient) {
        return Map.of("templateName", "welcome", "recipientEmail", recipient, "templateData", Map.of("name", "Ana"));
    }

    private long sendsWithKey(String clientId, String key) {
        return sendRepository.findAll().stream()
                .filter(send -> send.getClientId().equals(clientId) && key.equals(send.getIdempotencyKey()))
                .count();
    }

    private void configureSender(String clientId) {
        if (!senderRepository.existsById(clientId)) {
            senderRepository.save(new ClientSender(clientId, "no-reply@" + clientId + ".example", Instant.now()));
        }
    }

    private String issueKey(String clientName) {
        byte[] entropy = new byte[ApiKeyFormat.ENTROPY_BYTES];
        RANDOM.nextBytes(entropy);
        String plaintext = ApiKeyFormat.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        issuedKeyIds.add(apiKeyRepository.save(new ApiKey(clientName, apiKeyHasher.hash(plaintext), Instant.now(), null)).getId());
        return plaintext;
    }

    private RequestSpecification request(String apiKey) {
        return RestAssured.given().port(port).basePath("/api").contentType("application/json")
                .header("X-API-Key", apiKey);
    }
}
