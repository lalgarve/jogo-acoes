package dev.leilaalgarve.jogoacoes.email.client;

import dev.leilaalgarve.jogoacoes.competition.CompetitionEmailTemplates;
import dev.leilaalgarve.jogoacoes.email.EmailTemplateSource;
import dev.leilaalgarve.jogoacoes.email.TemplateDefinition;
import dev.leilaalgarve.jogoacoes.loginsession.LoginEmailTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How app composes its template sources before registering them in email-service (spec 05-034)
 * -- no email-service needed. EmailServiceClientIntegrationTest covers the registration itself.
 * The sources are the clients' own (spec 05-061).
 */
class EmailTemplateSynchronizerTest {

    private final List<EmailServiceTemplate> templates = new EmailTemplateSynchronizer(null,
            List.of(new CompetitionEmailTemplates(), new LoginEmailTemplates())).templates();

    @Test
    void composesOneTemplatePerEmailAppSends() {
        assertThat(templates).extracting(EmailServiceTemplate::name).containsExactly(
                "invite", "registration-link", "login-link-invite", "login-link-request", "login-link");
    }

    @Test
    void everyTemplateGetsTheSharedHeaderAndFooterAroundItsOwnContent() {
        assertThat(templates).allSatisfy(template -> {
            assertThat(template.body())
                    .startsWith("<!DOCTYPE html>")
                    .contains("Jogo de Ações", "Equipe Jogo de Ações", "href=\"{{link}}\"")
                    .doesNotContain("{{!-- content --}}", "<!--");
            assertThat(template.subject()).isNotBlank().doesNotContain("\n");
        });
    }

    @Test
    void subjectsKeepTheirVariablesForSesToSubstitute() {
        assertThat(templates).filteredOn(template -> template.name().equals("registration-link"))
                .extracting(EmailServiceTemplate::subject)
                .containsExactly("Finalize seu cadastro em {{competitionName}}");
    }

    /** Spec 05-061: two clients with the same template name would silently overwrite each other. */
    @Test
    void twoSourcesDeclaringTheSameTemplateNameStopTheStartup() {
        EmailTemplateSource another = () -> List.of(new TemplateDefinition("login-link", "Outro", "<p>outro</p>"));
        EmailTemplateSynchronizer synchronizer = new EmailTemplateSynchronizer(null,
                List.of(new LoginEmailTemplates(), another));

        assertThatThrownBy(synchronizer::templates)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login-link")
                .hasMessageContaining(LoginEmailTemplates.class.getName());
    }
}
