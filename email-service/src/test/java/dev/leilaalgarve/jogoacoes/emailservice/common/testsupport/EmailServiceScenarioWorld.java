package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import io.cucumber.spring.ScenarioScope;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Shared state for the step definitions of a single Cucumber scenario -- cucumber-spring
 * recreates this bean fresh per scenario ({@link ScenarioScope}), same pattern as app's own
 * {@code ScenarioWorld}. Authentication here is a header value, not a session cookie, so
 * there's no need for RestAssured's session filter.
 */
@Component
@ScenarioScope
public class EmailServiceScenarioWorld {

    @Value("${local.server.port}")
    private int port;

    private String apiKey;
    private Response lastResponse;

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public RequestSpecification request() {
        return requestWithApiKey(apiKey);
    }

    public RequestSpecification requestWithApiKey(String key) {
        return RestAssured.given()
                .port(port)
                .basePath("/api")
                .contentType("application/json")
                .header("X-API-Key", key);
    }

    public RequestSpecification requestWithoutApiKey() {
        return RestAssured.given()
                .port(port)
                .basePath("/api")
                .contentType("application/json");
    }

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }
}
