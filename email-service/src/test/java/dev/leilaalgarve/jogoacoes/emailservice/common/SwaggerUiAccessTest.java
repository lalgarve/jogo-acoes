package dev.leilaalgarve.jogoacoes.emailservice.common;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-049: the Swagger UI and the contract it shows open without an API key, while every
 * route of the API itself still asks for one. The real server on a random port, the real
 * {@code ApiKeyAuthenticationFilter} -- no request reaches it mocked.
 *
 * <p>With the packaged application.yml (target/classes), not the suite's, which replaces it: the
 * springdoc settings under test are the ones the service starts with, read from config-server
 * like outside the test suite (same setup as {@link ConfigServerClientIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:target/classes/application.yml",
        // docker-compose.yml's test credentials for config-server; never a real secret.
        "CONFIG_SERVER_USERNAME=config",
        "CONFIG_SERVER_PASSWORD=config-docker-not-a-secret"})
class SwaggerUiAccessTest {

    @LocalServerPort
    private int port;

    @Test
    void swaggerUiPageOpensWithoutApiKey() {
        Response response = withoutApiKey().get("/swagger-ui.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).contains("swagger-ui");
    }

    @Test
    void contractOpensWithoutApiKeyAndIsTheStaticOne() {
        Response response = withoutApiKey().get("/openapi.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).contains("title: Serviço de E-mail");
    }

    /** The page fetches this on load; a 401 here shows "Failed to load remote configuration". */
    @Test
    void swaggerUiConfigurationOpensWithoutApiKey() {
        assertThat(withoutApiKey().get("/v3/api-docs/swagger-config").statusCode()).isEqualTo(200);
    }

    @Test
    void apiRoutesStillAskForAnApiKey() {
        Response response = withoutApiKey().get("/templates");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("message")).isEqualTo("Missing or invalid X-API-Key");
    }

    /** Contract-first: nothing is generated from the code's annotations. */
    @Test
    void noContractIsGeneratedFromAnnotations() {
        Response response = withoutApiKey().get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> paths = response.jsonPath().getMap("paths");
        assertThat(paths == null ? Map.of() : paths).isEmpty();
    }

    private RequestSpecification withoutApiKey() {
        return RestAssured.given().port(port).basePath("/api");
    }
}
