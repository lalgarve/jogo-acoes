package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import io.cucumber.java.After;

/**
 * Deletes the API keys a scenario issued (ScenarioApiKeys) -- the api_key schema persists across
 * runs (docker-compose.yml), same reason as {@link TemplateCleanupHooks}. Unlike templates,
 * never wipes the whole table: the versioned test key lives there too.
 */
public class ApiKeyCleanupHooks {

    private final ScenarioApiKeys apiKeys;

    public ApiKeyCleanupHooks(ScenarioApiKeys apiKeys) {
        this.apiKeys = apiKeys;
    }

    @After
    public void deleteIssuedKeys() {
        apiKeys.deleteIssued();
    }
}
