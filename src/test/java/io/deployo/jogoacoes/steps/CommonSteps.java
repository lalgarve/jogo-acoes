package io.deployo.jogoacoes.steps;

import io.cucumber.java.en.Given;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.testsupport.UserMother;
import io.restassured.response.Response;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonSteps {

    private final ScenarioWorld world;
    private final UserMother userMother;
    private final LoginLinkRepository loginLinkRepository;

    public CommonSteps(ScenarioWorld world, UserMother userMother, LoginLinkRepository loginLinkRepository) {
        this.world = world;
        this.userMother = userMother;
        this.loginLinkRepository = loginLinkRepository;
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

        LoginLink link = new LoginLink();
        link.setToken(UUID.randomUUID().toString());
        link.setEmail(world.getCurrentUser().getEmail());
        link.setUser(world.getCurrentUser());
        link.setExpiresAt(LocalDateTime.now().plusHours(1));
        link = loginLinkRepository.save(link);

        Response response = world.request()
                .when()
                .get("/login-links/{token}", link.getToken());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
