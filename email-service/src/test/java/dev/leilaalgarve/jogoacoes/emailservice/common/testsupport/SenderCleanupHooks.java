package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import io.cucumber.java.After;

/** Deletes the sender addresses a scenario configured ({@link ScenarioSenders}). */
public class SenderCleanupHooks {

    private final ScenarioSenders senders;

    public SenderCleanupHooks(ScenarioSenders senders) {
        this.senders = senders;
    }

    @After
    public void deleteConfiguredSenders() {
        senders.deleteConfigured();
    }
}
