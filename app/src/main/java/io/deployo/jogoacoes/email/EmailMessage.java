package io.deployo.jogoacoes.email;

/**
 * The SQS command-queue message contract (docs/context/iteracao-4.md, decisão 1) — producer-side
 * copy. email-lambda has its own identical copy on the consumer side; kept as two separate records,
 * not a shared module, so a future change to either side never forces a cross-module release.
 */
public record EmailMessage(String schemaVersion, String correlationId, String recipientEmail, String subject,
                            String body) {
}
