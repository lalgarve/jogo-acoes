package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One line of a list into the domain the table keeps (spec 05-048): trimmed, lower case, in the
 * ASCII form of an internationalized domain. Blank lines and comments ({@code #}) are left out
 * ({@code null}, Spring Batch's filter); a line that is not a domain throws
 * {@link InvalidDisposableDomainLineException}, which the step skips and counts.
 */
public class DisposableDomainLineProcessor implements ItemProcessor<String, String> {

    private static final Logger log = LoggerFactory.getLogger(DisposableDomainLineProcessor.class);

    private static final int MAX_DOMAIN_LENGTH = 253;
    /** 1 to 63 letters, digits or hyphens, not starting or ending with a hyphen. */
    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    @Override
    public String process(String line) {
        // A list saved with a byte order mark carries it on the first line.
        String trimmed = line.replace("﻿", "").strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        String domain = toAscii(trimmed.toLowerCase(Locale.ROOT));
        if (domain == null || !isDomain(domain)) {
            log.warn("Skipped a line that is not a domain: {}", trimmed);
            throw new InvalidDisposableDomainLineException(trimmed);
        }
        return domain;
    }

    private static String toAscii(String domain) {
        try {
            return IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isDomain(String domain) {
        if (domain.length() > MAX_DOMAIN_LENGTH || !domain.contains(".")) {
            return false;
        }
        for (String label : domain.split("\\.", -1)) {
            if (!LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }
}
