package br.com.jogoacoes.steps;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class CreateCompetitionSteps {

    @Given("is on the competition creation screen")
    public void is_on_the_competition_creation_screen() {
        throw new PendingException();
    }

    @Given("they choose the public competition option")
    public void they_choose_the_public_competition_option() {
        throw new PendingException();
    }

    @Given("they choose the private competition option")
    public void they_choose_the_private_competition_option() {
        throw new PendingException();
    }

    @Given("define the competition name")
    public void define_the_competition_name() {
        throw new PendingException();
    }

    @Given("leave the competition name empty")
    public void leave_the_competition_name_empty() {
        throw new PendingException();
    }

    @Given("enter a list of e-mails")
    public void enter_a_list_of_e_mails() {
        throw new PendingException();
    }

    @Given("leave the e-mail list empty")
    public void leave_the_e_mail_list_empty() {
        throw new PendingException();
    }

    @Given("enter a list of e-mails containing an invalid e-mail address")
    public void enter_a_list_of_e_mails_containing_an_invalid_e_mail_address() {
        throw new PendingException();
    }

    @Given("define the start date")
    public void define_the_start_date() {
        throw new PendingException();
    }

    @Given("define the start date as {}")
    public void define_the_start_date_as(String startDate) {
        throw new PendingException();
    }

    @Given("define the duration")
    public void define_the_duration() {
        throw new PendingException();
    }

    @Given("define the duration as {}")
    public void define_the_duration_as(String duration) {
        throw new PendingException();
    }

    @Given("define whether it is recurring or not")
    public void define_whether_it_is_recurring_or_not() {
        throw new PendingException();
    }

    @Given("define the buy brokerage fee")
    public void define_the_buy_brokerage_fee() {
        throw new PendingException();
    }

    @Given("define the buy brokerage fee as {}")
    public void define_the_buy_brokerage_fee_as(String buyFee) {
        throw new PendingException();
    }

    @Given("define the sell brokerage fee")
    public void define_the_sell_brokerage_fee() {
        throw new PendingException();
    }

    @Given("define the sell brokerage fee as {}")
    public void define_the_sell_brokerage_fee_as(String sellFee) {
        throw new PendingException();
    }

    @When("they click the \"create\" button")
    public void they_click_the_create_button() {
        throw new PendingException();
    }

    @When("click the \"create\" button")
    public void click_the_create_button() {
        throw new PendingException();
    }

    @Then("the system should create a new public competition")
    public void the_system_should_create_a_new_public_competition() {
        throw new PendingException();
    }

    @Then("show the creation success screen")
    public void show_the_creation_success_screen() {
        throw new PendingException();
    }

    @Given("the system creates the new private competition and shows the success screen asking whether to send the invite e-mails now or not")
    public void the_system_creates_the_new_private_competition_and_shows_the_success_screen() {
        throw new PendingException();
    }

    @When("the administrator decides to send the invite e-mails {word}")
    public void the_administrator_decides_to_send_the_invite_e_mails(String timing) {
        throw new PendingException();
    }

    @Then("the system sends the invite e-mails to the provided list")
    public void the_system_sends_the_invite_e_mails_to_the_provided_list() {
        throw new PendingException();
    }

    @Then("the system does not send the invite e-mails and keeps the competition awaiting sending")
    public void the_system_does_not_send_the_invite_e_mails_and_keeps_the_competition_awaiting_sending() {
        throw new PendingException();
    }

    @Then("the system rejects the competition creation and shows an error message about the {}")
    public void the_system_rejects_the_competition_creation_and_shows_an_error_message_about_the(String error) {
        throw new PendingException();
    }

    @Given("is not the system administrator")
    public void is_not_the_system_administrator() {
        throw new PendingException();
    }

    @When("they try to access the competition creation screen")
    public void they_try_to_access_the_competition_creation_screen() {
        throw new PendingException();
    }

    @Then("the system denies access and shows an error message")
    public void the_system_denies_access_and_shows_an_error_message() {
        throw new PendingException();
    }
}
