package dev.leilaalgarve.jogoacoes.emailservice.template;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.TcpForwarder;
import dev.leilaalgarve.jogoacoes.emailservice.send.EmailSendRepository;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-044: with SES unreachable, template registration, update and preview answer 503 --
 * not the 422 that means "SES rejected this template" -- and nothing is stored. SES points at a
 * port nothing listens on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SesUnavailableIntegrationTest {

    /** The versioned test key of client jogo-acoes (docker/postgres-email-service/test-data/README.md). */
    private static final String TEST_KEY = "dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU";
    private static final String CLIENT = "jogo-acoes";

    @DynamicPropertySource
    static void sesIsDown(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.ses.endpoint", () -> "http://localhost:" + TcpForwarder.freePort());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private EmailTemplateRepository templateRepository;

    @Autowired
    private EmailSendRepository sendRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        sendRepository.deleteAll();
        templateRepository.deleteAll();
    }

    @Test
    void registeringATemplateAnswers503AndStoresNothing() {
        Response response = request()
                .body(Map.of("name", "welcome", "subject", "Welcome!", "body", "Hello {{name}}"))
                .when().post("/templates");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.jsonPath().getString("message")).isNotBlank();
        assertThat(templateRepository.existsByClientIdAndName(CLIENT, "welcome")).isFalse();
    }

    @Test
    void updatingATemplateAnswers503AndKeepsTheStoredContent() {
        storeWithoutSes("welcome");

        Response response = request()
                .body(Map.of("subject", "Updated", "body", "Updated {{name}}"))
                .when().put("/templates/{name}", "welcome");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(templateRepository.findByClientIdAndName(CLIENT, "welcome").orElseThrow().getSubject())
                .isEqualTo("Welcome!");
    }

    @Test
    void previewingATemplateAnswers503() {
        storeWithoutSes("welcome");

        Response response = request()
                .body(Map.of("variables", Map.of("name", "Ada")))
                .when().post("/templates/{name}/preview", "welcome");

        assertThat(response.statusCode()).isEqualTo(503);
    }

    private void storeWithoutSes(String name) {
        templateRepository.save(new EmailTemplate(CLIENT, name, "Welcome!", "Hello {{name}}", null,
                CLIENT + "__" + name, Instant.now()));
    }

    private RequestSpecification request() {
        return RestAssured.given().port(port).basePath("/api").contentType("application/json")
                .header("X-API-Key", TEST_KEY);
    }
}
