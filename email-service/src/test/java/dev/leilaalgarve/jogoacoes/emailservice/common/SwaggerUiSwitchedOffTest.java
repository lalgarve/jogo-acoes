package dev.leilaalgarve.jogoacoes.emailservice.common;

import io.restassured.RestAssured;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-049: outside docker the Swagger UI is switched off (config-repo, staging and
 * production, with the same two properties as here), and then neither the page nor the contract
 * opens without an API key -- including the contract, a static file that springdoc's switches
 * alone leave served. Same setup as {@link SwaggerUiAccessTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:target/classes/application.yml",
        // docker-compose.yml's test credentials for config-server; never a real secret.
        "CONFIG_SERVER_USERNAME=config",
        "CONFIG_SERVER_PASSWORD=config-docker-not-a-secret",
        "springdoc.swagger-ui.enabled=false",
        "springdoc.api-docs.enabled=false"})
class SwaggerUiSwitchedOffTest {

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui.html", "/openapi.yaml", "/v3/api-docs", "/v3/api-docs/swagger-config"})
    void nothingOpensWithoutApiKey(String path) {
        int status = RestAssured.given().port(port).basePath("/api").get(path).statusCode();

        assertThat(status).isEqualTo(401);
    }
}
