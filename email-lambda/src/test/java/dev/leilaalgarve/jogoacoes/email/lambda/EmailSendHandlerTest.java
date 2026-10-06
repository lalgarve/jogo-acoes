package dev.leilaalgarve.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.CreateTemplateRequest;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.Template;
import software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the handler against the LocalStack SES container Dev Services starts automatically
 * for @QuarkusTest (docs/context/iteracao-4.md, decision 3) -- requires Docker, and fails without
 * it (memory/constitution.md, "Testes exigem a infraestrutura de pé").
 *
 * <p>Spec 05-031, T003: a queue message names a template already on SES, the client's sender
 * address and the template data; the handler turns it into a {@code SendTemplatedEmail}. What
 * LocalStack actually received is read back from its own SES store ({@code GET /_aws/ses}), not
 * from a mocked client -- except the message tags, which that store doesn't keep and
 * {@link SentRequestRecorder} records on the way out. LocalStack, like real SES in sandbox mode, only accepts a verified sender
 * and an existing template, so each test verifies its sender and creates its template first.
 */
@QuarkusTest
class EmailSendHandlerTest {

    @Inject
    EmailSendHandler handler;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SesClient sesClient;

    @ConfigProperty(name = "quarkus.ses.endpoint-override")
    String sesEndpoint;

    @Test
    void sendsTheTemplateFromTheClientSenderWithTheCorrelationIdTag() throws Exception {
        String sender = "no-reply+" + UUID.randomUUID() + "@client-a.example";
        String recipient = "success+player@simulator.amazonses.com";
        String templateName = "client-a__welcome-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        verify(sender);
        createTemplate(templateName);

        handler.handleRequest(eventWith(message(correlationId, sender, recipient, templateName,
                Map.of("name", "Ada"))), null);

        JsonNode sent = singleMessageSentBy(sender);
        assertThat(sent.get("Source").asText()).isEqualTo(sender);
        assertThat(sent.get("Destination").get("ToAddresses").get(0).asText()).isEqualTo(recipient);
        assertThat(sent.get("Template").asText()).isEqualTo(templateName);
        assertThat(objectMapper.readTree(sent.get("TemplateData").asText()))
                .isEqualTo(objectMapper.readTree("{\"name\":\"Ada\"}"));
        assertThat(SentRequestRecorder.sentFrom(sender).orElseThrow().tags())
                .containsExactly(MessageTag.builder().name("correlationId").value(correlationId).build());
    }

    @Test
    void sendsAnEmptyObjectWhenTheMessageHasNoTemplateData() throws Exception {
        String sender = "no-reply+" + UUID.randomUUID() + "@client-a.example";
        String templateName = "client-a__welcome-" + UUID.randomUUID();
        verify(sender);
        createTemplate(templateName);

        handler.handleRequest(eventWith(message(UUID.randomUUID().toString(), sender,
                "success+player@simulator.amazonses.com", templateName, null)), null);

        JsonNode sent = singleMessageSentBy(sender);
        assertThat(objectMapper.readTree(sent.get("TemplateData").asText())).isEqualTo(objectMapper.createObjectNode());
    }

    @Test
    void rejectsAMessageWithoutATemplateName() {
        SQSEvent event = eventWith(message(UUID.randomUUID().toString(), "no-reply@client-a.example",
                "player@example.com", null, Map.of()));

        assertThatThrownBy(() -> handler.handleRequest(event, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateName");
    }

    @Test
    void rejectsAMessageWithoutASenderAddress() {
        SQSEvent event = eventWith(message(UUID.randomUUID().toString(), null,
                "player@example.com", "client-a__welcome", Map.of()));

        assertThatThrownBy(() -> handler.handleRequest(event, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senderAddress");
    }

    @Test
    void rejectsTheOldRenderedMessage() {
        // Spec 05-031: subject/body already rendered is no longer a valid message.
        SQSEvent event = eventWith(Map.of("schemaVersion", "1", "correlationId", UUID.randomUUID().toString(),
                "recipientEmail", "player@example.com", "subject", "Assunto", "body", "<p>Corpo</p>"));

        assertThatThrownBy(() -> handler.handleRequest(event, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMalformedMessage() {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody("not-json");
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        assertThatThrownBy(() -> handler.handleRequest(event, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void verify(String sender) {
        sesClient.verifyEmailIdentity(VerifyEmailIdentityRequest.builder().emailAddress(sender).build());
    }

    private void createTemplate(String templateName) {
        sesClient.createTemplate(CreateTemplateRequest.builder()
                .template(Template.builder()
                        .templateName(templateName)
                        .subjectPart("Welcome, {{name}}")
                        .htmlPart("<p>Hello {{name}}</p>")
                        .build())
                .build());
    }

    /** The message shape email-service publishes (its EmailQueueMessage); null fields are left out. */
    private static Map<String, Object> message(String correlationId, String senderAddress, String recipientEmail,
                                               String templateName, Map<String, Object> templateData) {
        Map<String, Object> message = new HashMap<>();
        message.put("schemaVersion", "1");
        message.put("correlationId", correlationId);
        putIfPresent(message, "senderAddress", senderAddress);
        putIfPresent(message, "recipientEmail", recipientEmail);
        putIfPresent(message, "templateName", templateName);
        putIfPresent(message, "templateData", templateData);
        return message;
    }

    private static void putIfPresent(Map<String, Object> message, String key, Object value) {
        if (value != null) {
            message.put(key, value);
        }
    }

    private SQSEvent eventWith(Map<String, Object> payload) {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        try {
            message.setBody(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));
        return event;
    }

    /** LocalStack's own record of what SES was asked to send from {@code sender}. */
    private JsonNode singleMessageSentBy(String sender) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(sesEndpoint + "/_aws/ses?email="
                        + URLEncoder.encode(sender, StandardCharsets.UTF_8))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        List<JsonNode> messages = StreamSupport.stream(
                objectMapper.readTree(response.body()).get("messages").spliterator(), false).toList();
        assertThat(messages).hasSize(1);
        return messages.get(0);
    }
}
