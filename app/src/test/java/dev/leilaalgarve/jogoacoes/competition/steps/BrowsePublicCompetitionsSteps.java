package dev.leilaalgarve.jogoacoes.competition.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.ScenarioWorld;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-004 (T001): written against the endpoint before it exists (`GET /competitions/public`
 * -- not in {@code docs/openapi.yaml} yet), asserting on raw JSON so nothing here depends on a
 * generated model class. Expected to fail (404) until T003 (contract) and the tasks after it
 * land -- see specs/05-004-visao-do-jogador-sobre-competicoes/tasks.md.
 */
public class BrowsePublicCompetitionsSteps {

    private final ScenarioWorld world;
    private final CompetitionFixtures competitionFixtures;

    public BrowsePublicCompetitionsSteps(ScenarioWorld world, CompetitionFixtures competitionFixtures) {
        this.world = world;
        this.competitionFixtures = competitionFixtures;
    }

    @Given("there are public competitions open for entry")
    public void there_are_public_competitions_open_for_entry() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
    }

    @Given("a public competition has already closed")
    public void a_public_competition_has_already_closed() {
        world.setTargetCompetition(competitionFixtures.closedCompetition());
    }

    @Given("a private competition exists")
    public void a_private_competition_exists() {
        world.setTargetCompetition(competitionFixtures.privateCompetition());
    }

    @When("they access the list of public competitions")
    public void they_access_the_list_of_public_competitions() {
        Response response = world.request().when().get("/competitions/public");
        world.setLastResponse(response);
    }

    @Then("the system shows each competition's basic information")
    public void the_system_shows_each_competition_s_basic_information() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        Map<String, Object> item = findById(world.getTargetCompetition().getId());
        assertThat(item).isNotNull();
        assertThat(item.get("name")).isEqualTo(world.getTargetCompetition().getName());
        assertThat(item.get("type")).isEqualTo(world.getTargetCompetition().getType().name());
        assertThat(item.get("status")).isEqualTo(world.getTargetCompetition().getStatus().name());
        assertThat(item.get("startDate")).isEqualTo(world.getTargetCompetition().getStartDate().toString());
        assertThat(item.get("durationDays")).isEqualTo(world.getTargetCompetition().getDurationDays());
    }

    @Then("that competition is not shown")
    public void that_competition_is_not_shown() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(findById(world.getTargetCompetition().getId())).isNull();
    }

    private Map<String, Object> findById(Long competitionId) {
        List<Map<String, Object>> items = world.getLastResponse().jsonPath().getList("$");
        return items.stream()
                .filter(item -> competitionId.equals(((Number) item.get("id")).longValue()))
                .findFirst()
                .orElse(null);
    }
}
