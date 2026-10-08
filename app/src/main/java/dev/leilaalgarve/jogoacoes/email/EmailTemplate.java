package dev.leilaalgarve.jogoacoes.email;

/**
 * Which e-mail was sent; labels the row in {@link SentEmail}. The text itself is one of the
 * templates app registers in email-service (spec 05-034), picked by EmailServiceEmailSender.
 */
public enum EmailTemplate {
    INVITE,
    REGISTRATION_LINK,
    LOGIN_LINK
}
