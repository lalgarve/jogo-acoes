package dev.leilaalgarve.jogoacoes.email.lambda;

import java.util.Map;

/**
 * The SQS send-queue message contract (spec 05-031, plan.md "Contrato da mensagem da fila"),
 * published by email-service's {@code EmailQueueMessage} -- duplicated here rather than shared
 * through a module, since it's six fields and not worth the extra module for that.
 * {@code templateName} is a template already on SES (its namespaced name there) and
 * {@code templateData} its variables: SES renders the e-mail at send time, so nothing here is
 * rendered, and no domain concept crosses the queue. The old rendered subject/body shape is gone,
 * without a new schemaVersion: the system is in pre-production.
 */
public record EmailMessage(String schemaVersion, String correlationId, String senderAddress, String recipientEmail,
                           String templateName, Map<String, Object> templateData) {
}
