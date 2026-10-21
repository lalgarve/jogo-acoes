package dev.leilaalgarve.jogoacoes.emailservice.disposable.steps;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.DisposableListFiles;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.EmailServiceScenarioWorld;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-048: repositories and imports through the real API, lists over real HTTP
 * ({@link DisposableListFiles}), and the domain table read back from the real database.
 * Repositories are named in the scenarios by their description.
 */
public class DisposableDomainSteps {

    private static final Duration IMPORT_TIMEOUT = Duration.ofSeconds(60);
    private static final String DISPOSABLE_ADMIN = "disposable-email:admin";

    private final EmailServiceScenarioWorld world;
    private final DisposableListFiles listFiles;
    private final JdbcTemplate jdbcTemplate;

    private final Map<String, Long> sourceIds = new HashMap<>();
    private final Map<Long, Long> executionBySource = new HashMap<>();
    private final Map<String, Map<String, Object>> listedBefore = new HashMap<>();

    public DisposableDomainSteps(EmailServiceScenarioWorld world, DisposableListFiles listFiles,
                                 JdbcTemplate jdbcTemplate) {
        this.world = world;
        this.listFiles = listFiles;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Given("no disposable domain repository is registered")
    public void no_disposable_domain_repository_is_registered() {
        jdbcTemplate.update("DELETE FROM email_service.disposable_domain_source");
    }

    @Given("operations authenticated with a key that has only the permission {string}")
    public void operations_authenticated_with_a_key_that_has_only_the_permission(String permission) {
        world.authenticateAs("operations", Set.of(permission));
    }

    @Given("a list file {string} with the lines:")
    @Given("the list file {string} now has the lines:")
    public void a_list_file_with_the_lines(String name, String lines) {
        listFiles.put(name, lines + "\n");
    }

    @Given("they added a repository for the list file {string} described as {string}")
    public void they_added_a_repository_for_the_list_file(String name, String description) {
        addRepository(listFiles.url(name), description);
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
    }

    @Given("a repository {string} whose server holds the download")
    @Given("a repository {string} that never answers")
    public void a_repository_whose_server_holds_the_download(String description) {
        addRepository(listFiles.heldServerUrl(), description);
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
    }

    @Given("a repository {string} that points at a list file that does not exist")
    public void a_repository_that_points_at_a_list_file_that_does_not_exist(String description) {
        addRepository(listFiles.url("does-not-exist.conf"), description);
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
    }

    @Given("a repository {string} that points at a port where nothing listens")
    public void a_repository_that_points_at_a_port_where_nothing_listens(String description) {
        addRepository(DisposableListFiles.refusedUrl(), description);
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
    }

    /** The test application.yml caps a list at 64 KB; this one is about 90 KB. */
    @Given("a repository {string} that serves a list larger than the limit")
    public void a_repository_that_serves_a_list_larger_than_the_limit(String description) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 6000; i++) {
            lines.append("domain-").append(i).append(".test\n");
        }
        addRepository(listFiles.put("large.conf", lines.toString()), description);
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
    }

    @Given("the import of every repository already ran")
    public void the_import_of_every_repository_already_ran() {
        they_ask_to_run_the_import_of_every_repository();
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        // Spec 05-054: an import that failed here used to surface only later, as a wrong count.
        for (Long executionId : executionBySource.values()) {
            Response execution = awaitEnd(executionId);
            assertThat(execution.jsonPath().getString("status")).as(execution.asString()).isEqualTo("COMPLETED");
        }
        for (Map<String, Object> source : listSources()) {
            listedBefore.put((String) source.get("description"), source);
        }
    }

    @Given("they asked to run the import of the repository {string}")
    public void they_asked_to_run_the_import_of_the_repository(String description) {
        they_ask_to_run_the_import_of_the_repository(description);
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
    }

    @When("they add a repository for the list file {string} described as {string}")
    public void they_add_a_repository_for_the_list_file(String name, String description) {
        addRepository(listFiles.url(name), description);
    }

    @When("they add a repository with the address {string} described as {string}")
    public void they_add_a_repository_with_the_address(String url, String description) {
        addRepository(url, description);
    }

    @When("they list the disposable domain repositories")
    public void they_list_the_disposable_domain_repositories() {
        world.setLastResponse(world.request().when().get("/disposable-domain-sources"));
    }

    @When("someone lists the disposable domain repositories without an API key")
    public void someone_lists_the_disposable_domain_repositories_without_an_api_key() {
        world.setLastResponse(world.requestWithoutApiKey().when().get("/disposable-domain-sources"));
    }

    @When("they ask to run the import of every repository")
    public void they_ask_to_run_the_import_of_every_repository() {
        startImport(null);
    }

    @When("they ask to run the import of the repository {string}")
    public void they_ask_to_run_the_import_of_the_repository(String description) {
        startImport(sourceId(description));
    }

    @When("they ask to run the import of the repository with id {long}")
    public void they_ask_to_run_the_import_of_the_repository_with_id(long id) {
        startImport(id);
    }

    @When("they read the import with id {long}")
    public void they_read_the_import_with_id(long id) {
        world.setLastResponse(world.request().when().get("/disposable-domain-imports/{id}", id));
    }

    @Then("the repository is created")
    public void the_repository_is_created() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
    }

    @Then("the repository {string} is listed with no download and no database update yet")
    public void the_repository_is_listed_with_no_download_and_no_database_update_yet(String description) {
        Map<String, Object> source = listed(description);
        assertThat(source.get("lastDownloadedAt")).isNull();
        assertThat(source.get("lastUpdatedAt")).isNull();
        assertThat(((Number) source.get("domainCount")).longValue()).isZero();
    }

    @Then("the repository {string} has a last download and a last database update")
    public void the_repository_has_a_last_download_and_a_last_database_update(String description) {
        Map<String, Object> source = listed(description);
        assertThat(source.get("lastDownloadedAt")).isNotNull();
        assertThat(source.get("lastUpdatedAt")).isNotNull();
    }

    @Then("the last download of {string} changed but its last database update did not")
    public void the_last_download_changed_but_its_last_database_update_did_not(String description) {
        Map<String, Object> before = listedBefore.get(description);
        Map<String, Object> now = listed(description);
        assertThat(now.get("lastDownloadedAt")).isNotEqualTo(before.get("lastDownloadedAt"));
        assertThat(now.get("lastUpdatedAt")).isEqualTo(before.get("lastUpdatedAt"));
    }

    @Then("the system rejects the repository because its address is already registered")
    public void the_system_rejects_the_repository_because_its_address_is_already_registered() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(409);
    }

    @Then("the system refuses because the key lacks the permission {string}")
    public void the_system_refuses_because_the_key_lacks_the_permission(String permission) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(403);
        assertThat(world.getLastResponse().jsonPath().getString("message"))
                .isEqualTo("Missing permission: " + permission);
    }

    @Then("the import is accepted with one execution for {string}")
    public void the_import_is_accepted_with_one_execution_for(String description) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        assertThat(executionBySource).containsOnlyKeys(sourceId(description));
    }

    @Then("the import of {string} ends {string} with {long} read, {long} skipped, {long} added and {long} removed")
    public void the_import_ends_with_counts(String description, String status, long read, long skipped,
                                            long added, long removed) {
        Response execution = awaitEnd(executionOf(description));
        assertThat(execution.jsonPath().getString("status")).as(execution.asString()).isEqualTo(status);
        assertThat(execution.jsonPath().getLong("read")).isEqualTo(read);
        assertThat(execution.jsonPath().getLong("skipped")).isEqualTo(skipped);
        assertThat(execution.jsonPath().getLong("added")).isEqualTo(added);
        assertThat(execution.jsonPath().getLong("removed")).isEqualTo(removed);
    }

    @Then("the import of {string} ends {string} with the reason {string}")
    public void the_import_ends_with_the_reason(String description, String status, String reason) {
        Response execution = awaitEnd(executionOf(description));
        assertThat(execution.jsonPath().getString("status")).as(execution.asString()).isEqualTo(status);
        assertThat(execution.jsonPath().getString("exitMessage")).contains(reason);
    }

    @Then("the disposable domains of {string} are {string}")
    public void the_disposable_domains_of_are(String description, String domains) {
        assertThat(jdbcTemplate.queryForList(
                "SELECT domain FROM email_service.disposable_domain WHERE source_id = ? ORDER BY domain",
                String.class, sourceId(description)))
                .containsExactlyElementsOf(Arrays.stream(domains.split(",")).map(String::trim).toList());
    }

    @Then("the domain {string} is still a disposable domain")
    public void the_domain_is_still_a_disposable_domain(String domain) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_service.disposable_domain WHERE domain = ?", Integer.class, domain))
                .isPositive();
    }

    @Then("the system rejects the import because one is already running")
    public void the_system_rejects_the_import_because_one_is_already_running() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(409);
    }

    @Then("the system shows an error that the repository does not exist")
    @Then("the system shows an error that the import does not exist")
    public void the_system_shows_an_error_that_it_does_not_exist() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(404);
    }

    private void addRepository(String url, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("url", url.isEmpty() ? null : url);
        body.put("description", description);
        Response response = world.request().body(body).when().post("/disposable-domain-sources");
        world.setLastResponse(response);
        if (response.statusCode() == 201) {
            sourceIds.put(description, response.jsonPath().getLong("id"));
        }
    }

    private void startImport(Long sourceId) {
        Response response = sourceId == null
                ? world.request().when().post("/disposable-domain-imports")
                : world.request().body(Map.of("sourceId", sourceId)).when().post("/disposable-domain-imports");
        world.setLastResponse(response);
        executionBySource.clear();
        if (response.statusCode() == 202) {
            List<Map<String, Object>> imports = response.jsonPath().getList("imports");
            for (Map<String, Object> started : imports) {
                executionBySource.put(((Number) started.get("sourceId")).longValue(),
                        ((Number) started.get("executionId")).longValue());
            }
        }
    }

    private Response awaitEnd(long executionId) {
        Instant deadline = Instant.now().plus(IMPORT_TIMEOUT);
        while (true) {
            Response execution = world.request().when().get("/disposable-domain-imports/{id}", executionId);
            assertThat(execution.statusCode()).as(execution.asString()).isEqualTo(200);
            String status = execution.jsonPath().getString("status");
            if (!Set.of("STARTING", "STARTED", "STOPPING").contains(status) || Instant.now().isAfter(deadline)) {
                return execution;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private long executionOf(String description) {
        Long executionId = executionBySource.get(sourceId(description));
        assertThat(executionId).as("no execution started for " + description).isNotNull();
        return executionId;
    }

    private long sourceId(String description) {
        Long id = sourceIds.get(description);
        assertThat(id).as("no repository described as " + description).isNotNull();
        return id;
    }

    private Map<String, Object> listed(String description) {
        return listSources().stream()
                .filter(source -> description.equals(source.get("description")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("repository " + description + " is not listed"));
    }

    private List<Map<String, Object>> listSources() {
        Response response = world.request().when().get("/disposable-domain-sources");
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath().getList("$");
    }
}
