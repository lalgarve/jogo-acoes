package dev.leilaalgarve.jogoacoes.email.client;

import dev.leilaalgarve.jogoacoes.JogoAcoesApplication;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceAuthenticationException;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceUnavailableException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the Feign client against the real email-service docker-compose.yml starts (spec
 * 05-034, T006), not a mock HTTP server: the real contract, the real X-API-Key validation (test
 * key of client jogo-acoes, spec 05-030) and SES on LocalStack behind it. Skipped, not failed,
 * when email-service isn't reachable -- same pattern as SqsEmailSenderDockerIntegrationTest; CI
 * starts it.
 *
 * <p>Sending (POST /emails) joins this test with spec 05-031.
 */
@SpringBootTest(properties = "email-service.base-url=" + EmailServiceClientIntegrationTest.EMAIL_SERVICE_URL)
class EmailServiceClientIntegrationTest {

    static final String EMAIL_SERVICE_URL = "http://localhost:8082/api";

    @BeforeAll
    static void requiresEmailService() {
        assumeTrue(reachable("localhost", 5432) && reachable("localhost", 8082),
                "Postgres and/or email-service not reachable on localhost -- skipping, this test needs "
                        + "`docker compose up -d --wait db email-service` (see docker-compose.yml)");
    }

    @Autowired
    private EmailServiceGateway gateway;

    @Autowired
    private EmailTemplateSynchronizer synchronizer;

    @Test
    void startingAppRegistersEveryTemplateInEmailService() {
        // The synchronizer already ran on ApplicationReadyEvent, while this context started.
        for (EmailServiceTemplate expected : EmailTemplateSynchronizer.templates()) {
            assertThat(gateway.findTemplate(expected.name())).contains(expected);
        }
    }

    @Test
    void synchronizingAgainLeavesEmailServiceInTheSameState() {
        List<EmailServiceTemplate> expected = EmailTemplateSynchronizer.templates();

        synchronizer.synchronize();
        synchronizer.synchronize();

        for (EmailServiceTemplate template : expected) {
            assertThat(gateway.findTemplate(template.name())).contains(template);
        }
    }

    @Test
    void repeatingTheSameUpdateGivesTheSameResult() {
        EmailServiceTemplate loginLink = templateNamed("login-link");

        gateway.upsertTemplate(loginLink);
        gateway.upsertTemplate(loginLink);

        assertThat(gateway.findTemplate("login-link")).contains(loginLink);
    }

    @Test
    void previewIsRenderedBySesAndCanBeRepeated() {
        Map<String, Object> variables = Map.of(
                "name", "Ana", "competitionName", "Copa de Inverno", "link", "https://jogo-acoes.example/login-links/abc");

        TemplatePreview first = gateway.preview("login-link-invite", variables);
        TemplatePreview second = gateway.preview("login-link-invite", variables);

        assertThat(first).isEqualTo(second);
        assertThat(first.subject()).isEqualTo("Convite para competir em Copa de Inverno");
        assertThat(first.body()).contains("Ana", "Copa de Inverno", "https://jogo-acoes.example/login-links/abc");
    }

    @Test
    void anUnknownTemplateIsNotFound() {
        assertThat(gateway.findTemplate("no-such-template")).isEmpty();
    }

    @Test
    void appDoesNotStartWithAnApiKeyEmailServiceRejects() {
        assertThatThrownBy(() -> startAppWith(EMAIL_SERVICE_URL,
                "dak_notARealKeyAtAllxxxxxxxxxxxxxxxxxxxxxxxxxxx"))
                .satisfies(e -> assertThat(causeChain(e)).hasAtLeastOneElementOfType(
                        EmailServiceAuthenticationException.class));
    }

    @Test
    void appFailsFastWhenEmailServiceIsUnreachable() {
        Instant start = Instant.now();

        // Port 9 (discard) is never served here: connection refused on every attempt.
        assertThatThrownBy(() -> startAppWith("http://localhost:9", null))
                .satisfies(e -> assertThat(causeChain(e)).hasAtLeastOneElementOfType(
                        EmailServiceUnavailableException.class));
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(60));
    }

    /** A whole app of its own: the startup sync is what has to fail. */
    private static void startAppWith(String baseUrl, String apiKey) {
        // Command-line arguments, not builder properties: those are defaults, and the test
        // application.yml's own email-service.api-key would win over them.
        List<String> args = new java.util.ArrayList<>(List.of("--server.port=0", "--email-service.base-url=" + baseUrl));
        if (apiKey != null) {
            args.add("--email-service.api-key=" + apiKey);
        }
        new SpringApplicationBuilder(JogoAcoesApplication.class).run(args.toArray(String[]::new)).close();
    }

    private static List<Throwable> causeChain(Throwable e) {
        List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable t = e; t != null && !chain.contains(t); t = t.getCause()) {
            chain.add(t);
        }
        return chain;
    }

    private static EmailServiceTemplate templateNamed(String name) {
        return EmailTemplateSynchronizer.templates().stream()
                .filter(template -> template.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static boolean reachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
