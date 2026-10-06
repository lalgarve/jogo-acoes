package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.SendEmailRequest;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateReference;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateService;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Queues one e-mail built from one of the client's own templates (spec 05-031). Nothing is
 * rendered or sent here: the message goes to the send queue and email-lambda asks SES to send the
 * template, rendered by SES itself.
 *
 * <p>{@code email_send} is written before publishing, in the same transaction: if the publish
 * fails, the row is rolled back and the client gets a 503, so there is never a recorded send that
 * never reached the queue (plan.md, "Falha ao publicar"). The reverse -- published, then the commit
 * fails -- leaves an e-mail without a row; accepted, it is rare.
 */
@Service
public class EmailSendService {

    private final ClientSenderRepository senderRepository;
    private final EmailSendRepository sendRepository;
    private final TemplateService templateService;
    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public EmailSendService(ClientSenderRepository senderRepository, EmailSendRepository sendRepository,
                            TemplateService templateService, SqsTemplate sqsTemplate,
                            @Value("${email.queue-name}") String queueName) {
        this.senderRepository = senderRepository;
        this.sendRepository = sendRepository;
        this.templateService = templateService;
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    @Transactional
    public UUID send(String clientId, SendEmailRequest request) {
        ClientSender sender = senderRepository.findById(clientId).orElseThrow(SenderNotConfiguredException::new);
        TemplateReference template = templateService.reference(clientId, request.getTemplateName());

        EmailSend send = sendRepository.saveAndFlush(
                new EmailSend(clientId, template.id(), request.getRecipientEmail(), Instant.now()));

        Map<String, Object> templateData = request.getTemplateData() == null ? Map.of() : request.getTemplateData();
        EmailQueueMessage message = new EmailQueueMessage(EmailQueueMessage.SCHEMA_VERSION, send.getId().toString(),
                sender.getAddress(), request.getRecipientEmail(), template.sesTemplateName(), templateData);
        try {
            sqsTemplate.send(queueName, message);
        } catch (RuntimeException e) {
            throw new EmailQueuePublishException(e);
        }
        return send.getId();
    }
}
