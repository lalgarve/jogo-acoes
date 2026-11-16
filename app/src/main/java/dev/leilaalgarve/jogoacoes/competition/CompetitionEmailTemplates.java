package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.email.EmailTemplateSource;
import dev.leilaalgarve.jogoacoes.email.TemplateDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/** The templates of {@link CompetitionEmail}, from {@code email-templates/competition/} (spec 05-061). */
@Component
public class CompetitionEmailTemplates implements EmailTemplateSource {

    @Override
    public List<TemplateDefinition> templates() {
        return TemplateDefinition.fromClasspath("email-templates/competition",
                "invite", "registration-link", "login-link-invite", "login-link-request");
    }
}
