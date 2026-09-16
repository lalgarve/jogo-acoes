package dev.leilaalgarve.jogoacoes.link;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.ScenarioWorld;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-005 (T001): written against a `returnTo` field and a `redirectTo` path that don't
 * exist in the contract yet -- raw JSON body (`Map`, not the generated `RequestLoginLinkRequest`,
 * which has no `returnTo` setter), asserting `redirectTo` as a plain string rather than the
 * current closed enum. Expected to fail until T002 (contract) and the tasks after it land --
 * see specs/05-005-redirecionamento-pos-login/tasks.md.
 */
public class RedirectAfterLoginSteps {

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final CompetitionFixtures competitionFixtures;
    private final ParticipationRepository participationRepository;
    private final LinkRecordRepository linkRecordRepository;

    public RedirectAfterLoginSteps(ScenarioWorld world, UserMother userMother, CompetitionFixtures competitionFixtures,
                                    ParticipationRepository participationRepository, LinkRecordRepository linkRecordRepository) {
        this.world = world;
        this.userMother = userMother;
        this.competitionFixtures = competitionFixtures;
        this.participationRepository = participationRepository;
        this.linkRecordRepository = linkRecordRepository;
    }

    @When("they try to access a specific competition's details")
    public void they_try_to_access_a_specific_competition_s_details() {
        Competition competition = competitionFixtures.publicCompetition();
        participate(competition);
        world.setTargetCompetition(competition);
        requestLoginLink("/competitions/" + competition.getId());
    }

    @When("they try to access the list of their own competitions")
    public void they_try_to_access_the_list_of_their_own_competitions() {
        requestLoginLink("/competitions/mine");
    }

    @Given("the player requested a login link while trying to reach one competition's details")
    public void the_player_requested_a_login_link_while_trying_to_reach_one_competition_s_details() {
        world.setCurrentUser(userMother.registeredPlayer());
        Competition competition = competitionFixtures.publicCompetition();
        participate(competition);
        world.setTargetCompetition(competition);
        requestLoginLink("/competitions/" + competition.getId());
    }

    @Given("then requested another login link while trying to reach their competitions list")
    public void then_requested_another_login_link_while_trying_to_reach_their_competitions_list() {
        requestLoginLink("/competitions/mine");
    }

    @When("they click the newer link")
    public void they_click_the_newer_link() {
        clickCurrentLink();
    }

    @Then("the system sends them a login link by e-mail")
    public void the_system_sends_them_a_login_link_by_e_mail() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
    }

    @Then("clicking that link logs them in and takes them straight to that competition's details")
    public void clicking_that_link_logs_them_in_and_takes_them_straight_to_that_competition_s_details() {
        clickCurrentLink();
        assertRedirectsTo("/competitions/" + world.getTargetCompetition().getId());
    }

    @Then("clicking that link logs them in and takes them straight to their competitions list")
    public void clicking_that_link_logs_them_in_and_takes_them_straight_to_their_competitions_list() {
        clickCurrentLink();
        assertRedirectsTo("/competitions/mine");
    }

    @Then("it logs them in and takes them straight to their competitions list, not the earlier destination")
    public void it_logs_them_in_and_takes_them_straight_to_their_competitions_list_not_the_earlier_destination() {
        assertRedirectsTo("/competitions/mine");
    }

    private void requestLoginLink(String returnTo) {
        Response response = world.request()
                .body(Map.of("email", world.getCurrentUser().getEmail(), "returnTo", returnTo))
                .when()
                .post("/login-requests");
        world.setLastResponse(response);
        linkRecordRepository
                .findFirstByUserIdAndUsedAtIsNullAndInvalidatedAtIsNullOrderByIdDesc(world.getCurrentUser().getId())
                .ifPresent(world::setCurrentLoginLink);
    }

    private void clickCurrentLink() {
        Response response = world.request()
                .when()
                .get("/login-links/{token}", world.getCurrentLoginLink().getToken());
        world.setLastResponse(response);
    }

    private void assertRedirectsTo(String expectedPath) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getString("redirectTo")).isEqualTo(expectedPath);
    }

    private void participate(Competition competition) {
        Participation participation = new Participation();
        participation.setCompetition(competition);
        participation.setUser(world.getCurrentUser());
        participation.setEmail(world.getCurrentUser().getEmail());
        participation.setStatus(ParticipationStatus.IN_COMPETITION);
        participation.setRequestType(RequestType.REQUEST);
        participation.setJoinedAt(LocalDate.now());
        participationRepository.save(participation);
    }
}
