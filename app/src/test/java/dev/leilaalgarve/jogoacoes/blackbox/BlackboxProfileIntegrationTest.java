package dev.leilaalgarve.jogoacoes.blackbox;

import dev.leilaalgarve.jogoacoes.captcha.CaptchaVerifier;
import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.login.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-014: proves the blackbox-only mechanisms actually work together, activating the
 * real profile instead of just trusting the wiring reads correctly -- same spirit as {@link
 * dev.leilaalgarve.jogoacoes.common.logging.ProductionProfileSuppressesLoggingAspectsTest}
 * activating {@code production}. Never runs under any other profile ({@code
 * ArchitectureTest}/{@code OpenApiRoutesConsistencyTest}/{@code OpenApiRolesConsistencyTest}
 * cover that these mechanisms stay invisible elsewhere).
 *
 * <p>Used to also cover {@code GET /blackbox/last-email} -- removed along with
 * {@code BlackboxController} (spec 05-023): reading a sent e-mail's link is only ever needed by
 * the Python blackbox suite, which now reads LocalStack's own SES message store directly
 * (`common/blackbox_fixtures.py`), no Java code involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("blackbox")
class BlackboxProfileIntegrationTest {

    @Autowired
    private CaptchaVerifier captchaVerifier;

    @Autowired
    private BlackboxDataSeeder blackboxDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void captchaIsAlwaysAccepted() {
        assertThat(captchaVerifier.verify(null)).isTrue();
        assertThat(captchaVerifier.verify("")).isTrue();
        assertThat(captchaVerifier.verify("anything")).isTrue();
    }

    @Test
    void administratorIsSeededIdempotently() {
        var admin = userRepository.findByEmail(BlackboxDataSeeder.ADMIN_EMAIL).orElseThrow();
        assertThat(userRoleRepository.findByUser_Id(admin.getId()))
                .anyMatch(userRole -> userRole.getRole().getName().equals(RoleName.ADMINISTRATOR));

        long usersBefore = userRepository.count();
        blackboxDataSeeder.run(null);

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }
}
