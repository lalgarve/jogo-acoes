package dev.leilaalgarve.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

/**
 * Dev-only "live consumer" for the e-mail command queue (spec 05-021) -- lets a message
 * published by app/ under a normal docker-compose dev session actually reach the simulated
 * SES, instead of sitting in the queue forever, without waiting for a real AWS Lambda event
 * source mapping (blocked on AWS account access, docs/context/iteracao-4.md, decision 7).
 *
 * Off by default (email.dev-poller.enabled); only docker-compose.email-lambda.yml turns it on.
 * Reuses EmailSendHandler as-is -- this class is purely an alternative invocation path (long
 * polling in a dedicated thread instead of the real Lambda runtime), never a reimplementation
 * of the send logic.
 *
 * A message is deleted only after EmailSendHandler.handleRequest returns without throwing;
 * SQS's own redrive policy re-delivers anything left behind, same reasoning already documented
 * on EmailSendHandler itself for the real Lambda path.
 */
@ApplicationScoped
public class EmailQueuePoller {

    private static final Logger LOG = Logger.getLogger(EmailQueuePoller.class);
    private static final int WAIT_TIME_SECONDS = 20;
    private static final int MAX_MESSAGES_PER_POLL = 10;

    @Inject
    SqsClient sqsClient;

    @Inject
    EmailSendHandler handler;

    @ConfigProperty(name = "email.dev-poller.enabled")
    boolean enabled;

    @ConfigProperty(name = "email.queue-name")
    String queueName;

    private Thread pollerThread;

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
        pollerThread = new Thread(() -> poll(queueUrl), "email-queue-poller");
        pollerThread.setDaemon(true);
        pollerThread.start();
        LOG.infof("Dev poller started, consuming queue %s", queueName);
    }

    void onStop(@Observes ShutdownEvent event) {
        if (pollerThread == null) {
            return;
        }
        pollerThread.interrupt();
        try {
            pollerThread.join(WAIT_TIME_SECONDS * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void poll(String queueUrl) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                pollOnce(queueUrl);
            } catch (RuntimeException e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                LOG.warn("Failed to receive from the e-mail queue, retrying", e);
            }
        }
    }

    // Package-private (not private) so EmailQueuePollerTest can exercise a single receive/
    // handle/delete cycle directly, without needing to run/interrupt the actual polling thread.
    void pollOnce(String queueUrl) {
        ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .waitTimeSeconds(WAIT_TIME_SECONDS)
                .maxNumberOfMessages(MAX_MESSAGES_PER_POLL)
                .build());
        for (Message message : response.messages()) {
            handleOne(queueUrl, message);
        }
    }

    private void handleOne(String queueUrl, Message message) {
        try {
            handler.handleRequest(toSqsEvent(message), null);
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (RuntimeException e) {
            LOG.warn("Failed to process a queued e-mail message, leaving it for redrive", e);
        }
    }

    // Deliberately partial: only `body` is populated. Every other real AWS field on
    // SQSMessage (messageId, receiptHandle inside the event itself, attributes,
    // messageAttributes, eventSourceARN, awsRegion...) is left null/default. Safe today
    // because EmailSendHandler only reads the body (docs/context/iteracao-4.md, decision 1: the
    // handler is deliberately domain-blind, the message already carries everything it needs).
    // Would silently break if any future code path needs SQS's own messageId specifically --
    // the planned idempotency check (iteracao-4.md, decision 8) keys on EmailMessage's own
    // correlationId instead, which lives inside `body` and survives here untouched, so that
    // plan is not affected by this gap.
    private SQSEvent toSqsEvent(Message message) {
        SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        sqsMessage.setBody(message.body());
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(sqsMessage));
        return event;
    }
}
