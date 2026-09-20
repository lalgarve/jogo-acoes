package dev.leilaalgarve.jogoacoes.common.testsupport;

import java.util.UUID;

/**
 * Spec 05-016 -- every "valid test e-mail" address used by the automated suite goes through
 * the Amazon SES mailbox simulator instead of {@code @example.com}: {@code success+anything@
 * simulator.amazonses.com} always produces a simulated successful delivery (plus addressing,
 * RFC 5233 -- the text after {@code +} is just a preserved identifier), so a test address
 * behaves the same whether it's intercepted by LocalStack/StubEmailSender (today) or ever
 * reaches the real SES.
 */
public final class TestEmails {

    private static final String DOMAIN = "@simulator.amazonses.com";

    private TestEmails() {
    }

    /** A unique address per call -- same role the "-" + UUID suffix already played against example.com. */
    public static String unique(String qualifier) {
        return "success+" + qualifier + "-" + UUID.randomUUID() + DOMAIN;
    }

    /** A fixed, non-unique address -- for tests that don't need uniqueness across runs. */
    public static String fixed(String qualifier) {
        return "success+" + qualifier + DOMAIN;
    }
}
