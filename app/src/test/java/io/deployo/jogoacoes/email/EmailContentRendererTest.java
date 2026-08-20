package io.deployo.jogoacoes.email;

import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.RequestType;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the real 5 templates under src/main/resources/templates/email -- exercises the
 * origem+conta selection matrix from docs/iteracao-4.md, "Catálogo de templates de e-mail".
 */
class EmailContentRendererTest {

    private final EmailContentRenderer renderer = new EmailContentRenderer(newTemplateEngine());

    @Test
    void selectsInviteForAnAdminInviteWithNoAccount() {
        RenderedEmail email = renderer.render(new EmailRequest(null, "bob@example.com", null, "Copa Verão",
                RequestType.INVITE, "https://jogo-acoes.example/login-links/abc", EmailTemplate.INVITE));

        assertThat(email.subject()).isEqualTo("Convite para competir em Copa Verão");
        assertThat(email.body()).contains("Copa Verão", "https://jogo-acoes.example/login-links/abc");
    }

    @Test
    void selectsRegistrationLinkForASpontaneousRequestWithNoAccount() {
        RenderedEmail email = renderer.render(new EmailRequest(null, "bob@example.com", null, "Copa Verão",
                RequestType.REQUEST, "https://jogo-acoes.example/login-links/abc", EmailTemplate.REGISTRATION_LINK));

        assertThat(email.subject()).isEqualTo("Finalize seu cadastro em Copa Verão");
    }

    @Test
    void selectsStandaloneLoginLinkWhenThereIsNoCompetition() {
        RenderedEmail email = renderer.render(new EmailRequest(1L, "alice@example.com", "Alice", null, null,
                "https://jogo-acoes.example/login-links/abc", EmailTemplate.LOGIN_LINK));

        assertThat(email.subject()).isEqualTo("Seu link de acesso");
        assertThat(email.body()).contains("Alice");
    }

    @Test
    void selectsLoginLinkInviteWhenAnAlreadyRegisteredPlayerIsInvited() {
        RenderedEmail email = renderer.render(new EmailRequest(1L, "alice@example.com", "Alice", "Copa Verão",
                RequestType.INVITE, "https://jogo-acoes.example/login-links/abc", EmailTemplate.LOGIN_LINK));

        assertThat(email.subject()).isEqualTo("Convite para competir em Copa Verão");
        assertThat(email.body()).contains("Alice", "já tem uma conta");
    }

    @Test
    void selectsLoginLinkRequestWhenAnAlreadyRegisteredPlayerRequestsEntry() {
        RenderedEmail email = renderer.render(new EmailRequest(1L, "alice@example.com", "Alice", "Copa Verão",
                RequestType.REQUEST, "https://jogo-acoes.example/login-links/abc", EmailTemplate.LOGIN_LINK));

        assertThat(email.subject()).isEqualTo("Acesse Copa Verão");
        assertThat(email.body()).contains("Alice", "Recebemos seu pedido");
    }

    // SpringTemplateEngine (not a plain TemplateEngine), matching production: it registers
    // SpringStandardDialect, which uses SpEL. Plain TemplateEngine defaults to the vanilla
    // StandardDialect instead, which needs OGNL -- not a dependency here (real finding, from
    // actually running this test: NoClassDefFoundError: ognl/PropertyAccessor).
    private static TemplateEngine newTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
