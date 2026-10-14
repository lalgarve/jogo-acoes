package dev.leilaalgarve.jogoacoes.emailservice.domain;

import dev.leilaalgarve.jogoacoes.emailservice.disposable.DisposableDomainChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.IDN;
import java.time.Instant;
import java.util.Locale;

/**
 * Checks that the recipient's domain accepts e-mail before a send is accepted (spec 05-047) and
 * is not a disposable e-mail domain (spec 05-048, checked first).
 * Whitelisted domains pass without DNS; any other is looked up, and an approved one joins the
 * whitelist. DNS that does not answer lets the send through without whitelisting the domain: a
 * send never fails because of infrastructure (same rule as spec 05-046). Only the domain is
 * logged, never the full address.
 */
@Service
public class RecipientDomainVerifier {

    private static final Logger log = LoggerFactory.getLogger(RecipientDomainVerifier.class);

    private final ApprovedDomainRepository approvedDomains;
    private final DnsMailDomainLookup dns;
    private final DisposableDomainChecker disposableDomains;

    public RecipientDomainVerifier(ApprovedDomainRepository approvedDomains, DnsMailDomainLookup dns,
                                   DisposableDomainChecker disposableDomains) {
        this.approvedDomains = approvedDomains;
        this.dns = dns;
        this.disposableDomains = disposableDomains;
    }

    /**
     * @throws RecipientDomainRejectedException when the domain is a disposable e-mail domain, or
     *                                          DNS says it does not accept e-mail
     */
    public void verify(String recipientEmail) {
        String domain = domainOf(recipientEmail);
        // Spec 05-048: before the whitelist, so a disposable domain is refused even if it was
        // approved by DNS before it reached a list.
        if (disposableDomains.isDisposable(domain)) {
            log.info("Rejected recipient domain {}: disposable e-mail domain", domain);
            throw RecipientDomainRejectedException.disposable(domain);
        }
        if (approvedDomains.existsById(domain)) {
            return;
        }
        switch (dns.check(domain)) {
            case MailDomainCheck.Approved approved ->
                    approvedDomains.approve(domain, approved.record(), Instant.now());
            case MailDomainCheck.Rejected rejected -> {
                log.info("Rejected recipient domain {}: {}", domain, rejected.reason());
                throw new RecipientDomainRejectedException(domain);
            }
            case MailDomainCheck.Unavailable unavailable ->
                    log.warn("DNS did not answer for recipient domain {} ({}); accepting the send without"
                            + " whitelisting it", domain, unavailable.detail());
        }
    }

    /** Lower case, ASCII form of an internationalized domain: the only form DNS knows. */
    private static String domainOf(String recipientEmail) {
        String domain = recipientEmail.substring(recipientEmail.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            log.info("Rejected recipient domain {}: not a valid internationalized domain", domain);
            throw new RecipientDomainRejectedException(domain);
        }
    }
}
