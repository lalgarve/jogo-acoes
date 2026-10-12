package dev.leilaalgarve.jogoacoes.competition.steps;

import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.ScenarioWorld;
import dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.competition.CompetitionStatus;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.user.User;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-038 (T001): written against `GET /players/{userId}` and `GET /players?email=` before
 * they exist in docs/openapi.yaml, raw JSON (no generated model), so this is expected to fail
 * until the tasks after T001 land.
 */
public class ViewPlayerProfileSteps {

    private static final long NONEXISTENT_USER_ID = 999_999_999L;

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final CompetitionFixtures competitionFixtures;
    private final ParticipationRepository participationRepository;

    private User otherPlayer;

    public ViewPlayerProfileSteps(ScenarioWorld world, UserMother userMother, CompetitionFixtures competitionFixtures,
                                   ParticipationRepository participationRepository) {
        this.world = world;
        this.userMother = userMother;
        this.competitionFixtures = competitionFixtures;
        this.participationRepository = participationRepository;
    }

    @Given("they joined {int} public competitions and {int} private competition")
    public void they_joined_public_and_private_competitions(int publicCount, int privateCount) {
        joinCompetitions(world.getCurrentUser(), CompetitionType.PUBLIC, publicCount);
        joinCompetitions(world.getCurrentUser(), CompetitionType.PRIVATE, privateCount);
    }

    @Given("they joined {int} private competition")
    public void they_joined_private_competitions(int privateCount) {
        joinCompetitions(world.getCurrentUser(), CompetitionType.PRIVATE, privateCount);
    }

    @Given("they were invited to another private competition and did not confirm entry")
    public void they_were_invited_to_another_private_competition_and_did_not_confirm_entry() {
        participate(world.getCurrentUser(), CompetitionType.PRIVATE, ParticipationStatus.EMAIL_SENT, RequestType.INVITE, null);
    }

    @Given("another registered player exists")
    public void another_registered_player_exists() {
        otherPlayer = userMother.registeredPlayer();
    }

    @Given("a registered player joined {int} public competition")
    public void a_registered_player_joined_public_competitions(int publicCount) {
        otherPlayer = userMother.registeredPlayer();
        joinCompetitions(otherPlayer, CompetitionType.PUBLIC, publicCount);
    }

    @Given("they created {int} competitions")
    public void they_created_competitions(int count) {
        for (int i = 0; i < count; i++) {
            competitionFixtures.createdBy(world.getCurrentUser(), CompetitionType.PUBLIC, CompetitionStatus.OPEN);
        }
    }

    @When("they view their own profile")
    public void they_view_their_own_profile() {
        world.setLastResponse(getPlayer(world.getCurrentUser().getId()));
    }

    @When("they try to view that other player's profile")
    @When("they view that player's profile")
    public void they_view_that_player_s_profile() {
        world.setLastResponse(getPlayer(otherPlayer.getId()));
    }

    @When("they try to view the profile of a player that does not exist")
    public void they_try_to_view_the_profile_of_a_player_that_does_not_exist() {
        world.setLastResponse(getPlayer(NONEXISTENT_USER_ID));
    }

    @When("they try to view a player's profile")
    public void they_try_to_view_a_player_s_profile() {
        world.setLastResponse(getPlayer(userMother.registeredPlayer().getId()));
    }

    @When("they try to look up that other player by e-mail")
    public void they_try_to_look_up_that_other_player_by_e_mail() {
        world.setLastResponse(findByEmail(otherPlayer.getEmail()));
    }

    @When("they look up that player by e-mail written in upper case")
    public void they_look_up_that_player_by_e_mail_written_in_upper_case() {
        world.setLastResponse(findByEmail(otherPlayer.getEmail().toUpperCase(Locale.ROOT)));
    }

    @When("they look up an e-mail that belongs to no player")
    public void they_look_up_an_e_mail_that_belongs_to_no_player() {
        world.setLastResponse(findByEmail(TestEmails.unique("nobody")));
    }

    @When("they look up the e-mail {string}")
    public void they_look_up_the_e_mail(String email) {
        world.setLastResponse(findByEmail(email));
    }

    @When("they look up a player without giving an e-mail")
    public void they_look_up_a_player_without_giving_an_e_mail() {
        world.setLastResponse(world.request().when().get("/players"));
    }

    @Then("the system shows their name and e-mail")
    public void the_system_shows_their_name_and_e_mail() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        User user = world.getCurrentUser();
        assertThat(world.getLastResponse().jsonPath().getLong("userId")).isEqualTo(user.getId());
        assertThat(world.getLastResponse().jsonPath().getString("name")).isEqualTo(user.getName());
        assertThat(world.getLastResponse().jsonPath().getString("email")).isEqualTo(user.getEmail());
    }

    @Then("shows {int} owned, {int} public and {int} private competitions")
    @Then("the system shows {int} owned, {int} public and {int} private competitions")
    public void shows_counts(int owned, int publicCount, int privateCount) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getInt("ownedCount")).isEqualTo(owned);
        assertThat(world.getLastResponse().jsonPath().getInt("publicCount")).isEqualTo(publicCount);
        assertThat(world.getLastResponse().jsonPath().getInt("privateCount")).isEqualTo(privateCount);
    }

    @Then("the system shows {int} owned competitions")
    public void the_system_shows_owned_competitions(int owned) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getInt("ownedCount")).isEqualTo(owned);
    }

    @Then("the system shows that player's summary")
    public void the_system_shows_that_player_s_summary() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getLong("userId")).isEqualTo(otherPlayer.getId());
        assertThat(world.getLastResponse().jsonPath().getString("email")).isEqualTo(otherPlayer.getEmail());
        assertThat(world.getLastResponse().jsonPath().getInt("publicCount")).isEqualTo(1);
    }

    @Then("the system shows an error message without revealing whether the player exists")
    @Then("the system reports that the player was not found")
    public void the_system_reports_that_the_player_was_not_found() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(404);
    }

    private Response getPlayer(Long userId) {
        return world.request().when().get("/players/{userId}", userId);
    }

    private Response findByEmail(String email) {
        return world.request().queryParam("email", email).when().get("/players");
    }

    private void joinCompetitions(User user, CompetitionType type, int count) {
        for (int i = 0; i < count; i++) {
            participate(user, type, ParticipationStatus.IN_COMPETITION, RequestType.REQUEST, LocalDate.now());
        }
    }

    private void participate(User user, CompetitionType type, ParticipationStatus status, RequestType requestType,
                             LocalDate joinedAt) {
        CompetitionStatus competitionStatus = type == CompetitionType.PUBLIC ? CompetitionStatus.OPEN : CompetitionStatus.AWAITING_INVITES;
        Participation participation = new Participation();
        participation.setCompetition(competitionFixtures.custom(type, competitionStatus));
        participation.setUser(user);
        participation.setEmail(user.getEmail());
        participation.setStatus(status);
        participation.setRequestType(requestType);
        participation.setJoinedAt(joinedAt);
        participationRepository.save(participation);
    }
}
