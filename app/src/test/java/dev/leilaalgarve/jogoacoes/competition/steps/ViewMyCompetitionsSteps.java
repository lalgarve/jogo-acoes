package dev.leilaalgarve.jogoacoes.competition.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.competition.CompetitionStatus;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.ScenarioWorld;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-004 (T002): written against the endpoints before they exist (`GET /competitions/mine`,
 * `GET /competitions/{competitionId}` -- not in {@code docs/openapi.yaml} yet), raw JSON, no
 * generated model dependency. Expected to fail until T003 (contract) and the tasks after it
 * land -- see specs/05-004-visao-do-jogador-sobre-competicoes/tasks.md.
 *
 * <p>{@code canConfirmEntry} on the detail response is a field this class needed to assert
 * "offers the option to confirm entry from that screen" that plan.md's schema table didn't
 * spell out explicitly (it only said "CompetitionSummary + fees/recurring + accessLevel") --
 * carry it into the contract in T003.
 */
public class ViewMyCompetitionsSteps {

    private final ScenarioWorld world;
    private final CompetitionFixtures competitionFixtures;
    private final ParticipationRepository participationRepository;

    public ViewMyCompetitionsSteps(ScenarioWorld world, CompetitionFixtures competitionFixtures,
                                    ParticipationRepository participationRepository) {
        this.world = world;
        this.competitionFixtures = competitionFixtures;
        this.participationRepository = participationRepository;
    }

    @Given("the player is registered in an open competition")
    public void the_player_is_registered_in_an_open_competition() {
        Competition competition = competitionFixtures.publicCompetition();
        participate(competition, ParticipationStatus.IN_COMPETITION, RequestType.REQUEST, LocalDate.now());
        world.setTargetCompetition(competition);
    }

    @Given("the player was registered in a competition that has since closed")
    public void the_player_was_registered_in_a_competition_that_has_since_closed() {
        Competition competition = competitionFixtures.closedCompetition();
        participate(competition, ParticipationStatus.IN_COMPETITION, RequestType.REQUEST, LocalDate.now().minusDays(30));
        world.setTargetCompetition(competition);
    }

    @Given("the player was invited to a private competition")
    public void the_player_was_invited_to_a_private_competition() {
        Competition competition = competitionFixtures.custom(CompetitionType.PRIVATE, CompetitionStatus.OPEN);
        participate(competition, ParticipationStatus.EMAIL_SENT, RequestType.INVITE, null);
        world.setTargetCompetition(competition);
    }

    @Given("the player requested entry into a public competition")
    public void the_player_requested_entry_into_a_public_competition() {
        Competition competition = competitionFixtures.publicCompetition();
        participate(competition, ParticipationStatus.EMAIL_SENT, RequestType.REQUEST, null);
        world.setTargetCompetition(competition);
    }

    @Given("has not finished confirming entry yet")
    public void has_not_finished_confirming_entry_yet() {
        // Already true from the previous step -- the participation is EMAIL_SENT, not IN_COMPETITION.
    }

    @Given("the player never participated in or was invited to a competition")
    public void the_player_never_participated_in_or_was_invited_to_a_competition() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
    }

    @Given("is not a participant in a competition")
    public void is_not_a_participant_in_a_competition() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
    }

    @When("they access their competitions list")
    public void they_access_their_competitions_list() {
        world.setLastResponse(world.request().when().get("/competitions/mine"));
    }

    @When("they access that competition's details")
    public void they_access_that_competition_s_details() {
        world.setLastResponse(world.request().when().get("/competitions/{id}", world.getTargetCompetition().getId()));
    }

    @When("they try to access that competition's details")
    public void they_try_to_access_that_competition_s_details() {
        they_access_that_competition_s_details();
    }

    @When("they confirm entry from that competition's details screen")
    public void they_confirm_entry_from_that_competition_s_details_screen() {
        world.setLastResponse(world.request().when().post("/competitions/{id}/entry-requests", world.getTargetCompetition().getId()));
    }

    @Then("^the system shows that competition under \"(participating|participated in the past|invited, not confirmed)\"$")
    public void the_system_shows_that_competition_under(String bucketLabel) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        String expectedBucket = bucketKey(bucketLabel);
        for (String bucket : List.of("participating", "pastParticipations", "pendingConfirmation")) {
            List<Map<String, Object>> items = world.getLastResponse().jsonPath().getList(bucket);
            boolean present = items.stream().anyMatch(item -> world.getTargetCompetition().getId().equals(((Number) item.get("id")).longValue()));
            assertThat(present).as("competition present under '%s'", bucket).isEqualTo(bucket.equals(expectedBucket));
        }
    }

    @Then("the system shows the competition's basic information with read-write access")
    public void the_system_shows_the_competition_s_basic_information_with_read_write_access() {
        assertDetail("READ_WRITE");
    }

    @Then("the system shows the competition's basic information with read-only access")
    public void the_system_shows_the_competition_s_basic_information_with_read_only_access() {
        assertDetail("READ");
    }

    @Then("the competition now shows read-write access")
    public void the_competition_now_shows_read_write_access() {
        they_access_that_competition_s_details();
        assertDetail("READ_WRITE");
    }

    @Then("offers the option to confirm entry from that screen")
    public void offers_the_option_to_confirm_entry_from_that_screen() {
        assertThat(world.getLastResponse().jsonPath().getBoolean("canConfirmEntry")).isTrue();
    }

    @Then("the system shows an error message without revealing whether the competition exists")
    public void the_system_shows_an_error_message_without_revealing_whether_the_competition_exists() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(404);
    }

    private void assertDetail(String expectedAccessLevel) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        Map<String, Object> body = world.getLastResponse().jsonPath().getMap("$");
        assertThat(body.get("id")).isEqualTo(world.getTargetCompetition().getId().intValue());
        assertThat(body.get("name")).isEqualTo(world.getTargetCompetition().getName());
        assertThat(body.get("accessLevel")).isEqualTo(expectedAccessLevel);
    }

    private String bucketKey(String label) {
        return switch (label) {
            case "participating" -> "participating";
            case "participated in the past" -> "pastParticipations";
            case "invited, not confirmed" -> "pendingConfirmation";
            default -> throw new IllegalArgumentException("Unknown bucket label: " + label);
        };
    }

    private void participate(Competition competition, ParticipationStatus status, RequestType requestType, LocalDate joinedAt) {
        Participation participation = new Participation();
        participation.setCompetition(competition);
        participation.setUser(world.getCurrentUser());
        participation.setEmail(world.getCurrentUser().getEmail());
        participation.setStatus(status);
        participation.setRequestType(requestType);
        participation.setJoinedAt(joinedAt);
        participationRepository.save(participation);
    }
}
