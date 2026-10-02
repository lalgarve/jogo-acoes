package dev.leilaalgarve.jogoacoes.blackbox;

import dev.leilaalgarve.jogoacoes.captcha.CaptchaVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-014: proves the blackbox-only mechanism actually works, activating the real profile
 * instead of just trusting the wiring reads correctly -- same spirit as {@link
 * dev.leilaalgarve.jogoacoes.common.logging.ProductionProfileSuppressesLoggingAspectsTest}
 * activating {@code production}. Never runs under any other profile ({@code
 * ArchitectureTest}/{@code OpenApiRoutesConsistencyTest}/{@code OpenApiRolesConsistencyTest}
 * cover that this mechanism stays invisible elsewhere).
 *
 * <p>Used to also cover {@code GET /blackbox/last-email} -- removed along with
 * {@code BlackboxController} (spec 05-023): reading a sent e-mail's link is only ever needed by
 * the Python blackbox suite, which now reads LocalStack's own SES message store directly
 * (`common/blackbox_fixtures.py`), no Java code involved.
 *
 * <p>Used to also cover the administrator being seeded idempotently -- that was
 * {@code BlackboxDataSeeder}, only active under this profile; it's now
 * {@link dev.leilaalgarve.jogoacoes.bootstrap.AdministratorBootstrap}, which runs under every
 * profile (spec 05-026) and has its own dedicated test, {@code AdministratorBootstrapTest},
 * instead of living here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("blackbox")
class BlackboxProfileIntegrationTest {

    @Autowired
    private CaptchaVerifier captchaVerifier;

    @Test
    void captchaIsAlwaysAccepted() {
        assertThat(captchaVerifier.verify(null)).isTrue();
        assertThat(captchaVerifier.verify("")).isTrue();
        assertThat(captchaVerifier.verify("anything")).isTrue();
    }
}
