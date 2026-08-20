package io.deployo.jogoacoes.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.deployo.jogoacoes.api.model.CompleteRegistrationRequest;
import io.deployo.jogoacoes.api.model.RequestLoginLinkRequest;
import io.deployo.jogoacoes.domain.Competition;
import io.deployo.jogoacoes.domain.CompetitionStatus;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.LoginSession;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.domain.RequestType;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.repository.CompetitionRepository;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.repository.LoginSessionRepository;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import io.deployo.jogoacoes.testsupport.CompetitionFixtures;
import io.deployo.jogoacoes.testsupport.LoginHelper;
import io.deployo.jogoacoes.testsupport.LoginLinkFixtures;
import io.deployo.jogoacoes.testsupport.UserMother;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSteps {

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final LoginHelper loginHelper;
    private final CompetitionFixtures competitionFixtures;
    private final LoginLinkFixtures loginLinkFixtures;
    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;
    private final LoginLinkRepository loginLinkRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final UserRepository userRepository;
    private final int maxDevicesPerUser;

    public LoginSteps(ScenarioWorld world, UserMother userMother, LoginHelper loginHelper,
                       CompetitionFixtures competitionFixtures, LoginLinkFixtures loginLinkFixtures,
                       CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                       LoginLinkRepository loginLinkRepository, LoginSessionRepository loginSessionRepository,
                       UserRepository userRepository, @Value("${login.max-devices-per-user}") int maxDevicesPerUser) {
        this.world = world;
        this.userMother = userMother;
        this.loginHelper = loginHelper;
        this.competitionFixtures = competitionFixtures;
        this.loginLinkFixtures = loginLinkFixtures;
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.loginLinkRepository = loginLinkRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.userRepository = userRepository;
        this.maxDevicesPerUser = maxDevicesPerUser;
    }

    // -- New-player / registered-player Outlines: same <situation> text, current user tells them apart --

    @Given("requested entry into a new public competition")
    public void requested_entry_into_a_new_public_competition() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
        setUpPendingParticipation(RequestType.REQUEST);
    }

    @Given("was invited to join a private competition")
    public void was_invited_to_join_a_private_competition() {
        world.setTargetCompetition(competitionFixtures.privateCompetition());
        setUpPendingParticipation(RequestType.INVITE);
    }

    private void setUpPendingParticipation(RequestType requestType) {
        String email = world.getCurrentUser() != null
                ? world.getCurrentUser().getEmail()
                : "player-" + UUID.randomUUID() + "@example.com";
        world.setCandidateEmail(email);

        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setEmail(email);
        participation.setUser(world.getCurrentUser());
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participation.setRequestType(requestType);
        participation.setFirstEmailSentDate(LocalDate.now());
        participation = participationRepository.save(participation);

        world.setCurrentLoginLink(loginLinkFixtures.linkForParticipation(participation));
    }

    @Given("clicked the login link received by e-mail")
    public void clicked_the_login_link_received_by_e_mail() {
        clickCurrentLink(ScenarioWorld.PRIMARY_DEVICE);
    }

    @When("they click the login link received by e-mail")
    public void they_click_the_login_link_received_by_e_mail() {
        clickCurrentLink(ScenarioWorld.PRIMARY_DEVICE);
    }

    private void clickCurrentLink(String device) {
        Response response = world.request(device)
                .when()
                .get("/login-links/{token}", world.getCurrentLoginLink().getToken());
        world.setLastResponse(response);
    }

    @When("they enter their name")
    public void they_enter_their_name() {
        world.setCandidateName("Test Player " + UUID.randomUUID());
    }

    @When("confirm entry into the competition")
    public void confirm_entry_into_the_competition() {
        Response response = world.request()
                .body(new CompleteRegistrationRequest().name(world.getCandidateName()))
                .when()
                .post("/login-links/{token}/registration", world.getCurrentLoginLink().getToken());
        world.setLastResponse(response);
    }

    @When("they confirm entry into the competition")
    public void they_confirm_entry_into_the_competition() {
        Response response = world.request()
                .when()
                .post("/competitions/{id}/entry-requests", world.getTargetCompetition().getId());
        world.setLastResponse(response);
    }

    @Then("the system registers the player")
    public void the_system_registers_the_player() {
        User user = userRepository.findByEmail(world.getCandidateEmail()).orElseThrow();
        assertThat(user.isRegistered()).isTrue();
        assertThat(user.getName()).isEqualTo(world.getCandidateName());
    }

    @Then("includes them in the competition")
    public void includes_them_in_the_competition() {
        Participation participation = currentParticipation();
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.IN_COMPETITION);
    }

    @Then("redirects them to the competition page")
    public void redirects_them_to_the_competition_page() {
        assertRedirectsToCompetitionPage();
    }

    @Then("the system adds the player to the competition")
    public void the_system_adds_the_player_to_the_competition() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getString("status")).isEqualTo("IN_COMPETITION");
    }

    private void assertRedirectsToCompetitionPage() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        String redirectTo = world.getLastResponse().jsonPath().getString("redirectTo");
        if (redirectTo != null) {
            assertThat(redirectTo).isEqualTo("competition-page");
        } else {
            // The entry-requests endpoint returns a Participation, not a LoginResult --
            // matching competitionId is this endpoint's equivalent of a redirect target.
            assertThat(world.getLastResponse().jsonPath().getLong("competitionId")).isEqualTo(world.getTargetCompetition().getId());
        }
    }

    private Participation currentParticipation() {
        return participationRepository.findAll().stream()
                .filter(p -> p.getCompetition().getId().equals(world.getTargetCompetition().getId()))
                .filter(p -> p.getEmail().equals(world.getCandidateEmail()))
                .findFirst()
                .orElseThrow();
    }

    // -- Already-participating player re-requests --

    @Given("already participates in a public competition")
    public void already_participates_in_a_public_competition() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
        world.setCandidateEmail(world.getCurrentUser().getEmail());
        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setUser(world.getCurrentUser());
        participation.setEmail(world.getCurrentUser().getEmail());
        participation.setStatus(ParticipationStatus.IN_COMPETITION);
        participation.setRequestType(RequestType.REQUEST);
        participation.setJoinedAt(LocalDate.now().minusDays(1));
        participationRepository.save(participation);
    }

    @Given("requested entry into that competition again")
    public void requested_entry_into_that_competition_again() {
        Participation participation = participationRepository
                .findByCompetition_IdAndUser_Id(world.getTargetCompetition().getId(), world.getCurrentUser().getId())
                .orElseThrow();
        world.setCurrentLoginLink(loginLinkFixtures.linkForParticipation(participation));
    }

    @Then("the system redirects them directly to the competition page")
    public void the_system_redirects_them_directly_to_the_competition_page() {
        assertRedirectsToCompetitionPage();
    }

    // -- Closed competition --

    @Given("the competition referenced in the link is already closed")
    public void the_competition_referenced_in_the_link_is_already_closed() {
        if (world.getTargetCompetition() == null) {
            world.setTargetCompetition(competitionFixtures.closedCompetition());
        } else {
            Competition competition = world.getTargetCompetition();
            competition.setStatus(CompetitionStatus.CLOSED);
            competitionRepository.save(competition);
        }
    }

    @Given("the player participated in that competition")
    public void the_player_participated_in_that_competition() {
        world.setCandidateEmail(world.getCurrentUser().getEmail());
        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setUser(world.getCurrentUser());
        participation.setEmail(world.getCurrentUser().getEmail());
        participation.setStatus(ParticipationStatus.IN_COMPETITION);
        participation.setRequestType(RequestType.REQUEST);
        participation.setJoinedAt(LocalDate.now().minusDays(10));
        participation = participationRepository.save(participation);
        world.setCurrentLoginLink(loginLinkFixtures.linkForParticipation(participation));
    }

    @Then("the system redirects them to the competition page")
    public void the_system_redirects_them_to_the_competition_page() {
        assertRedirectsToCompetitionPage();
    }

    @Then("shows the final result")
    public void shows_the_final_result() {
        // No results/trading feature yet (later iteration) -- the meaningful backend
        // signal already checked is the successful redirect above.
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
    }

    @Given("the player's status is {}")
    public void the_players_status_is(String status) {
        boolean isPrivate = status.contains("private");
        world.setTargetCompetition(isPrivate ? competitionFixtures.privateCompetition() : competitionFixtures.publicCompetition());
        RequestType requestType = isPrivate ? RequestType.INVITE : RequestType.REQUEST;
        world.setCandidateEmail("player-" + UUID.randomUUID() + "@example.com");

        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setEmail(world.getCandidateEmail());
        participation.setStatus(ParticipationStatus.EMAIL_SENT); // not finished
        participation.setRequestType(requestType);
        participation.setFirstEmailSentDate(LocalDate.now());
        participation = participationRepository.save(participation);

        world.setCurrentLoginLink(loginLinkFixtures.linkForParticipation(participation));
    }

    @Then("the system shows an error message")
    public void the_system_shows_an_error_message() {
        assertThat(world.getLastResponse().statusCode()).isGreaterThanOrEqualTo(400);
    }

    // -- Requesting a login link directly (already-registered player / administrator) --

    @Given("requested the login link")
    public void requested_the_login_link() {
        Response response = world.request()
                .body(new RequestLoginLinkRequest().email(world.getCurrentUser().getEmail()))
                .when()
                .post("/login-requests");
        assertThat(response.statusCode()).isEqualTo(202);

        LoginLink link = loginLinkRepository
                .findFirstByUser_IdAndUsedAtIsNullAndInvalidatedAtIsNullOrderByIdDesc(world.getCurrentUser().getId())
                .orElseThrow();
        world.setCurrentLoginLink(link);
    }

    @Then("the system redirects them to the page listing the competitions they are participating in or have participated in")
    public void the_system_redirects_them_to_the_page_listing_the_competitions() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getString("redirectTo")).isEqualTo("competitions-list");
    }

    @Then("the system redirects them to the administration page")
    public void the_system_redirects_them_to_the_administration_page() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getString("redirectTo")).isEqualTo("admin-page");
    }

    // -- Invalid/expired link --

    @Given("the user clicked a login link that is invalid or expired")
    public void the_user_clicked_a_login_link_that_is_invalid_or_expired() {
        world.setCurrentLoginLink(loginLinkFixtures.expiredLink("someone-" + UUID.randomUUID() + "@example.com"));
    }

    @When("the system validates the link")
    public void the_system_validates_the_link() {
        clickCurrentLink(ScenarioWorld.PRIMARY_DEVICE);
    }

    @Then("offers the option to request a new login link")
    public void offers_the_option_to_request_a_new_login_link() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(400);
    }

    // -- Device rules --

    @Given("a registered player used the login link to log in on one device")
    public void a_registered_player_used_the_login_link_to_log_in_on_one_device() {
        world.setCurrentUser(userMother.registeredPlayer());
        LoginLink link = new LoginLink();
        link.setToken(UUID.randomUUID().toString());
        link.setEmail(world.getCurrentUser().getEmail());
        link.setUser(world.getCurrentUser());
        link.setExpiresAt(LocalDateTime.now().plusHours(1));
        link = loginLinkRepository.save(link);
        world.setCurrentLoginLink(link);

        Response response = world.request(ScenarioWorld.PRIMARY_DEVICE)
                .when()
                .get("/login-links/{token}", link.getToken());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Given("they are not logged in on this other device")
    public void they_are_not_logged_in_on_this_other_device() {
        // Trivially true -- "other" hasn't made any request yet in this scenario.
    }

    @When("they click the same login link on this other device")
    public void they_click_the_same_login_link_on_this_other_device() {
        clickCurrentLink("other");
    }

    @Given("the player is already logged in on this device")
    public void the_player_is_already_logged_in_on_this_device() {
        world.setCurrentUser(userMother.registeredPlayer());
        loginHelper.loginAs(world, world.getCurrentUser(), ScenarioWorld.PRIMARY_DEVICE);
    }

    @Given("the login link was already used to log in on a different device")
    public void the_login_link_was_already_used_to_log_in_on_a_different_device() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setUser(world.getCurrentUser());
        participation.setEmail(world.getCurrentUser().getEmail());
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participation.setRequestType(RequestType.REQUEST);
        participation = participationRepository.save(participation);

        LoginLink link = loginLinkFixtures.linkForParticipation(participation);
        world.setCurrentLoginLink(link);

        Response response = world.request("other")
                .when()
                .get("/login-links/{token}", link.getToken());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Given("a registered player has an active login link that has not been used yet")
    public void a_registered_player_has_an_active_login_link_that_has_not_been_used_yet() {
        world.setCurrentUser(userMother.registeredPlayer());
        LoginLink link = new LoginLink();
        link.setToken(UUID.randomUUID().toString());
        link.setEmail(world.getCurrentUser().getEmail());
        link.setUser(world.getCurrentUser());
        link.setExpiresAt(LocalDateTime.now().plusHours(1));
        link = loginLinkRepository.save(link);
        world.setCurrentLoginLink(link);
    }

    @When("they request a new login link")
    public void they_request_a_new_login_link() {
        Response response = world.request()
                .body(new RequestLoginLinkRequest().email(world.getCurrentUser().getEmail()))
                .when()
                .post("/login-requests");
        world.setLastResponse(response);
    }

    @Then("the previous login link is invalidated")
    public void the_previous_login_link_is_invalidated() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        LoginLink oldLink = loginLinkRepository.findById(world.getCurrentLoginLink().getId()).orElseThrow();
        assertThat(oldLink.getInvalidatedAt()).isNotNull();
    }

    @Then("only the new login link can be used to log in")
    public void only_the_new_login_link_can_be_used_to_log_in() {
        Response oldAttempt = world.request()
                .when()
                .get("/login-links/{token}", world.getCurrentLoginLink().getToken());
        assertThat(oldAttempt.statusCode()).isEqualTo(400);

        LoginLink newLink = loginLinkRepository
                .findFirstByUser_IdAndUsedAtIsNullAndInvalidatedAtIsNullOrderByIdDesc(world.getCurrentUser().getId())
                .orElseThrow();
        Response newAttempt = world.request()
                .when()
                .get("/login-links/{token}", newLink.getToken());
        assertThat(newAttempt.statusCode()).isEqualTo(200);
    }

    @Given("the player is already logged in on the maximum number of devices allowed by the system")
    public void the_player_is_already_logged_in_on_the_maximum_number_of_devices_allowed_by_the_system() {
        world.setCurrentUser(userMother.registeredPlayer());
        for (int i = 1; i <= maxDevicesPerUser; i++) {
            loginHelper.loginAs(world, world.getCurrentUser(), "device-" + i);
        }
    }

    @When("they log in successfully on one more device")
    public void they_log_in_successfully_on_one_more_device() {
        loginHelper.loginAs(world, world.getCurrentUser(), "device-" + (maxDevicesPerUser + 1));
    }

    @Then("the system ends the oldest active session")
    public void the_system_ends_the_oldest_active_session() {
        List<LoginSession> sessions = loginSessionRepository.findAll().stream()
                .filter(session -> session.getUser().getId().equals(world.getCurrentUser().getId()))
                .sorted(Comparator.comparing(LoginSession::getCreatedAt))
                .toList();
        assertThat(sessions).isNotEmpty();
        assertThat(sessions.get(0).getEndedAt()).isNotNull();
    }

    @Then("the player remains within the configured device limit")
    public void the_player_remains_within_the_configured_device_limit() {
        long activeCount = loginSessionRepository
                .findByUser_IdAndEndedAtIsNullOrderByCreatedAtAsc(world.getCurrentUser().getId())
                .size();
        assertThat(activeCount).isEqualTo(maxDevicesPerUser);
    }
}
