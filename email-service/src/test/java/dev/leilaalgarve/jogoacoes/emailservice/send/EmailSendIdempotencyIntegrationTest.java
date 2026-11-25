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
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.ses.SesClient;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-059: POST /emails is idempotent with the Idempotency-Key header, scoped to the calling
 * client. Against the real server, PostgreSQL (the unique index decides between simultaneous
 * requests) and the LocalStack send queue (a repeat publishes nothing more).
 *
 * <p>Rows are counted with plain SQL, by recipient: the test does not depend on the repository
 * methods the spec adds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Autowired
    private SesClient sesClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> issuedKeyIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        sendQueue.ensureExists();
        sendQueue.drain();
        if (!senderRepository.existsById(CLIENT)) {
            senderRepository.save(new ClientSender(CLIENT, "no-reply@jogo-acoes.example", Instant.now()));
        }
        createTemplate(TEST_KEY, "welcome");
    }

    @AfterEach
    void cleanUp() {
        new TemplateCleanupHooks(sendRepository, templateRepository, sesClient).cleanUp();
        apiKeyRepository.deleteAllById(issuedKeyIds);
        issuedKeyIds.clear();
        if (senderRepository.existsById(OTHER_CLIENT)) {
            senderRepository.deleteById(OTHER_CLIENT);
        }
    }

    @Test
    void repeatingTheSameRequestWithTheSameKeyGivesTheSameSendAndQueuesItOnce() {
        String recipient = unique("repeat");
        String key = UUID.randomUUID().toString();

        String first = send(TEST_KEY, key, body("welcome", recipient, "Ada"))
                .then().statusCode(202).extract().jsonPath().getString("id");
        String second = send(TEST_KEY, key, body("welcome", recipient, "Ada"))
                .then().statusCode(202).extract().jsonPath().getString("id");

        assertThat(second).isEqualTo(first);
        assertThat(sendsTo(recipient)).isEqualTo(1);
        assertThat(sendQueue.receiveAll(5)).extracting(m -> m.get("correlationId").asString())
                .containsExactly(first);
    }

    @Test
    void theSameTemplateDataInAnotherOrderIsTheSameRequest() {
        String recipient = unique("order");
        String key = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Ada");
        data.put("link", "https://example.com/x");
        Map<String, Object> reversed = new LinkedHashMap<>();
        reversed.put("link", "https://example.com/x");
        reversed.put("name", "Ada");

        String first = send(TEST_KEY, key, Map.of("templateName", "welcome", "recipientEmail", recipient,
                "templateData", data)).then().statusCode(202).extract().jsonPath().getString("id");
        String second = send(TEST_KEY, key, Map.of("templateName", "welcome", "recipientEmail", recipient,
                "templateData", reversed)).then().statusCode(202).extract().jsonPath().getString("id");

        assertThat(second).isEqualTo(first);
        assertThat(sendsTo(recipient)).isEqualTo(1);
    }

    @Test
    void theSameKeyWithAnotherBodyIsRejectedAndRecordsNothing() {
        String recipient = unique("reused");
        String key = UUID.randomUUID().toString();
        send(TEST_KEY, key, body("welcome", recipient, "Ada")).then().statusCode(202);

        Response reused = send(TEST_KEY, key, body("welcome", recipient, "Bia"));

        assertThat(reused.statusCode()).isEqualTo(422);
        assertThat(reused.jsonPath().getString("message"))
                .isEqualTo("Idempotency-Key already used with a different request");
        assertThat(sendsTo(recipient)).isEqualTo(1);
    }

    @Test
    void anotherClientMayUseTheSameKey() {
        String otherKey = issueKey(OTHER_CLIENT);
        senderRepository.save(new ClientSender(OTHER_CLIENT, "no-reply@other.example", Instant.now()));
        createTemplate(otherKey, "welcome");
        String recipient = unique("clients");
        String key = UUID.randomUUID().toString();

        String mine = send(TEST_KEY, key, body("welcome", recipient, "Ada"))
                .then().statusCode(202).extract().jsonPath().getString("id");
        String theirs = send(otherKey, key, body("welcome", recipient, "Ada"))
                .then().statusCode(202).extract().jsonPath().getString("id");

        assertThat(theirs).isNotEqualTo(mine);
        assertThat(sendsTo(recipient)).isEqualTo(2);
    }

    @Test
    void withoutTheHeaderTheRequestIsMalformedAndRecordsNothing() {
        String recipient = unique("noheader");

        Response response = request(TEST_KEY).body(body("welcome", recipient, "Ada")).when().post("/emails");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("message")).contains("Idempotency-Key");
        assertThat(sendsTo(recipient)).isZero();
    }

    @Test
    void aKeyLongerThan255CharactersIsMalformedAndRecordsNothing() {
        String recipient = unique("longkey");

        Response response = send(TEST_KEY, "k".repeat(256), body("welcome", recipient, "Ada"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("message")).contains("Idempotency-Key");
        assertThat(sendsTo(recipient)).isZero();
    }

    @Test
    void aRejectedRequestDoesNotKeepItsKey() {
        String recipient = unique("rejected");
        String key = UUID.randomUUID().toString();
        send(TEST_KEY, key, body("missing-template", recipient, "Ada")).then().statusCode(404);

        send(TEST_KEY, key, body("welcome", recipient, "Ada")).then().statusCode(202);

        assertThat(sendsTo(recipient)).isEqualTo(1);
    }

    @Test
    void simultaneousRequestsWithTheSameKeyRecordOneSend() throws Exception {
        int requests = 8;
        String recipient = unique("simultaneous");
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<CompletableFuture<Response>> responses = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                responses.add(CompletableFuture.supplyAsync(() -> {
                    awaitQuietly(start);
                    return send(TEST_KEY, key, body("welcome", recipient, "Ada"));
                }, pool));
            }
            start.countDown();

            List<String> ids = new ArrayList<>();
            for (CompletableFuture<Response> response : responses) {
                Response done = response.get();
                assertThat(done.statusCode()).isEqualTo(202);
                ids.add(done.jsonPath().getString("id"));
            }
            assertThat(ids).hasSize(requests).containsOnly(ids.getFirst());
            assertThat(sendsTo(recipient)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private Response send(String apiKey, String idempotencyKey, Map<String, Object> body) {
        return request(apiKey).header("Idempotency-Key", idempotencyKey).body(body).when().post("/emails");
    }

    private void createTemplate(String apiKey, String name) {
        request(apiKey).body(Map.of("name", name, "subject", "Welcome", "body", "Hi {{name}}"))
                .when().post("/templates").then().statusCode(201);
    }

    private int sendsTo(String recipient) {
        return jdbcTemplate.queryForObject("select count(*) from email_send where recipient_email = ?",
                Integer.class, recipient);
    }

    private String issueKey(String clientName) {
        byte[] entropy = new byte[ApiKeyFormat.ENTROPY_BYTES];
        RANDOM.nextBytes(entropy);
        String plaintext = ApiKeyFormat.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        issuedKeyIds.add(apiKeyRepository.save(new ApiKey(clientName, apiKeyHasher.hash(plaintext), Instant.now(), null))
                .getId());
        return plaintext;
    }

    private RequestSpecification request(String apiKey) {
        return RestAssured.given().port(port).basePath("/api").contentType("application/json")
                .header("X-API-Key", apiKey);
    }

    private static Map<String, Object> body(String templateName, String recipient, String name) {
        return Map.of("templateName", templateName, "recipientEmail", recipient, "templateData", Map.of("name", name));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
