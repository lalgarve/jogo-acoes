package dev.leilaalgarve.jogoacoes.email.client;

/**
 * One of app's e-mail templates as registered in email-service (spec 05-034): SES Handlebars
 * syntax, {@code {{variables}}} left for SES to substitute at send time.
 */
public record EmailServiceTemplate(String name, String subject, String body) {
}
