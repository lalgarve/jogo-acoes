package dev.leilaalgarve.jogoacoes.emailservice.send;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.SendEmailRequest;
import dev.leilaalgarve.jogoacoes.emailservice.domain.RecipientDomainVerifier;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateReference;
import dev.leilaalgarve.jogoacoes.emailservice.template.TemplateService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
 *
 * <p>Spec 05-059: the client's Idempotency-Key is checked before anything else. A repeat (same key,
 * same body) answers with the send already recorded, whatever changed since; the same key with a
 * different body is rejected. Only a new request goes through the checks and is recorded, in its
 * own transaction: when a concurrent request with the same key wins the unique index, this one
 * answers as a repeat of it.
 */
@Service
public class EmailSendService {

    private final ClientSenderRepository senderRepository;
    private final EmailSendRepository sendRepository;
    private final TemplateService templateService;
    private final RecipientDomainVerifier recipientDomainVerifier;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public EmailSendService(ClientSenderRepository senderRepository, EmailSendRepository sendRepository,
                            TemplateService templateService, RecipientDomainVerifier recipientDomainVerifier,
                            ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.senderRepository = senderRepository;
        this.sendRepository = sendRepository;
        this.templateService = templateService;
        this.recipientDomainVerifier = recipientDomainVerifier;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public UUID send(String clientId, String idempotencyKey, SendEmailRequest request) {
        Map<String, Object> templateData = request.getTemplateData() == null ? Map.of() : request.getTemplateData();
        String requestHash = requestHash(request.getTemplateName(), request.getRecipientEmail(), templateData);

        Optional<UUID> repeated = repeatOf(clientId, idempotencyKey, requestHash);
        if (repeated.isPresent()) {
            return repeated.get();
        }
        try {
            return transactionTemplate.execute(status -> record(clientId, idempotencyKey, requestHash, request, templateData));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key was recorded first: this one is a repeat of it.
            return repeatOf(clientId, idempotencyKey, requestHash).orElseThrow(() -> e);
        }
    }

    private Optional<UUID> repeatOf(String clientId, String idempotencyKey, String requestHash) {
        return sendRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
                .map(existing -> {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyKeyReusedException();
                    }
                    return existing.getId();
                });
    }

    private UUID record(String clientId, String idempotencyKey, String requestHash, SendEmailRequest request,
                        Map<String, Object> templateData) {
        ClientSender sender = senderRepository.findById(clientId).orElseThrow(SenderNotConfiguredException::new);
        TemplateReference template = templateService.reference(clientId, request.getTemplateName());
        recipientDomainVerifier.verify(request.getRecipientEmail());

        EmailSend send = sendRepository.saveAndFlush(new EmailSend(clientId, idempotencyKey, requestHash, template.id(),
                request.getRecipientEmail(), sender.getAddress(), template.sesTemplateName(),
                objectMapper.writeValueAsString(templateData), Instant.now()));
        return send.getId();
    }

    /** SHA-256 of the body, map keys sorted so the same templateData hashes the same in any order. */
    private String requestHash(String templateName, String recipientEmail, Map<String, Object> templateData) {
        Map<String, Object> body = new TreeMap<>();
        body.put("templateName", templateName);
        body.put("recipientEmail", recipientEmail);
        body.put("templateData", templateData);
        String json = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(body);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
