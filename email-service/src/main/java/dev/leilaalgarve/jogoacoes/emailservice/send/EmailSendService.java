package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.SendEmailRequest;
import dev.leilaalgarve.jogoacoes.emailservice.domain.RecipientDomainVerifier;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateReference;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Accepts one e-mail built from one of the client's own templates (spec 05-031). Nothing is
 * rendered, sent or published here: the checks a client can fix (sender configured, template
 * exists, recipient's domain accepts e-mail) answer right away, and the send is recorded as
 * PENDING. {@link EmailQueuePublisher} puts it on the send queue in the background (spec 05-046),
 * so neither SQS nor SES being down fails this call.
 *
 * <p>The recipient's domain is checked last, after sender and template (specs 05-047 and 05-048):
 * a request those reject never reaches DNS, and a rejected domain records and queues nothing.
 */
@Service
public class EmailSendService {

    private final ClientSenderRepository senderRepository;
    private final EmailSendRepository sendRepository;
    private final TemplateService templateService;
    private final RecipientDomainVerifier recipientDomainVerifier;
    private final ObjectMapper objectMapper;

    public EmailSendService(ClientSenderRepository senderRepository, EmailSendRepository sendRepository,
                            TemplateService templateService, RecipientDomainVerifier recipientDomainVerifier,
                            ObjectMapper objectMapper) {
        this.senderRepository = senderRepository;
        this.sendRepository = sendRepository;
        this.templateService = templateService;
        this.recipientDomainVerifier = recipientDomainVerifier;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID send(String clientId, SendEmailRequest request) {
        ClientSender sender = senderRepository.findById(clientId).orElseThrow(SenderNotConfiguredException::new);
        TemplateReference template = templateService.reference(clientId, request.getTemplateName());
        recipientDomainVerifier.verify(request.getRecipientEmail());

        Map<String, Object> templateData = request.getTemplateData() == null ? Map.of() : request.getTemplateData();
        EmailSend send = sendRepository.save(new EmailSend(clientId, template.id(), request.getRecipientEmail(),
                sender.getAddress(), template.sesTemplateName(), objectMapper.writeValueAsString(templateData),
                Instant.now()));
        return send.getId();
    }
}
