package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import io.cucumber.java.After;
import io.cucumber.java.Before;

/**
 * Every scenario starts with the send queue existing and empty (LocalStack persists across runs,
 * same reason as {@link TemplateCleanupHooks}), and ends with it existing again -- the "queue is
 * unavailable" scenario deletes it.
 */
public class SendQueueHooks {

    private final SendQueue sendQueue;

    public SendQueueHooks(SendQueue sendQueue) {
        this.sendQueue = sendQueue;
    }

    @Before
    public void startWithAnEmptyQueue() {
        sendQueue.ensureExists();
        sendQueue.drain();
    }

    @After
    public void recreateTheQueue() {
        sendQueue.ensureExists();
    }
}
