package dev.leilaalgarve.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendTemplatedEmailRequest;

import java.util.Map;

/**
 * Consumes the SQS send queue and asks SES to send each message's template (spec 05-031).
 * Deliberately dumb: the message names a template already on SES, the client's sender address and
 * the template's variables, and SES renders the e-mail itself ({@code SendTemplatedEmail}, SESv1,
 * the same API email-service creates the templates with) -- this handler knows nothing about
 * templates, clients or any application-domain concept.
 *
 * SQS's own redrive policy (maxReceiveCount + DLQ, infrastructure config -- docs/context/
 * iteracao-4.md, decision 4) is what implements retry: a thrown exception here just fails this
 * invocation and lets SQS redeliver, no retry logic belongs in this class.
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

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            send(parse(record.getBody()));
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

    private String templateDataJson(Map<String, Object> templateData) {
        try {
            return objectMapper.writeValueAsString(templateData == null ? Map.of() : templateData);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Template data can't be written as JSON", e);
        }
    }
}
