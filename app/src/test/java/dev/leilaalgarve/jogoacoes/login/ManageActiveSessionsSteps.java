package dev.leilaalgarve.jogoacoes.login;

import dev.leilaalgarve.jogoacoes.common.testsupport.LoginLinkFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.ScenarioWorld;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.link.LinkRecord;
import dev.leilaalgarve.jogoacoes.link.LoginSession;
import dev.leilaalgarve.jogoacoes.link.LoginSessionRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-010 (T001): written against `GET /sessions`/`DELETE /sessions/{id}` before they
 * exist in docs/openapi.yaml, raw JSON (no generated model), so this is expected to 404 until
 * the tasks after T001 land. The second scenario doubles as the promised end-to-end check for
 * spec 05-009 -- two simulated devices, two recognizably different device labels in the list.
 */
public class ManageActiveSessionsSteps {

    private static final String SECOND_DEVICE = "second-device";
    private static final String OTHER_PLAYER_DEVICE = "other-player-device";

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final LoginLinkFixtures loginLinkFixtures;
    private final LoginSessionRepository loginSessionRepository;

    private Long otherPlayerSessionId;

    public ManageActiveSessionsSteps(ScenarioWorld world, UserMother userMother, LoginLinkFixtures loginLinkFixtures,
                                      LoginSessionRepository loginSessionRepository) {
        this.world = world;
        this.userMother = userMother;
        this.loginLinkFixtures = loginLinkFixtures;
        this.loginSessionRepository = loginSessionRepository;
    }

    @Given("they also log in on a second device sending User-Agent Client Hints for {string} and browser {string}")
    public void they_also_log_in_on_a_second_device(String platform, String browserBrand) {
        LinkRecord link = loginLinkFixtures.activeLink(world.getCurrentUser());
        Response response = world.request(SECOND_DEVICE)
                .header("Sec-CH-UA-Platform", "\"" + platform + "\"")
                .header("Sec-CH-UA-Platform-Version", "\"15.0.0\"")
                .header("Sec-CH-UA", "\"Not_A Brand\";v=\"24\", \"" + browserBrand + "\";v=\"131\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .when()
                .get("/login-links/{token}", link.getToken());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Given("another registered player has an active session")
    public void another_registered_player_has_an_active_session() {
        User otherPlayer = userMother.registeredPlayer();
        LinkRecord link = loginLinkFixtures.activeLink(otherPlayer);
        Response response = world.request(OTHER_PLAYER_DEVICE).when().get("/login-links/{token}", link.getToken());
        assertThat(response.statusCode()).isEqualTo(200);
        otherPlayerSessionId = activeSessionIdFor(otherPlayer.getId());
    }

    @When("they list their active sessions")
    public void they_list_their_active_sessions() {
        world.setLastResponse(world.request().when().get("/sessions"));
    }

    @When("they revoke the other device's session")
    public void they_revoke_the_other_device_s_session() {
        world.setLastResponse(world.request().when().delete("/sessions/{id}", mostRecentSessionIdFor(world.getCurrentUser().getId())));
    }

    @When("they revoke their own current session")
    public void they_revoke_their_own_current_session() {
        world.setLastResponse(world.request().when().delete("/sessions/{id}", activeSessionIdFor(world.getCurrentUser().getId())));
    }

    @When("they try to revoke a session that does not exist")
    public void they_try_to_revoke_a_session_that_does_not_exist() {
        world.setLastResponse(world.request().when().delete("/sessions/{id}", 999999));
    }

    @When("they try to revoke that other player's session")
    public void they_try_to_revoke_that_other_player_s_session() {
        world.setLastResponse(world.request().when().delete("/sessions/{id}", otherPlayerSessionId));
    }

    @Then("the system shows one session, marked as the current one")
    public void the_system_shows_one_session_marked_as_the_current_one() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        List<Map<String, Object>> sessions = world.getLastResponse().jsonPath().getList("$");
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).get("current")).isEqualTo(true);
        assertThat(sessions.get(0)).containsKeys("id", "deviceLabel", "createdAt");
    }

    @Then("the system shows two sessions with distinct device labels")
    public void the_system_shows_two_sessions_with_distinct_device_labels() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        List<Map<String, Object>> sessions = world.getLastResponse().jsonPath().getList("$");
        assertThat(sessions).hasSize(2);
        List<Object> labels = sessions.stream().map(s -> s.get("deviceLabel")).toList();
        assertThat(labels).doesNotHaveDuplicates();
        assertThat(sessions).filteredOn(s -> Boolean.TRUE.equals(s.get("current"))).hasSize(1);
    }

    @Then("that device's session no longer works for authenticated requests")
    public void that_device_s_session_no_longer_works_for_authenticated_requests() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(204);
        Response response = world.request(SECOND_DEVICE).when().get("/sessions");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Then("they are logged out of the current device")
    public void they_are_logged_out_of_the_current_device() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(204);
        Response response = world.request().when().get("/sessions");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Then("the system shows an error message without revealing whether the session exists")
    public void the_system_shows_an_error_message_without_revealing_whether_the_session_exists() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(404);
    }

    private Long activeSessionIdFor(Long userId) {
        List<LoginSession> sessions = loginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(userId);
        assertThat(sessions).as("active sessions for user " + userId).isNotEmpty();
        return sessions.get(0).getId();
    }

    private Long mostRecentSessionIdFor(Long userId) {
        List<LoginSession> sessions = loginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(userId);
        assertThat(sessions).as("active sessions for user " + userId).hasSizeGreaterThanOrEqualTo(2);
        return sessions.get(sessions.size() - 1).getId();
    }
}
