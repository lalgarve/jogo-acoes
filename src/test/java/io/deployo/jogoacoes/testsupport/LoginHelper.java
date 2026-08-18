package io.deployo.jogoacoes.testsupport;

import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.steps.ScenarioWorld;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Establishes a real session for a registered user by actually calling GET
 * /login-links/{token}, the same way CommonSteps' "the user is logged into the system" does
 * -- extracted here so other step classes (e.g. request_competition_entry's "registered and
 * logged in" player) can reuse it instead of duplicating the HTTP round trip.
 */
@Component
public class LoginHelper {

    private final LoginLinkRepository loginLinkRepository;

    public LoginHelper(LoginLinkRepository loginLinkRepository) {
        this.loginLinkRepository = loginLinkRepository;
    }

    public void loginAs(ScenarioWorld world, User user) {
        loginAs(world, user, ScenarioWorld.PRIMARY_DEVICE);
    }

    public void loginAs(ScenarioWorld world, User user, String device) {
        LoginLink link = new LoginLink();
        link.setToken(UUID.randomUUID().toString());
        link.setEmail(user.getEmail());
        link.setUser(user);
        link.setExpiresAt(LocalDateTime.now().plusHours(1));
        link = loginLinkRepository.save(link);

        Response response = world.request(device)
                .when()
                .get("/login-links/{token}", link.getToken());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
