package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.link.LinkRecord;
import dev.leilaalgarve.jogoacoes.link.LinkRecordRepository;
import dev.leilaalgarve.jogoacoes.user.User;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SessionConfig;
import io.restassured.filter.session.SessionFilter;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-037 (T006): the branch of {@link SessionsService#endCurrent()} where an authenticated
 * request has no active {@link LoginSession} -- not reachable through the normal login flow, so
 * it isn't a Gherkin scenario. Simulated by ending the row directly after a real login: the
 * HTTP session is still valid, but no active LOGIN_SESSION points at it. Logging out must still
 * delete the HTTP session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LogoutWithoutLoginSessionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserMother userMother;

    @Autowired
    private LinkRecordRepository linkRecordRepository;

    @Autowired
    private LoginSessionRepository loginSessionRepository;

    private final SessionFilter sessionFilter = new SessionFilter();

    @Test
    void logoutDeletesTheHttpSessionEvenWithoutAnActiveLoginSession() {
        User player = userMother.registeredPlayer();
        assertThat(request().get("/login-links/{token}", activeLinkFor(player).getToken()).statusCode())
                .isEqualTo(200);

        List<LoginSession> sessions = loginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(player.getId());
        assertThat(sessions).hasSize(1);
        LoginSession session = sessions.get(0);
        session.setEndedAt(LocalDateTime.now());
        loginSessionRepository.save(session);
        assertThat(request().get("/sessions").statusCode())
                .as("still authenticated -- only our own row was ended")
                .isEqualTo(200);

        assertThat(request().delete("/sessions/current").statusCode()).isEqualTo(204);

        assertThat(request().get("/sessions").statusCode()).isEqualTo(401);
    }

    private RequestSpecification request() {
        return RestAssured.given()
                .port(port)
                .basePath("/api")
                // Same as ScenarioWorld: Spring Session's cookie is "SESSION", not "JSESSIONID".
                .config(RestAssuredConfig.config().sessionConfig(new SessionConfig().sessionIdName("SESSION")))
                .filter(sessionFilter)
                .contentType("application/json");
    }

    private LinkRecord activeLinkFor(User user) {
        LinkRecord link = new LinkRecord();
        link.setToken(UUID.randomUUID().toString());
        link.setServiceKey(LoginLinkHandler.KEY);
        link.setEmail(user.getEmail());
        link.setUserId(user.getId());
        link.setExpiresAt(LocalDateTime.now().plusHours(1));
        return linkRecordRepository.save(link);
    }
}
