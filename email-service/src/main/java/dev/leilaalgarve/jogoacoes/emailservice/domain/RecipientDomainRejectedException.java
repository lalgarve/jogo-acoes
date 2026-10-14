package dev.leilaalgarve.jogoacoes.emailservice.domain;

/** The recipient's domain does not accept e-mail (spec 05-047): 422, nothing recorded or queued. */
public class RecipientDomainRejectedException extends RuntimeException {

    private final String domain;

    public RecipientDomainRejectedException(String domain) {
        super("Recipient domain does not accept e-mail: " + domain);
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }
}
