package dev.leilaalgarve.jogoacoes.email.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How app composes its template sources before registering them in email-service (spec 05-034)
 * -- no email-service needed. EmailServiceClientIntegrationTest covers the registration itself.
 */
class EmailTemplateSynchronizerTest {

    private final List<EmailServiceTemplate> templates = EmailTemplateSynchronizer.templates();

    @Test
    void composesOneTemplatePerEmailAppSends() {
        assertThat(templates).extracting(EmailServiceTemplate::name).containsExactly(
                "invite", "registration-link", "login-link", "login-link-invite", "login-link-request");
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
}
