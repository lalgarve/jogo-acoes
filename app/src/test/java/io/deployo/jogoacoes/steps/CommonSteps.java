package io.deployo.jogoacoes.steps;

import io.cucumber.java.en.Given;
import io.deployo.jogoacoes.testsupport.LoginHelper;
import io.deployo.jogoacoes.testsupport.UserMother;

public class CommonSteps {

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final LoginHelper loginHelper;

    public CommonSteps(ScenarioWorld world, UserMother userMother, LoginHelper loginHelper) {
        this.world = world;
        this.userMother = userMother;
        this.loginHelper = loginHelper;
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
}
