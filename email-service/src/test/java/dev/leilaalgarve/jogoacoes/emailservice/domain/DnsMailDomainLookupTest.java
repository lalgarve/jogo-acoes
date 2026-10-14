package dev.leilaalgarve.jogoacoes.emailservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-047: one test per outcome, against docker-compose.yml's real CoreDNS test zone
 * (docker/dns/test.zone) on its host port -- no mocked resolver.
 */
class DnsMailDomainLookupTest {

    private final DnsMailDomainLookup lookup =
            new DnsMailDomainLookup(List.of("localhost:1053"), Duration.ofSeconds(1));

    @Test
    void aDomainWithMxIsApprovedByMx() {
        assertThat(lookup.check("with-mx.test")).isEqualTo(new MailDomainCheck.Approved("MX"));
    }

    @Test
    void aDomainWithoutMxButWithAnIpv4AddressIsApprovedByA() {
        assertThat(lookup.check("only-a.test")).isEqualTo(new MailDomainCheck.Approved("A"));
    }

    @Test
    void aDomainWithOnlyAnIpv6AddressIsApprovedByAaaa() {
        assertThat(lookup.check("only-aaaa.test")).isEqualTo(new MailDomainCheck.Approved("AAAA"));
    }

    @Test
    void aNullMxRejectsTheDomainEvenWithAnAddress() {
        assertThat(lookup.check("null-mx.test")).isEqualTo(new MailDomainCheck.Rejected("null MX"));
    }

    @Test
    void aDomainThatDoesNotExistIsRejected() {
        assertThat(lookup.check("missing.test")).isEqualTo(new MailDomainCheck.Rejected("domain does not exist"));
    }

    @Test
    void aDomainWithoutMxNorAddressIsRejected() {
        assertThat(lookup.check("no-mail.test")).isEqualTo(new MailDomainCheck.Rejected("no MX nor address record"));
    }

    @Test
    void aNameThatIsNotADomainIsRejected() {
        assertThat(lookup.check("a..b")).isEqualTo(new MailDomainCheck.Rejected("not a domain name"));
    }

    @Test
    void aServerThatNeverAnswersIsUnavailableWithinTheTimeout() {
        long start = System.nanoTime();

        MailDomainCheck check = lookup.check("unavailable.test");

        assertThat(check).isInstanceOf(MailDomainCheck.Unavailable.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void aZoneTheServerDoesNotServeIsUnavailable() {
        // CoreDNS answers REFUSED outside its zones: not a "no", so the send is not rejected.
        assertThat(lookup.check("gmail.com")).isInstanceOf(MailDomainCheck.Unavailable.class);
    }
}
