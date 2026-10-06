package dev.leilaalgarve.jogoacoes.email.client;

import dev.leilaalgarve.jogoacoes.JogoAcoesApplication;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.email.EmailRequest;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.email.EmailTemplate;
import dev.leilaalgarve.jogoacoes.email.SentEmail;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceAuthenticationException;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceUnavailableException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the Feign client against the real email-service docker-compose.yml starts (spec
 * 05-034, T006), not a mock HTTP server: the real contract, the real X-API-Key validation (test
 * key of client jogo-acoes, spec 05-030) and SES on LocalStack behind it. Needs the whole
 * Compose infrastructure up -- db, localstack (with email-lambda) and email-service, with the test
 * key restored (scripts/test-api-key.sh); missing infrastructure is an error, never a skip
 * (constitution, "Testes exigem a infraestrutura de pé").
 */
@SpringBootTest(properties = {
        "email-service.base-url=" + EmailServiceClientIntegrationTest.EMAIL_SERVICE_URL,
        "email.sender=email-service"})
class EmailServiceClientIntegrationTest {

    static final String EMAIL_SERVICE_URL = "http://localhost:8082/api";

    /** LocalStack, where email-lambda hands each e-mail to SES. */
    private static final String SES_STORE = "http://localhost:4566/_aws/ses";

    /** The sender scripts/test-api-key.sh configures for client jogo-acoes. */
    private static final String SENDER = "no-reply@jogo-acoes.example";

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private SentEmailRepository sentEmailRepository;

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

    static Stream<Arguments> everyKindOfEmail() {
        return Stream.of(
                Arguments.of(EmailTemplate.INVITE, null, "Copa de Inverno", RequestType.INVITE, "invite"),
                Arguments.of(EmailTemplate.REGISTRATION_LINK, null, "Copa de Inverno", RequestType.REQUEST,
                        "registration-link"),
                Arguments.of(EmailTemplate.LOGIN_LINK, "Ana", null, null, "login-link"),
                Arguments.of(EmailTemplate.LOGIN_LINK, "Ana", "Copa de Inverno", RequestType.INVITE, "login-link-invite"),
                Arguments.of(EmailTemplate.LOGIN_LINK, "Ana", "Copa de Inverno", RequestType.REQUEST,
                        "login-link-request"));
    }

    @ParameterizedTest
    @MethodSource("everyKindOfEmail")
    void sendingRecordsEmailServiceIdAndReachesSesWithTheTemplate(EmailTemplate template, String name,
            String competitionName, RequestType origin, String expectedTemplateName) throws Exception {
        // Short qualifier: the local part has to stay within 64 characters (@Email in the generated client).
        String recipient = unique("send");
        String link = "https://jogo-acoes.example/login-links/" + UUID.randomUUID();

        emailSender.send(new EmailRequest(null, recipient, name, competitionName, origin, link, template));

        SentEmail recorded = sentEmailRepository.findByLink(link).orElseThrow();
        assertThat(recorded.getEmailServiceId()).isNotNull();
        JSONObject sent = sentToSes(recipient, Duration.ofSeconds(30))
                .orElseThrow(() -> new AssertionError("no e-mail to " + recipient + " reached SES on LocalStack"));
        assertThat(sent.getString("Template")).isEqualTo("jogo-acoes__" + expectedTemplateName);
        assertThat(new JSONObject(sent.getString("TemplateData")).getString("link")).isEqualTo(link);
    }

    /** Polls LocalStack's own SES store: email-lambda sends asynchronously, off the queue. */
    private static Optional<JSONObject> sentToSes(String recipient, Duration timeout) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        URI uri = URI.create(SES_STORE + "?email=" + URLEncoder.encode(SENDER, StandardCharsets.UTF_8));
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JSONArray messages = new JSONObject(response.body()).getJSONArray("messages");
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                JSONArray to = message.getJSONObject("Destination").getJSONArray("ToAddresses");
                if (to.length() > 0 && recipient.equals(to.getString(0))) {
                    return Optional.of(message);
                }
            }
            Thread.sleep(500);
        }
        return Optional.empty();
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
}
