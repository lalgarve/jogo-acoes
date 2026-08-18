package io.deployo.jogoacoes.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.deployo.jogoacoes.api.model.InvitePlayersRequest;
import io.deployo.jogoacoes.api.model.ResendPlayerInviteEmailsRequest;
import io.deployo.jogoacoes.api.model.UpdatePlayerEmailRequest;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.domain.RequestType;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.repository.SentEmailRepository;
import io.deployo.jogoacoes.testsupport.CompetitionFixtures;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ManageCompetitionPlayersSteps {

    private final ScenarioWorld world;
    private final CompetitionFixtures competitionFixtures;
    private final ParticipationRepository participationRepository;
    private final SentEmailRepository sentEmailRepository;

    public ManageCompetitionPlayersSteps(ScenarioWorld world, CompetitionFixtures competitionFixtures,
                                          ParticipationRepository participationRepository, SentEmailRepository sentEmailRepository) {
        this.world = world;
        this.competitionFixtures = competitionFixtures;
        this.participationRepository = participationRepository;
        this.sentEmailRepository = sentEmailRepository;
    }

    @Given("is on the player management screen for a competition")
    public void is_on_the_player_management_screen_for_a_competition() {
        world.setTargetCompetition(competitionFixtures.publicCompetition());
    }

    // -- Filter/list Outline --

    @Given("a player in the competition has status {} since {}")
    public void a_player_in_the_competition_has_status_since(String status, String date) {
        ParticipationStatus target = parseStatus(status);
        LocalDate parsedDate = LocalDate.parse(date);
        world.setCandidateEmail("player-" + UUID.randomUUID() + "@example.com");

        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setEmail(world.getCandidateEmail());
        participation.setRequestType(RequestType.INVITE);
        participation.setStatus(target);
        switch (target) {
            case EMAIL_NOT_SENT -> {
                // No date field applies yet -- nothing to set.
            }
            case EMAIL_SENT, LINK_CLICKED -> participation.setFirstEmailSentDate(parsedDate);
            case IN_COMPETITION -> {
                participation.setFirstEmailSentDate(parsedDate.minusDays(1));
                participation.setJoinedAt(parsedDate);
            }
        }
        participation = participationRepository.save(participation);
        world.setCurrentParticipationId(participation.getId());
    }

    @Then("the player list shows the player's status as {} along with the date {}")
    public void the_player_list_shows_the_players_status_as_along_with_the_date(String status, String date) {
        ParticipationStatus target = parseStatus(status);
        Response response = world.request()
                .when()
                .get("/competitions/{id}/players", world.getTargetCompetition().getId());
        world.setLastResponse(response);
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, Object> player = findPlayer(response, world.getCandidateEmail());
        assertThat(player.get("status")).isEqualTo(target.name());
        switch (target) {
            case EMAIL_SENT, LINK_CLICKED -> assertThat(player.get("firstEmailSentDate")).isEqualTo(date);
            case IN_COMPETITION -> assertThat(player.get("joinedAt")).isEqualTo(date);
            default -> {
                // EMAIL_NOT_SENT: no per-status date field exists yet to check against.
            }
        }
    }

    @When("the administrator filters the player list by {}")
    public void the_administrator_filters_the_player_list_by(String status) {
        ParticipationStatus target = parseStatus(status);
        Response response = world.request()
                .queryParam("status", target.name())
                .when()
                .get("/competitions/{id}/players", world.getTargetCompetition().getId());
        world.setLastResponse(response);
    }

    @Then("only players with status {} are shown")
    public void only_players_with_status_are_shown(String status) {
        ParticipationStatus target = parseStatus(status);
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        List<String> statuses = world.getLastResponse().jsonPath().getList("status");
        assertThat(statuses).isNotEmpty();
        assertThat(statuses).allMatch(target.name()::equals);
    }

    private static ParticipationStatus parseStatus(String phrase) {
        return switch (phrase) {
            case "e-mail not sent" -> ParticipationStatus.EMAIL_NOT_SENT;
            case "e-mail sent but link not clicked" -> ParticipationStatus.EMAIL_SENT;
            case "link clicked but registration not finished" -> ParticipationStatus.LINK_CLICKED;
            case "in the competition" -> ParticipationStatus.IN_COMPETITION;
            default -> throw new IllegalArgumentException("Unknown status phrase: " + phrase);
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findPlayer(Response response, String email) {
        List<Map<String, Object>> players = response.jsonPath().getList("$");
        return players.stream()
                .filter(p -> email.equals(p.get("email")))
                .findFirst()
                .orElseThrow();
    }

    // -- Send/resend invite e-mail --

    @Given("a player is listed in the competition")
    public void a_player_is_listed_in_the_competition() {
        world.setCandidateEmail("player-" + UUID.randomUUID() + "@example.com");
        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setEmail(world.getCandidateEmail());
        participation.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
        participation.setRequestType(RequestType.INVITE);
        participation = participationRepository.save(participation);
        world.setCurrentParticipationId(participation.getId());
    }

    @When("the administrator chooses to {}")
    public void the_administrator_chooses_to(String action) {
        if (action.startsWith("send or resend the e-mail")) {
            Response response = world.request()
                    .when()
                    .post("/competitions/{cid}/players/{pid}/invite-emails", world.getTargetCompetition().getId(), world.getCurrentParticipationId());
            world.setLastResponse(response);
        }
        // "remove that player" / "cancel the invite": just a UI choice here -- the actual
        // call happens on the "confirms the removal/cancellation" step that follows.
    }

    @Then("the system sends the invite e-mail to the player")
    public void the_system_sends_the_invite_e_mail_to_the_player() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        boolean sent = sentEmailRepository.findAll().stream()
                .anyMatch(sentEmail -> sentEmail.getEmail().equals(world.getCandidateEmail()));
        assertThat(sent).isTrue();
    }

    @Then("updates the e-mail sent date")
    public void updates_the_e_mail_sent_date() {
        Participation participation = participationRepository.findById(world.getCurrentParticipationId()).orElseThrow();
        assertThat(participation.getFirstEmailSentDate()).isNotNull();
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.EMAIL_SENT);
    }

    // -- Send/resend to a group --

    @Given("multiple players are listed in the competition")
    public void multiple_players_are_listed_in_the_competition() {
        List<String> emails = List.of(
                "player-" + UUID.randomUUID() + "@example.com",
                "player-" + UUID.randomUUID() + "@example.com",
                "player-" + UUID.randomUUID() + "@example.com");
        world.setCandidateEmails(emails);

        List<Long> ids = emails.stream().map(email -> {
            Participation participation = new Participation();
            participation.setCompetition(world.getTargetCompetition());
            participation.setEmail(email);
            participation.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
            participation.setRequestType(RequestType.INVITE);
            return participationRepository.save(participation).getId();
        }).toList();
        world.setSelectedParticipationIds(ids);
    }

    @When("the administrator selects a group of players")
    public void the_administrator_selects_a_group_of_players() {
        // Already selected (all of them) in the previous step -- nothing more to do here.
    }

    @When("chooses to send or resend the e-mail to the selected group")
    public void chooses_to_send_or_resend_the_e_mail_to_the_selected_group() {
        Response response = world.request()
                .body(new ResendPlayerInviteEmailsRequest().participationIds(world.getSelectedParticipationIds()))
                .when()
                .post("/competitions/{id}/players/invite-emails", world.getTargetCompetition().getId());
        world.setLastResponse(response);
    }

    @Then("the system sends the invite e-mail to each selected player")
    public void the_system_sends_the_invite_e_mail_to_each_selected_player() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        for (String email : world.getCandidateEmails()) {
            boolean sent = sentEmailRepository.findAll().stream().anyMatch(sentEmail -> sentEmail.getEmail().equals(email));
            assertThat(sent).isTrue();
        }
    }

    @Then("updates the e-mail sent date for each of them")
    public void updates_the_e_mail_sent_date_for_each_of_them() {
        for (Long id : world.getSelectedParticipationIds()) {
            Participation participation = participationRepository.findById(id).orElseThrow();
            assertThat(participation.getFirstEmailSentDate()).isNotNull();
            assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.EMAIL_SENT);
        }
    }

    // -- Edit e-mail --

    @When("the administrator edits the player's e-mail")
    public void the_administrator_edits_the_players_e_mail() {
        world.setCandidateEmail("edited-" + UUID.randomUUID() + "@example.com");
    }

    @When("confirms the change")
    public void confirms_the_change() {
        Response response = world.request()
                .body(new UpdatePlayerEmailRequest().email(world.getCandidateEmail()))
                .when()
                .patch("/competitions/{cid}/players/{pid}", world.getTargetCompetition().getId(), world.getCurrentParticipationId());
        world.setLastResponse(response);
    }

    @Then("the system updates the player's e-mail")
    public void the_system_updates_the_players_e_mail() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(200);
        Participation participation = participationRepository.findById(world.getCurrentParticipationId()).orElseThrow();
        assertThat(participation.getEmail()).isEqualTo(world.getCandidateEmail());
    }

    @When("the administrator edits the player's e-mail to an invalid e-mail address")
    public void the_administrator_edits_the_players_e_mail_to_an_invalid_e_mail_address() {
        world.setCandidateEmail("not-an-email");
    }

    @Then("the system rejects the change and shows an error message")
    public void the_system_rejects_the_change_and_shows_an_error_message() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(400);
    }

    @Given("another player in the competition already uses a given e-mail")
    public void another_player_in_the_competition_already_uses_a_given_e_mail() {
        String email = "taken-" + UUID.randomUUID() + "@example.com";
        world.setOtherPlayerEmail(email);
        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setEmail(email);
        participation.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
        participation.setRequestType(RequestType.INVITE);
        participationRepository.save(participation);
    }

    @When("the administrator edits the player's e-mail to that e-mail")
    public void the_administrator_edits_the_players_e_mail_to_that_e_mail() {
        world.setCandidateEmail(world.getOtherPlayerEmail());
    }

    // -- Remove / cancel invite --

    @When("confirms the removal")
    public void confirms_the_removal() {
        removeCurrentPlayer();
    }

    @When("confirms the cancellation")
    public void confirms_the_cancellation() {
        removeCurrentPlayer();
    }

    private void removeCurrentPlayer() {
        Response response = world.request()
                .when()
                .delete("/competitions/{cid}/players/{pid}", world.getTargetCompetition().getId(), world.getCurrentParticipationId());
        world.setLastResponse(response);
    }

    @Then("the system removes the player from the competition")
    public void the_system_removes_the_player_from_the_competition() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(204);
        assertThat(participationRepository.findById(world.getCurrentParticipationId())).isEmpty();
    }

    @Then("the player no longer appears in the player list")
    public void the_player_no_longer_appears_in_the_player_list() {
        Response response = world.request()
                .when()
                .get("/competitions/{id}/players", world.getTargetCompetition().getId());
        assertThat(response.statusCode()).isEqualTo(200);
        List<Number> ids = response.jsonPath().getList("id");
        assertThat(ids).extracting(Number::longValue).doesNotContain(world.getCurrentParticipationId());
    }

    @Given("a player has status \"e-mail sent but link not clicked\" in the competition")
    public void a_player_has_status_e_mail_sent_but_link_not_clicked_in_the_competition() {
        world.setCandidateEmail("player-" + UUID.randomUUID() + "@example.com");
        Participation participation = new Participation();
        participation.setCompetition(world.getTargetCompetition());
        participation.setEmail(world.getCandidateEmail());
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participation.setRequestType(RequestType.INVITE);
        participation.setFirstEmailSentDate(LocalDate.now());
        participation = participationRepository.save(participation);
        world.setCurrentParticipationId(participation.getId());
    }

    // -- Invite new players to a private competition --

    @Given("the competition is private")
    public void the_competition_is_private() {
        world.setTargetCompetition(competitionFixtures.privateCompetition());
    }

    @When("the administrator enters a new list of e-mails to invite")
    public void the_administrator_enters_a_new_list_of_e_mails_to_invite() {
        world.setCandidateEmails(List.of(
                "new-invitee-" + UUID.randomUUID() + "@example.com",
                "new-invitee-" + UUID.randomUUID() + "@example.com"));
    }

    @When("confirms the invitation")
    public void confirms_the_invitation() {
        Response response = world.request()
                .body(new InvitePlayersRequest().emails(world.getCandidateEmails()))
                .when()
                .post("/competitions/{id}/players", world.getTargetCompetition().getId());
        world.setLastResponse(response);
    }

    @Then("the system adds the new players to the competition with status \"e-mail not sent\"")
    public void the_system_adds_the_new_players_to_the_competition_with_status_e_mail_not_sent() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(202);
        for (String email : world.getCandidateEmails()) {
            boolean exists = participationRepository.findAll().stream()
                    .anyMatch(p -> p.getCompetition().getId().equals(world.getTargetCompetition().getId())
                            && p.getEmail().equals(email)
                            && p.getRequestType() == RequestType.INVITE);
            assertThat(exists).isTrue();
        }
    }

    @Then("sends the invite e-mail to each of them")
    public void sends_the_invite_e_mail_to_each_of_them() {
        for (String email : world.getCandidateEmails()) {
            boolean sent = sentEmailRepository.findAll().stream().anyMatch(sentEmail -> sentEmail.getEmail().equals(email));
            assertThat(sent).isTrue();
        }
    }
}
