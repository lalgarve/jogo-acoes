package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.template.SesUnavailableException;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the sends email-lambda could not deliver (spec 05-046, contracts/dlq-message.md). The
 * message body is the send queue's message itself; its {@code errorType} attribute says why
 * (missing: SQS's redrive moved it, {@link DeliveryErrorType#UNKNOWN}). The send is found by its
 * {@code correlationId}, the attempt recorded, and {@link RetryPolicy} decides: SES_REJECTED gives
 * up; TEMPLATE_MISSING recreates the template (once per burst) and publishes again right away;
 * anything else publishes again later. The template data comes back from the message body -- the
 * row stopped keeping it once the send was queued.
 */
@Component
class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final EmailSendRepository repository;
    private final TemplateService templateService;
    private final RetryPolicy retryPolicy;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    DeadLetterConsumer(EmailSendRepository repository, TemplateService templateService, RetryPolicy retryPolicy,
                       ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.templateService = templateService;
        this.retryPolicy = retryPolicy;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @SqsListener("${email.dlq-name}")
    void onDeadLetter(Message<String> message) {
        Optional<EmailQueueMessage> body = parse(message.getPayload());
        if (body.isEmpty()) {
            return;
        }
        DeliveryErrorType type = DeliveryErrorType.fromAttribute(message.getHeaders().get("errorType", String.class));
        String error = Optional.ofNullable(message.getHeaders().get("errorMessage", String.class))
                .orElse("no error message");
        UUID sendId = UUID.fromString(body.get().correlationId());
        Long templateId = repository.findById(sendId).map(EmailSend::getTemplateId).orElse(null);
        if (templateId == null) {
            log.warn("Dead-letter message for send {} with no record; dropped", sendId);
            return;
        }

        boolean retryNow = false;
        if (type == DeliveryErrorType.TEMPLATE_MISSING) {
            try {
                templateService.recreateOnSesUnlessRecent(templateId);
                retryNow = true;
            } catch (SesUnavailableException e) {
                log.warn("Could not recreate the template of send {} on SES, trying again later: {}", sendId,
                        e.getMessage());
                type = DeliveryErrorType.SES_UNAVAILABLE;
                error = e.getMessage();
            }
        }
        record(sendId, type, error, objectMapper.writeValueAsString(templateData(body.get())), retryNow);
    }

    private void record(UUID sendId, DeliveryErrorType type, String error, String templateData, boolean retryNow) {
        transactionTemplate.executeWithoutResult(status -> repository.findById(sendId).ifPresent(send -> {
            if (send.getStatus() != EmailSendStatus.QUEUED) {
                // A second copy of the same delivery error: the first one already decided.
                log.warn("Dead-letter message for send {} that is {}, not QUEUED; dropped", sendId, send.getStatus());
                return;
            }
            log.warn("Send {} was not delivered ({}): {}", sendId, type, error);
            retryPolicy.onFailure(send, type, error, templateData, retryNow, Instant.now());
        }));
    }

    private Optional<EmailQueueMessage> parse(String payload) {
        try {
            EmailQueueMessage body = objectMapper.readValue(payload, EmailQueueMessage.class);
            UUID.fromString(body.correlationId());
            return Optional.of(body);
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            log.warn("Dead-letter message is not a send queue message; dropped: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Map<String, Object> templateData(EmailQueueMessage body) {
        return body.templateData() == null ? Map.of() : body.templateData();
    }
}
