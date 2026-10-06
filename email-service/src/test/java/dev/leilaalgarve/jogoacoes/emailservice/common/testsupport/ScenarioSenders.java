package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import dev.leilaalgarve.jogoacoes.emailservice.send.ClientSender;
import dev.leilaalgarve.jogoacoes.emailservice.send.ClientSenderRepository;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Sender addresses a scenario configures for its clients -- the row operations writes with
 * scripts/set-email-sender.sh (spec 05-031), written here straight through the repository. Only
 * rows created here are deleted afterwards ({@link SenderCleanupHooks}), never the test client's
 * own (jogo-acoes), which scripts/test-api-key.sh restore sets up.
 */
@Component
@ScenarioScope
public class ScenarioSenders {

    private final ClientSenderRepository repository;
    private final List<String> configuredClients = new ArrayList<>();

    public ScenarioSenders(ClientSenderRepository repository) {
        this.repository = repository;
    }

    public void configure(String clientId, String address) {
        repository.save(new ClientSender(clientId, address, Instant.now()));
        configuredClients.add(clientId);
    }

    void deleteConfigured() {
        repository.deleteAllById(configuredClients);
        configuredClients.clear();
    }
}
