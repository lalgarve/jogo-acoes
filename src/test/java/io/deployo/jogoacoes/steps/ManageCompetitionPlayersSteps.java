package io.deployo.jogoacoes.steps;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ManageCompetitionPlayersSteps {

    @Given("is on the player management screen for a competition")
    public void is_on_the_player_management_screen_for_a_competition() {
        throw new PendingException();
    }

    @Given("a player in the competition has status {} since {}")
    public void a_player_in_the_competition_has_status_since(String status, String date) {
        throw new PendingException();
    }

    @Then("the player list shows the player's status as {} along with the date {}")
    public void the_player_list_shows_the_players_status_as_along_with_the_date(String status, String date) {
        throw new PendingException();
    }

    @When("the administrator filters the player list by {}")
    public void the_administrator_filters_the_player_list_by(String status) {
        throw new PendingException();
    }

    @Then("only players with status {} are shown")
    public void only_players_with_status_are_shown(String status) {
        throw new PendingException();
    }

    @Given("a player is listed in the competition")
    public void a_player_is_listed_in_the_competition() {
        throw new PendingException();
    }

    @When("the administrator chooses to {}")
    public void the_administrator_chooses_to(String action) {
        throw new PendingException();
    }

    @Then("the system sends the invite e-mail to the player")
    public void the_system_sends_the_invite_e_mail_to_the_player() {
        throw new PendingException();
    }

    @Then("updates the e-mail sent date")
    public void updates_the_e_mail_sent_date() {
        throw new PendingException();
    }

    @Given("multiple players are listed in the competition")
    public void multiple_players_are_listed_in_the_competition() {
        throw new PendingException();
    }

    @When("the administrator selects a group of players")
    public void the_administrator_selects_a_group_of_players() {
        throw new PendingException();
    }

    @When("chooses to send or resend the e-mail to the selected group")
    public void chooses_to_send_or_resend_the_e_mail_to_the_selected_group() {
        throw new PendingException();
    }

    @Then("the system sends the invite e-mail to each selected player")
    public void the_system_sends_the_invite_e_mail_to_each_selected_player() {
        throw new PendingException();
    }

    @Then("updates the e-mail sent date for each of them")
    public void updates_the_e_mail_sent_date_for_each_of_them() {
        throw new PendingException();
    }

    @When("the administrator edits the player's e-mail")
    public void the_administrator_edits_the_players_e_mail() {
        throw new PendingException();
    }

    @When("confirms the change")
    public void confirms_the_change() {
        throw new PendingException();
    }

    @Then("the system updates the player's e-mail")
    public void the_system_updates_the_players_e_mail() {
        throw new PendingException();
    }

    @When("the administrator edits the player's e-mail to an invalid e-mail address")
    public void the_administrator_edits_the_players_e_mail_to_an_invalid_e_mail_address() {
        throw new PendingException();
    }

    @Then("the system rejects the change and shows an error message")
    public void the_system_rejects_the_change_and_shows_an_error_message() {
        throw new PendingException();
    }

    @Given("another player in the competition already uses a given e-mail")
    public void another_player_in_the_competition_already_uses_a_given_e_mail() {
        throw new PendingException();
    }

    @When("the administrator edits the player's e-mail to that e-mail")
    public void the_administrator_edits_the_players_e_mail_to_that_e_mail() {
        throw new PendingException();
    }

    @When("confirms the removal")
    public void confirms_the_removal() {
        throw new PendingException();
    }

    @Then("the system removes the player from the competition")
    public void the_system_removes_the_player_from_the_competition() {
        throw new PendingException();
    }

    @Then("the player no longer appears in the player list")
    public void the_player_no_longer_appears_in_the_player_list() {
        throw new PendingException();
    }

    @Given("a player has status \"e-mail sent but link not clicked\" in the competition")
    public void a_player_has_status_e_mail_sent_but_link_not_clicked_in_the_competition() {
        throw new PendingException();
    }

    @When("confirms the cancellation")
    public void confirms_the_cancellation() {
        throw new PendingException();
    }

    @Given("the competition is private")
    public void the_competition_is_private() {
        throw new PendingException();
    }

    @When("the administrator enters a new list of e-mails to invite")
    public void the_administrator_enters_a_new_list_of_e_mails_to_invite() {
        throw new PendingException();
    }

    @When("confirms the invitation")
    public void confirms_the_invitation() {
        throw new PendingException();
    }

    @Then("the system adds the new players to the competition with status \"e-mail not sent\"")
    public void the_system_adds_the_new_players_to_the_competition_with_status_e_mail_not_sent() {
        throw new PendingException();
    }

    @Then("sends the invite e-mail to each of them")
    public void sends_the_invite_e_mail_to_each_of_them() {
        throw new PendingException();
    }
}
