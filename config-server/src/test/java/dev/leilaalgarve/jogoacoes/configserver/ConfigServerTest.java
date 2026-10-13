package dev.leilaalgarve.jogoacoes.configserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-042: config-server serves the real config-repo/ at the repository root -- the files
 * staging and production get, not a test copy. Surefire runs from the module directory.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CONFIG_REPO_LOCATION=file:../config-repo/",
        "CONFIG_SERVER_USERNAME=" + ConfigServerTest.USERNAME,
        "CONFIG_SERVER_PASSWORD=" + ConfigServerTest.PASSWORD})
class ConfigServerTest {

    static final String USERNAME = "config";
    static final String PASSWORD = "config-test-not-a-secret";

    /** Keys whose value is a secret outside docker: only an env placeholder with no default. */
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|api-key|secret|pepper).*");
    private static final Pattern PLACEHOLDER_WITHOUT_DEFAULT = Pattern.compile("^\\$\\{[A-Z0-9_]+}$");

    @LocalServerPort
    int port;

    @Test
    void servesAppDockerConfigurationWithPlaceholdersLeftForTheClient() {
        Map<String, Object> source = singleSource("jogo-acoes", "docker");

        // Returned raw: the client resolves ${...} against its own environment variables.
        assertThat(source).containsEntry("links.base-url", "${LINKS_BASE_URL:http://localhost:8080/api}");
        assertThat(source).containsKey("spring.datasource.url");
    }

    @Test
    void servesEmailServiceDockerConfiguration() {
        Map<String, Object> source = singleSource("email-service", "docker");

        assertThat(source).containsEntry("spring.cloud.aws.ses.endpoint",
                "${SPRING_CLOUD_AWS_SES_ENDPOINT:http://localhost:4566}");
    }

    @ParameterizedTest
    @CsvSource({"jogo-acoes,staging", "jogo-acoes,production", "email-service,staging", "email-service,production"})
    void secretsOutsideDockerAreEnvironmentPlaceholdersWithoutDefault(String application, String profile) {
        Map<String, Object> source = singleSource(application, profile);

        assertThat(source).isNotEmpty();
        source.forEach((key, value) -> {
            if (SECRET_KEY.matcher(key).matches()) {
                assertThat(String.valueOf(value)).as(key).matches(PLACEHOLDER_WITHOUT_DEFAULT);
            }
        });
    }

    @Test
    void rejectsRequestsWithoutCredentials() {
        assertThat(status(RestClient.create())).isEqualTo(HttpStatusCode.valueOf(401));
    }

    @Test
    void rejectsWrongCredentials() {
        RestClient client = RestClient.builder()
                .defaultHeaders(headers -> headers.setBasicAuth(USERNAME, "wrong"))
                .build();

        assertThat(status(client)).isEqualTo(HttpStatusCode.valueOf(401));
    }

    private HttpStatusCode status(RestClient client) {
        return client.get().uri(baseUrl() + "/jogo-acoes/docker")
                .exchange((request, response) -> response.getStatusCode());
    }

    /**
     * Each service gets its own file and nothing else: no config-repo/application.yml shared
     * between services (plan.md, "Nomes dos arquivos").
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> singleSource(String application, String profile) {
        Map<String, Object> environment = authenticatedClient().get()
                .uri(baseUrl() + "/{application}/{profile}", application, profile)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> propertySources = (List<Map<String, Object>>) environment.get("propertySources");
        assertThat(propertySources).singleElement()
                .extracting(propertySource -> (String) propertySource.get("name"))
                .asString()
                .endsWith(application + "-" + profile + ".yml");
        return (Map<String, Object>) propertySources.getFirst().get("source");
    }

    private RestClient authenticatedClient() {
        return RestClient.builder()
                .defaultHeaders(headers -> headers.setBasicAuth(USERNAME, PASSWORD))
                .build();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
