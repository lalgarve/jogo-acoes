package dev.leilaalgarve.jogoacoes.emailservice.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.IDN;
import java.time.Instant;
import java.util.Locale;

/**
 * Checks that the recipient's domain accepts e-mail before a send is accepted (spec 05-047).
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

    public RecipientDomainVerifier(ApprovedDomainRepository approvedDomains, DnsMailDomainLookup dns) {
        this.approvedDomains = approvedDomains;
        this.dns = dns;
    }

    /** @throws RecipientDomainRejectedException when DNS says the domain does not accept e-mail */
    public void verify(String recipientEmail) {
        String domain = domainOf(recipientEmail);
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
