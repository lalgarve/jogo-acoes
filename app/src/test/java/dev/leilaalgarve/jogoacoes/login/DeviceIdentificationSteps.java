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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 05-009: the device label recorded on LOGIN_SESSION, read straight from the repository
 * since no endpoint exposes it yet (that arrives with spec 05-010). */
public class DeviceIdentificationSteps {

    private static final String SAMPLE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)";

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final LoginLinkFixtures loginLinkFixtures;
    private final LoginSessionRepository loginSessionRepository;

    private User currentUser;
    private LinkRecord currentLink;

    public DeviceIdentificationSteps(ScenarioWorld world, UserMother userMother, LoginLinkFixtures loginLinkFixtures,
                                      LoginSessionRepository loginSessionRepository) {
        this.world = world;
        this.userMother = userMother;
        this.loginLinkFixtures = loginLinkFixtures;
        this.loginSessionRepository = loginSessionRepository;
    }

    @Given("a registered player has an active login link")
    public void a_registered_player_has_an_active_login_link() {
        currentUser = userMother.registeredPlayer();
        currentLink = loginLinkFixtures.activeLink(currentUser);
    }

    @When("they click the login link sending User-Agent Client Hints for {string} {string} and browser {string} {string}")
    public void they_click_the_login_link_sending_client_hints(String platform, String platformVersion,
                                                                 String browserBrand, String browserVersion) {
        world.setLastResponse(world.request()
                .header("Sec-CH-UA-Platform", "\"" + platform + "\"")
                .header("Sec-CH-UA-Platform-Version", "\"" + platformVersion + "\"")
                .header("Sec-CH-UA", "\"Not_A Brand\";v=\"24\", \"" + browserBrand + "\";v=\"" + browserVersion + "\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .when()
                .get("/login-links/{token}", currentLink.getToken()));
    }

    @When("they click the login link sending only a traditional User-Agent header")
    public void they_click_the_login_link_sending_only_user_agent() {
        world.setLastResponse(world.request()
                .header("User-Agent", SAMPLE_USER_AGENT)
                .when()
                .get("/login-links/{token}", currentLink.getToken()));
    }

    @When("they click the login link without any device-identifying header")
    public void they_click_the_login_link_without_any_device_header() {
        world.setLastResponse(world.request()
                .header("User-Agent", "")
                .when()
                .get("/login-links/{token}", currentLink.getToken()));
    }

    @Then("the session recorded for that login shows the device as {string}")
    public void the_session_recorded_shows_the_device_as(String expectedLabel) {
        assertThat(latestSession().getDeviceId()).isEqualTo(expectedLabel);
    }

    @Then("the session recorded for that login shows the raw User-Agent as the device")
    public void the_session_recorded_shows_the_raw_user_agent() {
        assertThat(latestSession().getDeviceId()).isEqualTo(SAMPLE_USER_AGENT);
    }

    private LoginSession latestSession() {
        List<LoginSession> sessions = loginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(currentUser.getId());
        assertThat(sessions).as("active sessions for the logged-in user").isNotEmpty();
        return sessions.get(sessions.size() - 1);
    }
}
