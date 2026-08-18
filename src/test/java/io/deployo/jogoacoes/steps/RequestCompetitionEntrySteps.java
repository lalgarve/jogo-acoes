package io.deployo.jogoacoes.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.deployo.jogoacoes.api.model.EntryRequest;
import io.deployo.jogoacoes.captcha.CaptchaService;
import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.repository.SentEmailRepository;
import io.deployo.jogoacoes.testsupport.CompetitionFixtures;
import io.deployo.jogoacoes.testsupport.LoginHelper;
import io.deployo.jogoacoes.testsupport.UserMother;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.altcha.altcha.v2.Altcha;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestCompetitionEntrySteps {

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final LoginHelper loginHelper;
    private final CompetitionFixtures competitionFixtures;
    private final CaptchaService captchaService;
    private final ParticipationRepository participationRepository;
    private final SentEmailRepository sentEmailRepository;

    public RequestCompetitionEntrySteps(ScenarioWorld world, UserMother userMother, LoginHelper loginHelper,
                                         CompetitionFixtures competitionFixtures, CaptchaService captchaService,
                                         ParticipationRepository participationRepository, SentEmailRepository sentEmailRepository) {
        this.world = world;
        this.userMother = userMother;
        this.loginHelper = loginHelper;
        this.competitionFixtures = competitionFixtures;
        this.captchaService = captchaService;
        this.participationRepository = participationRepository;
        this.sentEmailRepository = sentEmailRepository;
    }

    @Given("^the player is (unregistered|registered and not logged in|registered and logged in)$")
    public void the_player_is(String status) {
        switch (status) {
            case "unregistered" -> world.setCurrentUser(null);
            case "registered and not logged in" -> world.setCurrentUser(userMother.registeredPlayer());
            case "registered and logged in" -> {
                var user = userMother.registeredPlayer();
                world.setCurrentUser(user);
                loginHelper.loginAs(world, user);
            }
            default -> throw new IllegalArgumentException("Unknown player status: " + status);
        }
    }

    @Given("clicked on Join a Competition")
    public void clicked_on_join_a_competition() {
        // API-only in this iteration -- no separate "screen" to navigate to.
    }

    @Given("clicked on a public competition")
    public void clicked_on_a_public_competition() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
    }

    @Given("entered a valid e-mail")
    public void entered_a_valid_e_mail() {
        String email = world.getCurrentUser() != null
                ? world.getCurrentUser().getEmail()
                : "player-" + UUID.randomUUID() + "@example.com";
        world.setCandidateEmail(email);
    }

    @Given("passed the not-a-robot test")
    public void passed_the_not_a_robot_test() {
        submitEntryRequest(new EntryRequest().email(world.getCandidateEmail()).captchaToken(validCaptchaToken()));
    }

    @Then("the system adds the e-mail to the list of requesters and sends the link to finish registration by e-mail")
    public void the_system_adds_the_e_mail_to_the_list_of_requesters() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        assertPendingParticipationExists();
        assertEmailSent(EmailTemplate.REGISTRATION_LINK);
    }

    @Given("confirmed entry into the competition")
    public void confirmed_entry_into_the_competition() {
        submitEntryRequest(null);
    }

    @Then("the system adds the e-mail personalized with the name to the list of requesters and sends the link to finish registration by e-mail")
    public void the_system_adds_the_e_mail_personalized_with_the_name_to_the_list_of_requesters() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        assertPendingParticipationExists();
        assertEmailSent(EmailTemplate.LOGIN_LINK);
    }

    @Then("the system adds the player to the public competition and redirects them to the competition page")
    public void the_system_adds_the_player_to_the_public_competition_and_redirects_them() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        assertThat(world.getLastResponse().jsonPath().getString("status")).isEqualTo("IN_COMPETITION");
        assertThat(world.getLastResponse().jsonPath().getLong("competitionId")).isEqualTo(world.getTargetCompetition().getId());
    }

    @When("they try to access a private competition they were not invited to")
    public void they_try_to_access_a_private_competition_they_were_not_invited_to() {
        world.setTargetCompetition(competitionFixtures.privateCompetition());
        submitEntryRequest(null);
    }

    @Then("the system does not allow the request and does not show the private competition in the list")
    public void the_system_does_not_allow_the_request_and_does_not_show_the_private_competition() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(404);
    }

    @When("they enter an invalid e-mail address")
    public void they_enter_an_invalid_e_mail_address() {
        world.setCandidateEmail("not-an-email");
        submitEntryRequest(new EntryRequest().email(world.getCandidateEmail()).captchaToken(validCaptchaToken()));
    }

    @Then("the system shows an error message and does not send the registration link")
    public void the_system_shows_an_error_message_and_does_not_send_the_registration_link() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(400);
        boolean sent = sentEmailRepository.findAll().stream()
                .anyMatch(sentEmail -> sentEmail.getEmail().equals(world.getCandidateEmail()));
        assertThat(sent).isFalse();
    }

    @When("they fail the not-a-robot test")
    public void they_fail_the_not_a_robot_test() {
        submitEntryRequest(new EntryRequest().email(world.getCandidateEmail()).captchaToken(invalidCaptchaToken()));
    }

    @Given("the player already requested entry into a public competition")
    public void the_player_already_requested_entry_into_a_public_competition() {
        world.setCurrentUser(null);
        world.setTargetCompetition(competitionFixtures.publicCompetition());
        world.setCandidateEmail("player-" + UUID.randomUUID() + "@example.com");
        submitEntryRequest(new EntryRequest().email(world.getCandidateEmail()).captchaToken(validCaptchaToken()));
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
    }

    @Given("has not finished registration yet")
    public void has_not_finished_registration_yet() {
        // Already true from the previous step -- the participation is EMAIL_SENT, not IN_COMPETITION.
    }

    @When("they request entry into that same competition again")
    public void they_request_entry_into_that_same_competition_again() {
        submitEntryRequest(new EntryRequest().email(world.getCandidateEmail()).captchaToken(validCaptchaToken()));
    }

    @Then("the system resends the link to finish registration by e-mail")
    public void the_system_resends_the_link_to_finish_registration_by_e_mail() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);

        long participationCount = participationRepository.findAll().stream()
                .filter(p -> p.getCompetition().getId().equals(world.getTargetCompetition().getId()))
                .filter(p -> p.getEmail().equals(world.getCandidateEmail()))
                .count();
        assertThat(participationCount).isEqualTo(1);

        List<io.deployo.jogoacoes.domain.SentEmail> sentForEmail = sentEmailRepository.findAll().stream()
                .filter(sentEmail -> sentEmail.getEmail().equals(world.getCandidateEmail()))
                .toList();
        assertThat(sentForEmail).hasSize(2);
    }

    private void submitEntryRequest(EntryRequest body) {
        RequestSpecification spec = world.request();
        if (body != null) {
            spec = spec.body(body);
        }
        Response response = spec.when().post("/competitions/{id}/entry-requests", world.getTargetCompetition().getId());
        world.setLastResponse(response);
    }

    private void assertPendingParticipationExists() {
        Participation participation = participationRepository
                .findByCompetition_IdAndEmailAndStatusNot(world.getTargetCompetition().getId(), world.getCandidateEmail(), ParticipationStatus.IN_COMPETITION)
                .orElseThrow();
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.EMAIL_SENT);
    }

    private void assertEmailSent(EmailTemplate expectedTemplate) {
        boolean sent = sentEmailRepository.findAll().stream()
                .anyMatch(sentEmail -> sentEmail.getEmail().equals(world.getCandidateEmail()) && sentEmail.getTemplate() == expectedTemplate);
        assertThat(sent).isTrue();
    }

    private String validCaptchaToken() {
        try {
            Altcha.Challenge challenge = captchaService.createChallenge();
            Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf(challenge.parameters().algorithm()));
            return captchaService.encodeToken(challenge, solution);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to solve captcha challenge in test", e);
        }
    }

    private String invalidCaptchaToken() {
        Altcha.Challenge challenge = captchaService.createChallenge();
        Altcha.Solution wrongSolution = new Altcha.Solution(-1, "not-the-right-key", 0L);
        return captchaService.encodeToken(challenge, wrongSolution);
    }
}
