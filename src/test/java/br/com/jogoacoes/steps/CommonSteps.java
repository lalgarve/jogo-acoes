package br.com.jogoacoes.steps;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;

public class CommonSteps {

    @Given("^the user is (the system administrator|a new player|a registered player)$")
    public void the_user_is(String identity) {
        throw new PendingException();
    }

    @Given("the user is logged into the system")
    public void the_user_is_logged_into_the_system() {
        throw new PendingException();
    }
}
