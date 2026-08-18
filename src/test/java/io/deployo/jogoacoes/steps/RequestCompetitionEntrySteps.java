package io.deployo.jogoacoes.steps;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class RequestCompetitionEntrySteps {

    @Given("^the player is (unregistered|registered and not logged in|registered and logged in)$")
    public void the_player_is(String status) {
        throw new PendingException();
    }

    @Given("clicked on Join a Competition")
    public void clicked_on_join_a_competition() {
        throw new PendingException();
    }

    @Given("clicked on a public competition")
    public void clicked_on_a_public_competition() {
        throw new PendingException();
    }

    @Given("entered a valid e-mail")
    public void entered_a_valid_e_mail() {
        throw new PendingException();
    }

    @Given("passed the not-a-robot test")
    public void passed_the_not_a_robot_test() {
        throw new PendingException();
    }

    @Then("the system adds the e-mail to the list of requesters and sends the link to finish registration by e-mail")
    public void the_system_adds_the_e_mail_to_the_list_of_requesters() {
        throw new PendingException();
    }

    @Then("the system adds the e-mail personalized with the name to the list of requesters and sends the link to finish registration by e-mail")
    public void the_system_adds_the_e_mail_personalized_with_the_name_to_the_list_of_requesters() {
        throw new PendingException();
    }

    @Given("confirmed entry into the competition")
    public void confirmed_entry_into_the_competition() {
        throw new PendingException();
    }

    @Then("the system adds the player to the public competition and redirects them to the competition page")
    public void the_system_adds_the_player_to_the_public_competition_and_redirects_them() {
        throw new PendingException();
    }

    @When("they try to access a private competition they were not invited to")
    public void they_try_to_access_a_private_competition_they_were_not_invited_to() {
        throw new PendingException();
    }

    @Then("the system does not allow the request and does not show the private competition in the list")
    public void the_system_does_not_allow_the_request_and_does_not_show_the_private_competition() {
        throw new PendingException();
    }

    @When("they enter an invalid e-mail address")
    public void they_enter_an_invalid_e_mail_address() {
        throw new PendingException();
    }

    @Then("the system shows an error message and does not send the registration link")
    public void the_system_shows_an_error_message_and_does_not_send_the_registration_link() {
        throw new PendingException();
    }

    @When("they fail the not-a-robot test")
    public void they_fail_the_not_a_robot_test() {
        throw new PendingException();
    }

    @Given("the player already requested entry into a public competition")
    public void the_player_already_requested_entry_into_a_public_competition() {
        throw new PendingException();
    }

    @Given("has not finished registration yet")
    public void has_not_finished_registration_yet() {
        throw new PendingException();
    }

    @When("they request entry into that same competition again")
    public void they_request_entry_into_that_same_competition_again() {
        throw new PendingException();
    }

    @Then("the system resends the link to finish registration by e-mail")
    public void the_system_resends_the_link_to_finish_registration_by_e_mail() {
        throw new PendingException();
    }
}
