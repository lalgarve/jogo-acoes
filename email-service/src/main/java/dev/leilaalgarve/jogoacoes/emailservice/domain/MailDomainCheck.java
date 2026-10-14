package dev.leilaalgarve.jogoacoes.emailservice.domain;

/**
 * What DNS says about a recipient's domain (spec 05-047): it accepts e-mail (and which record
 * showed it), it does not, or DNS did not answer.
 */
public sealed interface MailDomainCheck {

    /** {@code record} is the record type that approved the domain: MX, A or AAAA. */
    record Approved(String record) implements MailDomainCheck {
    }

    record Rejected(String reason) implements MailDomainCheck {
    }

    record Unavailable(String detail) implements MailDomainCheck {
    }
}
