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
 *
 * <p>LocalStack sends the Subject header as raw UTF-8 instead (no encoded-word), which Jakarta
 * Mail reads as ISO-8859-1 by default; {@code mail.mime.allowutf8} reads it as UTF-8 and still
 * decodes RFC 2047 (spec 05-051). {@link Session#getInstance} rather than
 * {@code getDefaultInstance}, which keeps the JVM's first session and ignores later properties.
 */
@Component
public class MimeRenderedTemplateParser {

    public RenderedTemplate parse(String rawMime) {
        try {
            Properties properties = new Properties();
            properties.setProperty("mail.mime.allowutf8", "true");
            Session session = Session.getInstance(properties);
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
