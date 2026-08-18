package io.deployo.jogoacoes.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.deployo.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import io.deployo.jogoacoes.domain.Competition;
import io.deployo.jogoacoes.domain.CompetitionStatus;
import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.repository.CompetitionRepository;
import io.deployo.jogoacoes.repository.SentEmailRepository;
import io.deployo.jogoacoes.testsupport.CompetitionMother;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateCompetitionSteps {

    private final ScenarioWorld world;
    private final CompetitionRepository competitionRepository;
    private final SentEmailRepository sentEmailRepository;

    public CreateCompetitionSteps(ScenarioWorld world, CompetitionRepository competitionRepository, SentEmailRepository sentEmailRepository) {
        this.world = world;
        this.competitionRepository = competitionRepository;
        this.sentEmailRepository = sentEmailRepository;
    }

    @Given("is on the competition creation screen")
    public void is_on_the_competition_creation_screen() {
        // API-only in this iteration -- no separate "screen" endpoint to navigate to.
    }

    @Given("they choose the public competition option")
    public void they_choose_the_public_competition_option() {
        world.setCompetitionRequest(CompetitionMother.validPublicCompetition());
    }

    @Given("they choose the private competition option")
    public void they_choose_the_private_competition_option() {
        world.setCompetitionRequest(CompetitionMother.validPrivateCompetition());
    }

    @Given("leave the competition name empty")
    public void leave_the_competition_name_empty() {
        world.getCompetitionRequest().name("");
    }

    @Given("leave the e-mail list empty")
    public void leave_the_e_mail_list_empty() {
        world.getCompetitionRequest().emails(List.of());
    }

    @Given("enter a list of e-mails containing an invalid e-mail address")
    public void enter_a_list_of_e_mails_containing_an_invalid_e_mail_address() {
        world.getCompetitionRequest().emails(List.of("valid@example.com", "not-an-email"));
    }

    @Given("define the start date as {}")
    public void define_the_start_date_as(String startDate) {
        world.getCompetitionRequest().startDate(parseDate(startDate));
    }

    @Given("define the duration as {}")
    public void define_the_duration_as(String duration) {
        world.getCompetitionRequest().durationDays(parseLeadingInt(duration));
    }

    @Given("define the buy brokerage fee as {}")
    public void define_the_buy_brokerage_fee_as(String buyFee) {
        world.getCompetitionRequest().buyFee(parsePercentage(buyFee));
    }

    @Given("define the sell brokerage fee as {}")
    public void define_the_sell_brokerage_fee_as(String sellFee) {
        world.getCompetitionRequest().sellFee(parsePercentage(sellFee));
    }

    @When("they click the \"create\" button")
    public void they_click_the_create_button() {
        submitCompetitionCreation();
    }

    @When("click the \"create\" button")
    public void click_the_create_button() {
        submitCompetitionCreation();
    }

    private void submitCompetitionCreation() {
        Response response = world.request()
                .body(world.getCompetitionRequest())
                .when()
                .post("/competitions");
        world.setLastResponse(response);
        if (response.statusCode() == 201) {
            world.setCompetitionId(response.jsonPath().getLong("id"));
        }
    }

    @Then("the system should create a new public competition")
    public void the_system_should_create_a_new_public_competition() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
        assertThat(world.getLastResponse().jsonPath().getString("type")).isEqualTo("PUBLIC");
        assertThat(world.getLastResponse().jsonPath().getString("status")).isEqualTo("OPEN");
    }

    @Then("show the creation success screen")
    public void show_the_creation_success_screen() {
        assertThat(world.getLastResponse().jsonPath().getLong("id")).isPositive();
    }

    @Given("the system creates the new private competition and shows the success screen asking whether to send the invite e-mails now or not")
    public void the_system_creates_the_new_private_competition_and_shows_the_success_screen() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(201);
        assertThat(world.getLastResponse().jsonPath().getString("type")).isEqualTo("PRIVATE");
        assertThat(world.getLastResponse().jsonPath().getString("status")).isEqualTo("AWAITING_INVITES");
    }

    @When("the administrator decides to send the invite e-mails {word}")
    public void the_administrator_decides_to_send_the_invite_e_mails(String timing) {
        DecideInviteEmailTimingRequest body = new DecideInviteEmailTimingRequest()
                .timing("now".equals(timing) ? DecideInviteEmailTimingRequest.TimingEnum.NOW : DecideInviteEmailTimingRequest.TimingEnum.LATER);
        Response response = world.request()
                .body(body)
                .when()
                .post("/competitions/{id}/invite-emails", world.getCompetitionId());
        world.setLastResponse(response);
        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Then("the system sends the invite e-mails to the provided list")
    public void the_system_sends_the_invite_e_mails_to_the_provided_list() {
        List<String> invitedEmails = world.getCompetitionRequest().getEmails();
        long sentCount = sentEmailRepository.findAll().stream()
                .filter(sentEmail -> sentEmail.getTemplate() == EmailTemplate.INVITE)
                .filter(sentEmail -> invitedEmails.contains(sentEmail.getEmail()))
                .count();
        assertThat(sentCount).isEqualTo(invitedEmails.size());
        assertThat(currentCompetition().getStatus()).isEqualTo(CompetitionStatus.OPEN);
    }

    @Then("the system does not send the invite e-mails and keeps the competition awaiting sending")
    public void the_system_does_not_send_the_invite_e_mails_and_keeps_the_competition_awaiting_sending() {
        List<String> invitedEmails = world.getCompetitionRequest().getEmails();
        boolean anySent = sentEmailRepository.findAll().stream()
                .anyMatch(sentEmail -> invitedEmails.contains(sentEmail.getEmail()));
        assertThat(anySent).isFalse();
        assertThat(currentCompetition().getStatus()).isEqualTo(CompetitionStatus.AWAITING_INVITES);
    }

    @Then("the system rejects the competition creation and shows an error message about the {}")
    public void the_system_rejects_the_competition_creation_and_shows_an_error_message_about_the(String error) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(400);
        String message = world.getLastResponse().jsonPath().getString("message");
        assertThat(message.toLowerCase()).contains(error.toLowerCase());
    }

    @Given("is not the system administrator")
    public void is_not_the_system_administrator() {
        assertThat(world.getCurrentUser()).isNotNull();
    }

    @When("they try to access the competition creation screen")
    public void they_try_to_access_the_competition_creation_screen() {
        world.setCompetitionRequest(CompetitionMother.validPublicCompetition());
        submitCompetitionCreation();
    }

    @Then("the system denies access and shows an error message")
    public void the_system_denies_access_and_shows_an_error_message() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(403);
    }

    private Competition currentCompetition() {
        return competitionRepository.findById(world.getCompetitionId()).orElseThrow();
    }

    private static LocalDate parseDate(String phrase) {
        return switch (phrase) {
            case "a date in the past" -> LocalDate.now().minusDays(1);
            case "a valid future date" -> LocalDate.now().plusDays(7);
            default -> throw new IllegalArgumentException("Unknown date phrase: " + phrase);
        };
    }

    private static int parseLeadingInt(String phrase) {
        return Integer.parseInt(phrase.replaceAll("[^0-9-]", ""));
    }

    private static double parsePercentage(String phrase) {
        return Double.parseDouble(phrase.replace("%", ""));
    }
}
