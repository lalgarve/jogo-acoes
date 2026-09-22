package dev.leilaalgarve.jogoacoes.blackbox;

import dev.leilaalgarve.jogoacoes.captcha.CaptchaVerifier;
import dev.leilaalgarve.jogoacoes.email.EmailTemplate;
import dev.leilaalgarve.jogoacoes.email.SentEmail;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.login.UserRoleRepository;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-014: proves the three blackbox-only mechanisms actually work together, activating the
 * real profile instead of just trusting the wiring reads correctly -- same spirit as {@link
 * dev.leilaalgarve.jogoacoes.common.logging.ProductionProfileSuppressesLoggingAspectsTest}
 * activating {@code production}. Never runs under any other profile ({@code
 * ArchitectureTest}/{@code OpenApiRoutesConsistencyTest}/{@code OpenApiRolesConsistencyTest}
 * cover that these mechanisms stay invisible elsewhere).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("blackbox")
class BlackboxProfileIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private CaptchaVerifier captchaVerifier;

    @Autowired
    private BlackboxDataSeeder blackboxDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SentEmailRepository sentEmailRepository;

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

    @Test
    void lastEmailReturnsTheMostRecentLinkSentToAnAddress() {
        String email = unique("blackbox-test");
        saveSentEmail(email, "https://jogo-acoes.example/older", LocalDateTime.now().minusMinutes(5));
        saveSentEmail(email, "https://jogo-acoes.example/newer", LocalDateTime.now());

        RestAssured.given().port(port).basePath("/api")
                .when().get("/blackbox/last-email?email={email}", email)
                .then().statusCode(200)
                .body("link", org.hamcrest.Matchers.equalTo("https://jogo-acoes.example/newer"));
    }

    @Test
    void lastEmailReturnsNotFoundWhenNothingWasSentToTheAddress() {
        RestAssured.given().port(port).basePath("/api")
                .when().get("/blackbox/last-email?email={email}", unique("never-sent"))
                .then().statusCode(404);
    }

    private void saveSentEmail(String email, String link, LocalDateTime sentAt) {
        SentEmail sentEmail = new SentEmail();
        sentEmail.setEmail(email);
        sentEmail.setLink(link);
        sentEmail.setTemplate(EmailTemplate.LOGIN_LINK);
        sentEmail.setSentAt(sentAt);
        sentEmailRepository.save(sentEmail);
    }
}
