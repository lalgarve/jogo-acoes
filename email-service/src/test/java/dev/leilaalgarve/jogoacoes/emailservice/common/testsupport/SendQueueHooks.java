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
    private final DeadLetterQueue deadLetterQueue;

    public SendQueueHooks(SendQueue sendQueue, DeadLetterQueue deadLetterQueue) {
        this.sendQueue = sendQueue;
        this.deadLetterQueue = deadLetterQueue;
    }

    @Before
    public void startWithAnEmptyQueue() {
        deadLetterQueue.ensureExists();
        sendQueue.ensureExists();
        sendQueue.drain();
    }

    @After
    public void recreateTheQueue() {
        sendQueue.ensureExists();
    }
}
