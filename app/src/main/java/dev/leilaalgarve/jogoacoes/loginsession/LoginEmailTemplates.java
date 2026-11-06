package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.email.EmailTemplateSource;
import dev.leilaalgarve.jogoacoes.email.TemplateDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/** The template of {@link LoginLinkEmail}, from {@code email-templates/loginsession/} (spec 05-061). */
@Component
public class LoginEmailTemplates implements EmailTemplateSource {

    @Override
    public List<TemplateDefinition> templates() {
        return TemplateDefinition.fromClasspath("email-templates/loginsession", "login-link");
    }
}
