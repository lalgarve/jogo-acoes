package dev.leilaalgarve.jogoacoes.emailservice.domain;

/**
 * The recipient's domain does not accept e-mail (spec 05-047), or is a disposable e-mail domain
 * (spec 05-048): 422, nothing recorded or queued. The message says which.
 */
public class RecipientDomainRejectedException extends RuntimeException {

    private final String domain;

    private RecipientDomainRejectedException(String message, String domain) {
        super(message + domain);
        this.domain = domain;
    }

    public RecipientDomainRejectedException(String domain) {
        this("Recipient domain does not accept e-mail: ", domain);
    }

    public static RecipientDomainRejectedException disposable(String domain) {
        return new RecipientDomainRejectedException("Recipient domain is a disposable e-mail domain: ", domain);
    }

    public String getDomain() {
        return domain;
    }
}
