package dev.leilaalgarve.jogoacoes.emailservice.send.steps;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.EmailServiceScenarioWorld;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.ScenarioSenders;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.SendQueue;
import dev.leilaalgarve.jogoacoes.emailservice.send.EmailSend;
import dev.leilaalgarve.jogoacoes.emailservice.send.EmailSendRepository;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-031, T002: POST /emails against the real server, PostgreSQL and LocalStack queue --
 * the queued message is read back from the queue itself ({@link SendQueue}), not from a mock.
 */
public class SendEmailSteps {

    /** How long to wait for a message that should be on the queue -- the publish is synchronous. */
    private static final int RECEIVE_WAIT_SECONDS = 2;

    private final EmailServiceScenarioWorld world;
    private final ScenarioSenders senders;
    private final SendQueue sendQueue;
    private final EmailSendRepository sendRepository;
    private final EmailTemplateRepository templateRepository;

    private JsonNode receivedMessage;

    public SendEmailSteps(EmailServiceScenarioWorld world, ScenarioSenders senders, SendQueue sendQueue,
                          EmailSendRepository sendRepository, EmailTemplateRepository templateRepository) {
        this.world = world;
        this.senders = senders;
        this.sendQueue = sendQueue;
        this.sendRepository = sendRepository;
        this.templateRepository = templateRepository;
    }

    @Given("operations configured the sender address {string} for them")
    public void operations_configured_the_sender_address_for_them(String address) {
        senders.configure(world.getClientName(), address);
    }

    @Given("the send queue is unavailable")
    public void the_send_queue_is_unavailable() {
        sendQueue.delete();
    }

    @When("they send the template {string} to {string}")
    public void they_send_the_template_to(String templateName, String recipientEmail) {
        world.setLastResponse(send(Map.of("templateName", templateName, "recipientEmail", recipientEmail)));
    }

    @When("they send the template {string} to {string} with the variable {string} set to {string}")
    public void they_send_the_template_to_with_the_variable(String templateName, String recipientEmail,
                                                            String variable, String value) {
        world.setLastResponse(send(Map.of("templateName", templateName, "recipientEmail", recipientEmail,
                "templateData", Map.of(variable, value))));
    }

    @When("they send a request without a template name to {string}")
    public void they_send_a_request_without_a_template_name(String recipientEmail) {
        world.setLastResponse(send(Map.of("recipientEmail", recipientEmail)));
    }

    @When("they send a request for the template {string} without a recipient")
    public void they_send_a_request_without_a_recipient(String templateName) {
        world.setLastResponse(send(Map.of("templateName", templateName)));
    }

    @When("a send request is made without an API key")
    public void a_send_request_is_made_without_an_api_key() {
        world.setLastResponse(world.requestWithoutApiKey()
                .body(Map.of("templateName", "welcome", "recipientEmail", "player@example.com"))
                .when().post("/emails"));
    }

    @Then("the send is accepted as queued")
    public void the_send_is_accepted_as_queued() {
        Response response = world.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.jsonPath().getString("status")).isEqualTo("QUEUED");
        assertThat(UUID.fromString(response.jsonPath().getString("id"))).isNotNull();
    }

    @Then("the send queue has one message for {string} whose correlation id is the returned id")
    public void the_send_queue_has_one_message_whose_correlation_id_is_the_returned_id(String recipientEmail) {
        List<JsonNode> messages = sendQueue.receiveAll(RECEIVE_WAIT_SECONDS);
        assertThat(messages).hasSize(1);
        receivedMessage = messages.get(0);
        assertThat(receivedMessage.get("recipientEmail").asString()).isEqualTo(recipientEmail);
        assertThat(receivedMessage.get("correlationId").asString()).isEqualTo(returnedId().toString());
    }

    @Then("that message asks SES to send the template {string} of {string} from {string}")
    public void that_message_asks_ses_to_send_the_template_from(String templateName, String clientName,
                                                                String senderAddress) {
        String sesTemplateName = templateRepository.findByClientIdAndName(clientName, templateName)
                .orElseThrow().getSesTemplateName();
        assertThat(receivedMessage.get("templateName").asString()).isEqualTo(sesTemplateName);
        assertThat(receivedMessage.get("senderAddress").asString()).isEqualTo(senderAddress);
    }

    @Then("that message carries the variable {string} set to {string}")
    public void that_message_carries_the_variable(String variable, String value) {
        assertThat(templateData()).containsExactly(Map.entry(variable, value));
    }

    @Then("that message carries no variables")
    public void that_message_carries_no_variables() {
        assertThat(templateData()).isEmpty();
    }

    @Then("the send is recorded under the returned id")
    public void the_send_is_recorded_under_the_returned_id() {
        EmailSend send = sendRepository.findById(returnedId()).orElseThrow();
        assertThat(send.getClientId()).isEqualTo(world.getClientName());
        assertThat(send.getRecipientEmail()).isEqualTo(receivedMessage.get("recipientEmail").asString());
    }

    @Then("nothing is put on the send queue")
    public void nothing_is_put_on_the_send_queue() {
        assertThat(sendQueue.receiveAll(RECEIVE_WAIT_SECONDS)).isEmpty();
    }

    @Then("no send is recorded")
    public void no_send_is_recorded() {
        assertThat(sendRepository.count()).isZero();
    }

    @Then("the system rejects the send because no sender address is configured")
    public void the_system_rejects_the_send_because_no_sender_address_is_configured() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(409);
        assertThat(world.getLastResponse().jsonPath().getString("message")).isNotBlank();
    }

    @Then("the system rejects the request as malformed")
    public void the_system_rejects_the_request_as_malformed() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(400);
        assertThat(world.getLastResponse().jsonPath().getString("message")).isNotBlank();
    }

    @Then("the system answers that the service is unavailable")
    public void the_system_answers_that_the_service_is_unavailable() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(503);
        assertThat(world.getLastResponse().jsonPath().getString("message")).isNotBlank();
    }

    private Response send(Map<String, Object> body) {
        return world.request().body(body).when().post("/emails");
    }

    private UUID returnedId() {
        return UUID.fromString(world.getLastResponse().jsonPath().getString("id"));
    }

    private Map<String, String> templateData() {
        Map<String, String> data = new HashMap<>();
        receivedMessage.get("templateData").properties()
                .forEach(entry -> data.put(entry.getKey(), entry.getValue().asString()));
        return data;
    }
}
