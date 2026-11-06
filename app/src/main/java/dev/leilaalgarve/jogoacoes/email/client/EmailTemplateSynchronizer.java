package dev.leilaalgarve.jogoacoes.email.client;

import dev.leilaalgarve.jogoacoes.email.EmailTemplateSource;
import dev.leilaalgarve.jogoacoes.email.TemplateDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers app's e-mail templates in email-service when app starts (spec 05-034): app decides
 * what its e-mails say, email-service stores them and syncs them with SES. Upsert, so restarting
 * app (or two instances starting together) ends in the same state. A failure stops app from
 * becoming ready, instead of the first e-mail failing later.
 *
 * <p>Each client module declares its own templates through an {@link EmailTemplateSource}
 * (spec 05-061); this class only knows {@code email-templates/layout.html}, the header and footer
 * shared by every e-mail (SES has no partials), and pastes each template's content into its slot.
 */
@Component
@ConditionalOnProperty("email-service.base-url")
class EmailTemplateSynchronizer {

    private static final String LAYOUT = "email-templates/layout.html";
    private static final String CONTENT_SLOT = "{{!-- content --}}";

    private final EmailServiceGateway gateway;
    private final List<EmailTemplateSource> sources;

    EmailTemplateSynchronizer(EmailServiceGateway gateway, List<EmailTemplateSource> sources) {
        this.gateway = gateway;
        this.sources = sources;
    }

    @EventListener(ApplicationReadyEvent.class)
    void synchronize() {
        templates().forEach(gateway::upsertTemplate);
    }

    /**
     * Every source's templates inside the layout.
     *
     * @throws IllegalStateException when the layout has no content slot, or two sources declare
     *                               the same template name (one would silently replace the other)
     */
    List<EmailServiceTemplate> templates() {
        String layout = TemplateDefinition.withoutLeadingComment(TemplateDefinition.read(LAYOUT));
        if (!layout.contains(CONTENT_SLOT)) {
            throw new IllegalStateException(LAYOUT + " has no " + CONTENT_SLOT + " slot");
        }
        Map<String, EmailTemplateSource> owners = new HashMap<>();
        List<EmailServiceTemplate> templates = new ArrayList<>();
        for (EmailTemplateSource source : sources) {
            for (TemplateDefinition definition : source.templates()) {
                EmailTemplateSource owner = owners.putIfAbsent(definition.name(), source);
                if (owner != null) {
                    throw new IllegalStateException("E-mail template " + definition.name() + " is declared by both "
                            + owner.getClass().getName() + " and " + source.getClass().getName());
                }
                templates.add(new EmailServiceTemplate(definition.name(), definition.subject(),
                        layout.replace(CONTENT_SLOT, definition.content())));
            }
        }
        return templates;
    }
}
