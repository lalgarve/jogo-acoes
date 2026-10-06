package dev.leilaalgarve.jogoacoes.emailservice.template;

/**
 * What sending an e-mail needs from a client's template (spec 05-031): its row id, recorded with
 * the send, and its name on SES, which renders it at send time. Keeps the template's entity and
 * repository inside this package.
 */
public record TemplateReference(Long id, String sesTemplateName) {
}
