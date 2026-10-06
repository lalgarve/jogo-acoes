package dev.leilaalgarve.jogoacoes.emailservice.send;

import java.util.Map;

/**
 * The send queue's message contract (spec 05-031, plan.md "Contrato da mensagem da fila"), read by
 * email-lambda's own {@code EmailMessage} -- duplicated there rather than shared through a module,
 * same choice app's {@code EmailMessage} made. {@code templateName} is the namespaced name on SES;
 * SES renders it with {@code templateData} at send time, so nothing here is rendered.
 */
public record EmailQueueMessage(String schemaVersion, String correlationId, String senderAddress,
                                String recipientEmail, String templateName, Map<String, Object> templateData) {

    public static final String SCHEMA_VERSION = "1";
}
