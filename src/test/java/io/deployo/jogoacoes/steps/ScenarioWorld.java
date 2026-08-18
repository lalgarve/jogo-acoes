package io.deployo.jogoacoes.steps;

import io.cucumber.spring.ScenarioScope;
import io.deployo.jogoacoes.api.model.CompetitionCreateRequest;
import io.deployo.jogoacoes.domain.Competition;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.User;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SessionConfig;
import io.restassured.filter.session.SessionFilter;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared state for the step definitions of a single Cucumber scenario — cucumber-spring
 * recreates this bean fresh per scenario ({@link ScenarioScope}). Holds the RestAssured
 * session(s) (so steps in the same scenario share one HTTP session/cookie, i.e. one logged-in
 * device -- and login.feature's device-rule scenarios can address more than one, each with
 * its own independent cookie jar), the last HTTP response, the currently "logged in" domain
 * user, and whatever request body is being built up across a sequence of Given steps before
 * the actual call.
 */
@Component
@ScenarioScope
public class ScenarioWorld {

    public static final String PRIMARY_DEVICE = "primary";

    @Value("${local.server.port}")
    private int port;

    private final Map<String, SessionFilter> sessionFiltersByDevice = new HashMap<>();

    private User currentUser;
    private CompetitionCreateRequest competitionRequest;
    private Long competitionId;
    private Response lastResponse;
    private Competition targetCompetition;
    private String candidateEmail;
    private String candidateName;
    private LoginLink currentLoginLink;

    public RequestSpecification request() {
        return request(PRIMARY_DEVICE);
    }

    public RequestSpecification request(String device) {
        SessionFilter sessionFilter = sessionFiltersByDevice.computeIfAbsent(device, d -> new SessionFilter());
        return RestAssured.given()
                .port(port)
                .basePath("/api")
                // Spring Session's cookie is named "SESSION", not RestAssured's default
                // expectation of "JSESSIONID" -- without this, SessionFilter never notices
                // the Set-Cookie header and silently sends every request unauthenticated.
                .config(RestAssuredConfig.config().sessionConfig(new SessionConfig().sessionIdName("SESSION")))
                .filter(sessionFilter)
                .contentType("application/json");
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public CompetitionCreateRequest getCompetitionRequest() {
        return competitionRequest;
    }

    public void setCompetitionRequest(CompetitionCreateRequest competitionRequest) {
        this.competitionRequest = competitionRequest;
    }

    public Long getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(Long competitionId) {
        this.competitionId = competitionId;
    }

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }

    public Competition getTargetCompetition() {
        return targetCompetition;
    }

    public void setTargetCompetition(Competition targetCompetition) {
        this.targetCompetition = targetCompetition;
    }

    public String getCandidateEmail() {
        return candidateEmail;
    }

    public void setCandidateEmail(String candidateEmail) {
        this.candidateEmail = candidateEmail;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public LoginLink getCurrentLoginLink() {
        return currentLoginLink;
    }

    public void setCurrentLoginLink(LoginLink currentLoginLink) {
        this.currentLoginLink = currentLoginLink;
    }
}
