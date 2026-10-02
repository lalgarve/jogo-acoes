package dev.leilaalgarve.jogoacoes.emailservice.template;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SES's {@code TestRenderTemplate} returns the rendered e-mail as a raw MIME string (headers
 * included), not {subject, body} already split apart (plan.md, verified against AWS's own
 * docs) -- parsed here with Jakarta Mail instead of hand-rolled parsing, since
 * {@link MimeMessage#getSubject()} also decodes RFC 2047 encoded-word syntax for non-ASCII
 * subjects.
 */
@Component
public class MimeRenderedTemplateParser {

    public RenderedTemplate parse(String rawMime) {
        try {
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage message =
                    new MimeMessage(session, new ByteArrayInputStream(rawMime.getBytes(StandardCharsets.UTF_8)));
            return new RenderedTemplate(message.getSubject(), extractBody(message));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse SES TestRenderTemplate response as MIME", e);
        }
    }

    private String extractBody(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                body.append(multipart.getBodyPart(i).getContent());
            }
            return body.toString();
        }
        return String.valueOf(content);
    }

    public record RenderedTemplate(String subject, String body) {
    }
}
