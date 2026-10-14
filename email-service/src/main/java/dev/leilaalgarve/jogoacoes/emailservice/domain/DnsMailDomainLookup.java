package dev.leilaalgarve.jogoacoes.emailservice.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Asks DNS whether a domain accepts e-mail (spec 05-047): MX first; a domain that exists without
 * MX is checked for an address (A, then AAAA -- RFC 5321's implicit MX). A null MX (RFC 7505: a
 * single MX pointing at the root) means the domain declares it takes no mail.
 *
 * <p>{@code email.domain-check.dns-servers} lists {@code host:port} servers; empty means the
 * system resolver (staging/production). In docker it is the CoreDNS test zone. Nothing is cached
 * here: an answer is either stored in the whitelist or asked again next time.
 */
@Component
public class DnsMailDomainLookup {

    private final Resolver resolver;

    public DnsMailDomainLookup(@Value("${email.domain-check.dns-servers:}") List<String> dnsServers,
                               @Value("${email.domain-check.timeout:2s}") Duration timeout) {
        this.resolver = resolver(dnsServers, timeout);
    }

    /** {@code domain} is already lower case and ASCII (see {@link RecipientDomainVerifier}). */
    public MailDomainCheck check(String domain) {
        Name name;
        try {
            name = Name.fromString(domain, Name.root);
        } catch (TextParseException e) {
            return new MailDomainCheck.Rejected("not a domain name");
        }

        Lookup mx = lookup(name, Type.MX);
        switch (mx.getResult()) {
            case Lookup.SUCCESSFUL -> {
                boolean onlyNullMx = Arrays.stream(mx.getAnswers())
                        .allMatch(answer -> ((MXRecord) answer).getTarget().equals(Name.root));
                return onlyNullMx
                        ? new MailDomainCheck.Rejected("null MX")
                        : new MailDomainCheck.Approved("MX");
            }
            case Lookup.HOST_NOT_FOUND -> {
                return new MailDomainCheck.Rejected("domain does not exist");
            }
            case Lookup.TYPE_NOT_FOUND -> {
                return checkAddress(name);
            }
            default -> {
                return new MailDomainCheck.Unavailable("MX lookup: " + mx.getErrorString());
            }
        }
    }

    private MailDomainCheck checkAddress(Name name) {
        for (int type : new int[] {Type.A, Type.AAAA}) {
            Lookup address = lookup(name, type);
            switch (address.getResult()) {
                case Lookup.SUCCESSFUL -> {
                    return new MailDomainCheck.Approved(Type.string(type));
                }
                case Lookup.TYPE_NOT_FOUND, Lookup.HOST_NOT_FOUND -> {
                    // try the next address type
                }
                default -> {
                    return new MailDomainCheck.Unavailable(Type.string(type) + " lookup: " + address.getErrorString());
                }
            }
        }
        return new MailDomainCheck.Rejected("no MX nor address record");
    }

    private Lookup lookup(Name name, int type) {
        Lookup lookup = new Lookup(name, type);
        lookup.setResolver(resolver);
        lookup.setCache(null);
        lookup.run();
        return lookup;
    }

    private static Resolver resolver(List<String> dnsServers, Duration timeout) {
        List<String> servers = dnsServers.stream().filter(server -> !server.isBlank()).toList();
        ExtendedResolver resolver;
        if (servers.isEmpty()) {
            resolver = new ExtendedResolver();
        } else {
            resolver = new ExtendedResolver(servers.stream().map(DnsMailDomainLookup::simpleResolver)
                    .toArray(Resolver[]::new));
        }
        resolver.setTimeout(timeout);
        resolver.setRetries(1);
        return resolver;
    }

    private static Resolver simpleResolver(String server) {
        int colon = server.lastIndexOf(':');
        String host = colon < 0 ? server : server.substring(0, colon);
        int port = colon < 0 ? 53 : Integer.parseInt(server.substring(colon + 1));
        return new SimpleResolver(new InetSocketAddress(host, port));
    }
}
