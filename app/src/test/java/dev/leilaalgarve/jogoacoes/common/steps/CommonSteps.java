package dev.leilaalgarve.jogoacoes.common.steps;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import dev.leilaalgarve.jogoacoes.common.testsupport.LoginHelper;
import dev.leilaalgarve.jogoacoes.common.testsupport.ScenarioWorld;
import dev.leilaalgarve.jogoacoes.common.testsupport.SwitchableEmailSender;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonSteps {

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final LoginHelper loginHelper;
    private final SwitchableEmailSender emailSender;

    public CommonSteps(ScenarioWorld world, UserMother userMother, LoginHelper loginHelper,
                       SwitchableEmailSender emailSender) {
        this.world = world;
        this.userMother = userMother;
        this.loginHelper = loginHelper;
        this.emailSender = emailSender;
    }

    /** A scenario that switched email-service off must not leave it off for the next one. */
    @After
    public void switchEmailServiceBackOn() {
        emailSender.makeAvailable();
    }

    @Given("^the user is (the system administrator|a new player|a registered player)$")
    public void the_user_is(String identity) {
        switch (identity) {
            case "the system administrator" -> world.setCurrentUser(userMother.administrator());
            case "a registered player" -> world.setCurrentUser(userMother.registeredPlayer());
            case "a new player" -> world.setCurrentUser(null); // no account yet -- not covered by this pass
            default -> throw new IllegalArgumentException("Unknown identity: " + identity);
        }
    }

    @Given("the user is logged into the system")
    public void the_user_is_logged_into_the_system() {
        if (world.getCurrentUser() == null) {
            world.setCurrentUser(userMother.registeredPlayer());
        }
        loginHelper.loginAs(world, world.getCurrentUser());
    }

    @Then("the system denies access")
    public void the_system_denies_access() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(403);
    }

    @Then("the system rejects the request as invalid")
    public void the_system_rejects_the_request_as_invalid() {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(400);
    }
}
