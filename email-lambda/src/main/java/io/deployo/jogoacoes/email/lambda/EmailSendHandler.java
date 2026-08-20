package io.deployo.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Consumes the SQS command queue and sends each message via SES. Deliberately dumb: the
 * message already carries the rendered subject/body (docs/iteracao-4.md, decision 1), so this
 * handler has zero knowledge of EmailTemplate, competitions, or any other application-domain
 * concept -- exactly so the queue contract never needs to change when the main app's template
 * catalog does.
 *
 * SQS's own redrive policy (maxReceiveCount + DLQ, infrastructure config -- decision 4) is
 * what implements retry: a thrown exception here just fails this invocation and lets SQS
 * redeliver, no retry logic belongs in this class.
 *
 * correlationId goes on the SES message as a tag so a later consumer of the SES Event
 * Publishing pipeline (decision 10, EMAIL_EVENT) can associate a bounce/complaint back to the
 * sent_email row that requested it.
 */
@Named("emailSend")
public class EmailSendHandler implements RequestHandler<SQSEvent, Void> {

    @Inject
    SesClient sesClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "email.sender-address")
    String senderAddress;

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            send(parse(record.getBody()));
        }
        return null;
    }

    private EmailMessage parse(String body) {
        try {
            return objectMapper.readValue(body, EmailMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed message on the e-mail queue: " + body, e);
        }
    }

    private void send(EmailMessage message) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(senderAddress)
                .destination(Destination.builder().toAddresses(message.recipientEmail()).build())
                .message(Message.builder()
                        .subject(Content.builder().data(message.subject()).build())
                        .body(Body.builder().html(Content.builder().data(message.body()).build()).build())
                        .build())
                .tags(MessageTag.builder().name("correlationId").value(message.correlationId()).build())
                .build();
        sesClient.sendEmail(request);
    }
}
