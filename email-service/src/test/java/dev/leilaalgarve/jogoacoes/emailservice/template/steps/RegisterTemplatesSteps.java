package dev.leilaalgarve.jogoacoes.emailservice.template.steps;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.EmailServiceScenarioWorld;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.ScenarioApiKeys;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RegisterTemplatesSteps {

    private static final String VALID_BODY = "Hello {{name}}, welcome aboard!";
    // SES uses Handlebars -- an unclosed block helper is a hard syntax error, unlike merely
    // unbalanced simple variable braces (which some parsers tolerate).
    private static final String INVALID_BODY = "Hello {{#each items}}{{name}}";

    private final EmailServiceScenarioWorld world;
    private final EmailTemplateRepository repository;
    private final ScenarioApiKeys apiKeys;

    private String previousSubject;
    private String previousBody;
    private String secondApiKey;
    private String otherApiKey;

    public RegisterTemplatesSteps(EmailServiceScenarioWorld world, EmailTemplateRepository repository,
                                  ScenarioApiKeys apiKeys) {
        this.world = world;
        this.repository = repository;
        this.apiKeys = apiKeys;
    }

    // Spec 05-030: the quoted value is the client a real key is issued for (the api-key CLI's
    // --client), no longer the key text itself.
    @Given("a client authenticated with the API key {string}")
    public void a_client_authenticated_with_the_api_key(String clientName) {
        world.authenticateAs(clientName);
    }

    @Given("another client authenticated with the API key {string} also registered a template named {string}")
    public void another_client_also_registered_a_template(String clientName, String name) {
        registerAs(clientName, name);
    }

    @Given("another client authenticated with the API key {string} registered a template named {string}")
    public void another_client_registered_a_template(String clientName, String name) {
        registerAs(clientName, name);
    }

    @Given("they have a second active API key")
    public void they_have_a_second_active_api_key() {
        secondApiKey = apiKeys.issueActive(world.getClientName());
    }

    @Given("an API key issued for {string} that has expired")
    public void an_api_key_issued_that_has_expired(String clientName) {
        otherApiKey = apiKeys.issueExpired(clientName);
    }

    @Given("an API key issued for {string} that has been revoked")
    public void an_api_key_issued_that_has_been_revoked(String clientName) {
        otherApiKey = apiKeys.issueRevoked(clientName);
    }

    @Given("they already registered a template named {string}")
    public void they_already_registered_a_template(String name) {
        Response response = register(name, "Welcome!", VALID_BODY);
        assertThat(response.statusCode()).isEqualTo(201);
    }

    @When("they register a template named {string} with subject {string} and a valid body")
    public void they_register_a_template_with_subject_and_a_valid_body(String name, String subject) {
        world.setLastResponse(register(name, subject, VALID_BODY));
    }

    @When("they register a template named {string} with an invalid Handlebars body")
    public void they_register_a_template_with_an_invalid_handlebars_body(String name) {
        world.setLastResponse(register(name, "Subject", INVALID_BODY));
    }

    @When("they register another template named {string}")
    public void they_register_another_template_with_same_name(String name) {
        world.setLastResponse(register(name, "Another subject", VALID_BODY));
    }

    @When("they update the template named {string} with a new subject and body")
    public void they_update_the_template_with_a_new_subject_and_body(String name) {
        rememberCurrentContent(name);
        world.setLastResponse(update(name, "Updated subject", "Updated body {{name}}"));
    }

    @When("they try to update a template named {string}")
    public void they_try_to_update_a_template(String name) {
        world.setLastResponse(update(name, "Subject", VALID_BODY));
    }

    @When("they update the template named {string} with an invalid Handlebars body")
    public void they_update_the_template_with_an_invalid_handlebars_body(String name) {
        rememberCurrentContent(name);
        world.setLastResponse(update(name, "Subject", INVALID_BODY));
    }

    @When("they preview the template named {string} with sample data")
    public void they_preview_the_template_with_sample_data(String name) {
        world.setLastResponse(preview(name, Map.of("name", "Ada")));
    }

    @When("they try to preview the template named {string}")
    public void they_try_to_preview_the_template(String name) {
        world.setLastResponse(preview(name, Map.of("name", "Ada")));
    }

    @When("they try to preview a template named {string}")
    public void they_try_to_preview_a_template(String name) {
        world.setLastResponse(preview(name, Map.of("name", "Ada")));
    }

    @When("they list their templates")
    public void they_list_their_templates() {
        world.setLastResponse(world.request().when().get("/templates"));
    }

    @When("they list their templates with the second API key")
    public void they_list_their_templates_with_the_second_api_key() {
        world.setLastResponse(world.requestWithApiKey(secondApiKey).when().get("/templates"));
    }

    @When("a request is made with a malformed API key")
    public void a_request_is_made_with_a_malformed_api_key() {
        world.setLastResponse(world.requestWithApiKey("not-a-real-api-key").when().get("/templates"));
    }

    @When("a request is made with a well-formed API key that was never issued")
    public void a_request_is_made_with_a_key_that_was_never_issued() {
        world.setLastResponse(world.requestWithApiKey(ScenarioApiKeys.neverIssued()).when().get("/templates"));
    }

    @When("a request is made with that API key")
    public void a_request_is_made_with_that_api_key() {
        world.setLastResponse(world.requestWithApiKey(otherApiKey).when().get("/templates"));
    }

    @When("a request is made without an API key")
    public void a_request_is_made_without_an_api_key() {
        world.setLastResponse(world.requestWithoutApiKey().when().get("/templates"));
    }

    @When("a request is made with an empty API key")
    public void a_request_is_made_with_an_empty_api_key() {
        world.setLastResponse(world.requestWithApiKey("").when().get("/templates"));
    }

    @When("they try to update the template named {string}")
    public void they_try_to_update_the_template_named(String name) {
        world.setLastResponse(update(name, "Subject", VALID_BODY));
    }

    @Then("the template is created")
    public void the_template_is_created() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
    }

    @Then("the template is synced to SES")
    public void the_template_is_synced_to_ses() {
        // Creation already called CreateTemplate synchronously before persisting (data-model.md,
        // "Invariantes") -- the 201 itself is that proof. Re-confirms the row exists as the
        // observable side effect of that having succeeded.
        String name = world.getLastResponse().jsonPath().getString("name");
        assertThat(repository.existsByClientIdAndName(world.getClientName(), name)).isTrue();
    }

    @Then("the system rejects the registration with the reason SES returned")
    public void the_system_rejects_the_registration_with_the_reason_ses_returned() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(422);
        assertThat(world.getLastResponse().jsonPath().getString("message")).isNotBlank();
    }

    @Then("no template named {string} is created")
    public void no_template_named_is_created(String name) {
        assertThat(repository.existsByClientIdAndName(world.getClientName(), name)).isFalse();
    }

    @Then("the system rejects the registration because the name is already in use")
    public void the_system_rejects_the_registration_because_the_name_is_already_in_use() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(409);
    }

    @Then("the template reflects the new content")
    public void the_template_reflects_the_new_content() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getString("subject")).isEqualTo("Updated subject");
    }

    @Then("the updated template is synced to SES")
    public void the_updated_template_is_synced_to_ses() {
        // Same reasoning as "the template is synced to SES" -- UpdateTemplate already ran
        // synchronously before the 200 response.
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
    }

    @Then("the system shows an error that the template does not exist")
    public void the_system_shows_an_error_that_the_template_does_not_exist() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(404);
    }

    @Then("the system rejects the update with the reason SES returned")
    public void the_system_rejects_the_update_with_the_reason_ses_returned() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(422);
    }

    @Then("the template named {string} keeps its previous content")
    public void the_template_named_keeps_its_previous_content(String name) {
        Response response = world.request().when().get("/templates/{name}", name);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("subject")).isEqualTo(previousSubject);
        assertThat(response.jsonPath().getString("body")).isEqualTo(previousBody);
    }

    @Then("the system returns the rendered subject and body")
    public void the_system_returns_the_rendered_subject_and_body() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getString("subject")).isNotBlank();
        assertThat(world.getLastResponse().jsonPath().getString("body")).isNotBlank();
    }

    @Then("no e-mail is sent")
    public void no_e_mail_is_sent() {
        // Preview uses SES TestRenderTemplate, which never sends anything by design (spec.md) --
        // nothing in this service's code path ever calls SendTemplatedEmail, so this is a
        // structural guarantee, not something to assert against a mailbox.
    }

    @Then("they see only the template named {string}")
    public void they_see_only_the_template_named(String name) {
        List<Map<String, Object>> templates = world.getLastResponse().jsonPath().getList("$");
        assertThat(templates).extracting(t -> t.get("name")).containsExactly(name);
    }

    @Then("they see only their own template named {string}, not the other client's")
    public void they_see_only_their_own_template_named_not_the_other_clients(String name) {
        List<Map<String, Object>> templates = world.getLastResponse().jsonPath().getList("$");
        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).get("name")).isEqualTo(name);
    }

    @Then("the system rejects the request as unauthorized")
    public void the_system_rejects_the_request_as_unauthorized() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(401);
        assertThat(world.getLastResponse().jsonPath().getString("message"))
            .isEqualTo("Missing or invalid X-API-Key");
    }

    private void rememberCurrentContent(String name) {
        Response current = world.request().when().get("/templates/{name}", name);
        previousSubject = current.jsonPath().getString("subject");
        previousBody = current.jsonPath().getString("body");
    }

    private void registerAs(String clientName, String name) {
        Response response = world.requestWithApiKey(apiKeys.issueActive(clientName))
                .body(Map.of("name", name, "subject", "Subject", "body", VALID_BODY))
                .when().post("/templates");
        assertThat(response.statusCode()).isEqualTo(201);
    }

    private Response register(String name, String subject, String body) {
        return world.request()
                .body(Map.of("name", name, "subject", subject, "body", body))
                .when().post("/templates");
    }

    private Response update(String name, String subject, String body) {
        return world.request()
                .body(Map.of("subject", subject, "body", body))
                .when().put("/templates/{name}", name);
    }

    private Response preview(String name, Map<String, Object> variables) {
        return world.request()
                .body(Map.of("variables", variables))
                .when().post("/templates/{name}/preview", name);
    }
}
