package dev.leilaalgarve.jogoacoes.emailservice.send;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Publishes PENDING sends to the send queue in the background (spec 05-046). Each round locks up
 * to {@code batch-size} due rows ({@code FOR UPDATE SKIP LOCKED}, so several instances never
 * publish the same send) and, in the same transaction, publishes each one and records the
 * outcome: QUEUED (template data dropped), or a QUEUE_UNAVAILABLE attempt for {@link RetryPolicy}.
 *
 * <p>Publishes with {@link SqsAsyncClient} itself, not {@code SqsTemplate}: SqsTemplate creates a
 * queue it does not find, which would hide a missing queue instead of retrying later. A publish
 * that succeeds before a commit that fails is published again on the next round -- accepted, rare;
 * duplicates are another spec's.
 */
@Component
class EmailQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(EmailQueuePublisher.class);
    private static final long SQS_TIMEOUT_SECONDS = 10;

    private final EmailSendRepository repository;
    private final RetryPolicy retryPolicy;
    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String queueName;
    private final int batchSize;

    EmailQueuePublisher(EmailSendRepository repository, RetryPolicy retryPolicy, SqsAsyncClient sqsClient,
                        ObjectMapper objectMapper, TransactionTemplate transactionTemplate,
                        @Value("${email.queue-name}") String queueName,
                        @Value("${email.publisher.batch-size:20}") int batchSize) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.queueName = queueName;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${email.publisher.poll-interval:1s}")
    void publishDue() {
        Integer published;
        do {
            published = transactionTemplate.execute(status -> publishBatch());
        } while (published != null && published == batchSize);
    }

    /** One batch in one transaction; how many rows it handled. */
    private int publishBatch() {
        Instant now = Instant.now();
        List<EmailSend> due = repository.lockDue(now, batchSize);
        if (due.isEmpty()) {
            return 0;
        }
        String queueUrl;
        try {
            queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                    .get(SQS_TIMEOUT_SECONDS, TimeUnit.SECONDS).queueUrl();
        } catch (Exception e) {
            due.forEach(send -> queueUnavailable(send, e, now));
            return due.size();
        }
        for (EmailSend send : due) {
            try {
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(messageBody(send))
                        .build()).get(SQS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                send.markQueued(Instant.now());
            } catch (Exception e) {
                queueUnavailable(send, e, now);
            }
        }
        return due.size();
    }

    private void queueUnavailable(EmailSend send, Exception e, Instant now) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        String message = e.getCause() != null ? e.getCause().toString() : e.toString();
        log.warn("Could not publish send {} to queue {}, trying again later: {}", send.getId(), queueName, message);
        retryPolicy.onFailure(send, DeliveryErrorType.QUEUE_UNAVAILABLE, message, send.getTemplateData(), false, now);
    }

    @SuppressWarnings("unchecked")
    private String messageBody(EmailSend send) {
        Map<String, Object> templateData = send.getTemplateData() == null
                ? Map.of() : objectMapper.readValue(send.getTemplateData(), Map.class);
        return objectMapper.writeValueAsString(new EmailQueueMessage(EmailQueueMessage.SCHEMA_VERSION,
                send.getId().toString(), send.getSenderAddress(), send.getRecipientEmail(),
                send.getSesTemplateName(), templateData));
    }
}
