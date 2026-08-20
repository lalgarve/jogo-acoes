package io.deployo.jogoacoes.email;

import io.deployo.jogoacoes.domain.RequestType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks which of the 5 physical templates (docs/iteracao-4.md, "Catálogo de templates de
 * e-mail") an {@link EmailRequest} maps to and renders it. This selection is deliberately Java
 * code, not template logic (`th:if`) — the templates themselves stay free of any branching, per
 * that same decision.
 *
 * <p>Convention (also documented in iteracao-4.md): the rendered {@code <title>} is the
 * subject, the rendered document is the body — both come from the same {@link TemplateEngine}
 * pass, so a dynamic subject (e.g. the competition name) is still plain variable substitution,
 * never a second template.
 */
@Component
public class EmailContentRenderer {

    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    private final TemplateEngine templateEngine;

    public EmailContentRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public RenderedEmail render(EmailRequest request) {
        String templateFile = templateFileFor(request);

        Context context = new Context();
        context.setVariable("name", request.name());
        context.setVariable("competitionName", request.competitionName());
        context.setVariable("link", request.link());

        String html = templateEngine.process(templateFile, context);
        return new RenderedEmail(extractTitle(html), html);
    }

    private static String templateFileFor(EmailRequest request) {
        return switch (request.template()) {
            case INVITE -> "email/invite";
            case REGISTRATION_LINK -> "email/registration-link";
            case LOGIN_LINK -> {
                if (request.competitionName() == null) {
                    yield "email/login-link";
                }
                yield request.origin() == RequestType.INVITE ? "email/login-link-invite" : "email/login-link-request";
            }
        };
    }

    private static String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("Rendered e-mail template has no <title>");
        }
        return HtmlUtils.htmlUnescape(matcher.group(1).trim());
    }
}
