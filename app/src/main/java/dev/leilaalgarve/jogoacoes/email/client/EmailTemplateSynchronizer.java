package dev.leilaalgarve.jogoacoes.email.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Registers app's e-mail templates in email-service when app starts (spec 05-034): app decides
 * what its e-mails say, email-service stores them and syncs them with SES. Upsert, so restarting
 * app (or two instances starting together) ends in the same state. A failure stops app from
 * becoming ready, instead of the first e-mail failing later.
 *
 * <p>Sources live in {@code email-templates/}: {@code layout.html} (header and footer shared by
 * every e-mail -- SES has no partials) plus, per template, {@code <name>.html} (the content
 * pasted into the layout's slot) and {@code <name>.subject.txt}.
 */
@Component
@ConditionalOnProperty("email-service.base-url")
class EmailTemplateSynchronizer {

    /** Same names as the Thymeleaf files they replace (docs/context/iteracao-4.md, catálogo). */
    static final List<String> TEMPLATE_NAMES = List.of(
            "invite", "registration-link", "login-link", "login-link-invite", "login-link-request");

    private static final String SOURCE_DIRECTORY = "email-templates/";
    private static final String CONTENT_SLOT = "{{!-- content --}}";

    private final EmailServiceGateway gateway;

    EmailTemplateSynchronizer(EmailServiceGateway gateway) {
        this.gateway = gateway;
    }

    @EventListener(ApplicationReadyEvent.class)
    void synchronize() {
        templates().forEach(gateway::upsertTemplate);
    }

    static List<EmailServiceTemplate> templates() {
        String layout = withoutLeadingComment(read("layout.html"));
        if (!layout.contains(CONTENT_SLOT)) {
            throw new IllegalStateException(SOURCE_DIRECTORY + "layout.html has no " + CONTENT_SLOT + " slot");
        }
        return TEMPLATE_NAMES.stream()
                .map(name -> new EmailServiceTemplate(name,
                        read(name + ".subject.txt").strip(),
                        layout.replace(CONTENT_SLOT, read(name + ".html").stripTrailing())))
                .toList();
    }

    /** The source files' own documentation comment isn't part of the e-mail. */
    private static String withoutLeadingComment(String html) {
        String trimmed = html.stripLeading();
        if (!trimmed.startsWith("<!--")) {
            return html;
        }
        return trimmed.substring(trimmed.indexOf("-->") + "-->".length()).stripLeading();
    }

    private static String read(String file) {
        try {
            return new ClassPathResource(SOURCE_DIRECTORY + file).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing e-mail template source " + SOURCE_DIRECTORY + file, e);
        }
    }
}
