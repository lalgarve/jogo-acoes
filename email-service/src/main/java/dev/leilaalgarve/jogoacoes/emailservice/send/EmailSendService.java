package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.SendEmailRequest;
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
 * exists) answer right away, and the send is recorded as PENDING. {@link EmailQueuePublisher}
 * puts it on the send queue in the background (spec 05-046), so neither SQS nor SES being down
 * fails this call.
 */
@Service
public class EmailSendService {

    private final ClientSenderRepository senderRepository;
    private final EmailSendRepository sendRepository;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    public EmailSendService(ClientSenderRepository senderRepository, EmailSendRepository sendRepository,
                            TemplateService templateService, ObjectMapper objectMapper) {
        this.senderRepository = senderRepository;
        this.sendRepository = sendRepository;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID send(String clientId, SendEmailRequest request) {
        ClientSender sender = senderRepository.findById(clientId).orElseThrow(SenderNotConfiguredException::new);
        TemplateReference template = templateService.reference(clientId, request.getTemplateName());

        Map<String, Object> templateData = request.getTemplateData() == null ? Map.of() : request.getTemplateData();
        EmailSend send = sendRepository.save(new EmailSend(clientId, template.id(), request.getRecipientEmail(),
                sender.getAddress(), template.sesTemplateName(), objectMapper.writeValueAsString(templateData),
                Instant.now()));
        return send.getId();
    }
}
