package dev.leilaalgarve.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.AccountSendingPausedException;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.MailFromDomainNotVerifiedException;
import software.amazon.awssdk.services.ses.model.MessageRejectedException;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendTemplatedEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;
import software.amazon.awssdk.services.ses.model.TemplateDoesNotExistException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

/**
 * Consumes the SQS send queue and asks SES to send each message's template (spec 05-031).
 * Deliberately dumb: the message names a template already on SES, the client's sender address and
 * the template's variables, and SES renders the e-mail itself ({@code SendTemplatedEmail}, SESv1,
 * the same API email-service creates the templates with) -- this handler knows nothing about
 * templates, clients or any application-domain concept.
 *
 * Spec 05-046: what SES answers when it does not send is classified here, because only this
 * handler sees it -- {@code TEMPLATE_MISSING}, {@code SES_REJECTED} or {@code SES_UNAVAILABLE} --
 * and the original message goes to the dead-letter queue with that {@code errorType} (and the
 * {@code errorMessage}), the invocation succeeding. Whether and when to try again is
 * email-service's decision, not this class's: no retry logic belongs here. A malformed message,
 * any other exception, or a DLQ that cannot be reached still fails the invocation: SQS redelivers
 * it and, after {@code maxReceiveCount}, its redrive policy moves it to the same DLQ without an
 * error type (specs/05-046-publicacao-na-fila-em-segundo-plano/contracts/dlq-message.md).
 *
 * correlationId goes on the SES message as a tag so a later consumer of the SES Event
 * Publishing pipeline can associate a bounce/complaint back to the email_send row that requested
 * it.
 */
@Named("emailSend")
public class EmailSendHandler implements RequestHandler<SQSEvent, Void> {

    @Inject
    SesClient sesClient;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SqsClient sqsClient;

    @ConfigProperty(name = "email.dlq-name")
    String dlqName;

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            EmailMessage message = parse(record.getBody());
            try {
                send(message);
            } catch (TemplateDoesNotExistException e) {
                deadLetter(record.getBody(), "TEMPLATE_MISSING", e);
            } catch (MessageRejectedException | MailFromDomainNotVerifiedException
                     | AccountSendingPausedException e) {
                deadLetter(record.getBody(), "SES_REJECTED", e);
            } catch (SesException e) {
                if (!sesUnavailable(e)) {
                    throw e;
                }
                deadLetter(record.getBody(), "SES_UNAVAILABLE", e);
            } catch (SdkClientException e) {
                deadLetter(record.getBody(), "SES_UNAVAILABLE", e);
            }
        }
        return null;
    }

    private EmailMessage parse(String body) {
        EmailMessage message;
        try {
            message = objectMapper.readValue(body, EmailMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed message on the e-mail queue: " + body, e);
        }
        // The old rendered subject/body message (gone since spec 05-031) fails here: no template.
        requirePresent(message.correlationId(), "correlationId", body);
        requirePresent(message.senderAddress(), "senderAddress", body);
        requirePresent(message.recipientEmail(), "recipientEmail", body);
        requirePresent(message.templateName(), "templateName", body);
        return message;
    }

    private static void requirePresent(String value, String field, String body) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Malformed message on the e-mail queue, no " + field + ": " + body);
        }
    }

    private void send(EmailMessage message) {
        SendTemplatedEmailRequest request = SendTemplatedEmailRequest.builder()
                .source(message.senderAddress())
                .destination(Destination.builder().toAddresses(message.recipientEmail()).build())
                .template(message.templateName())
                .templateData(templateDataJson(message.templateData()))
                .tags(MessageTag.builder().name("correlationId").value(message.correlationId()).build())
                .build();
        sesClient.sendTemplatedEmail(request);
    }

    /** SES failing on its side or throttling: may work later. Any other refusal is not classified. */
    private static boolean sesUnavailable(SesException e) {
        return e.statusCode() >= 500 || e.isThrottlingException();
    }

    private void deadLetter(String body, String errorType, Exception cause) {
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(dlqName).build()).queueUrl();
        String errorMessage = cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage();
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageAttributes(Map.of(
                        "errorType", stringAttribute(errorType),
                        "errorMessage", stringAttribute(errorMessage)))
                .build());
    }

    private static MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private String templateDataJson(Map<String, Object> templateData) {
        try {
            return objectMapper.writeValueAsString(templateData == null ? Map.of() : templateData);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Template data can't be written as JSON", e);
        }
    }
}
