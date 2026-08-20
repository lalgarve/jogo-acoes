package io.deployo.jogoacoes.email.lambda;

/**
 * The SQS command-queue message contract (docs/iteracao-4.md, decision 1). Mirrors the shape
 * the main app's future EmailSender implementation publishes -- duplicated here rather than
 * shared through a module, since it's five fields and not worth the extra module for that.
 * Deliberately just recipient/subject/body: no EmailTemplate, no link, no domain concept
 * crosses the queue, so this contract never needs to change when the app's template catalog
 * does.
 */
public record EmailMessage(String schemaVersion, String correlationId, String recipientEmail, String subject, String body) {
}
