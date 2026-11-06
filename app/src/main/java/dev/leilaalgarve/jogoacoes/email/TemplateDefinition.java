package dev.leilaalgarve.jogoacoes.email;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * One template a client declares (spec 05-061): its name, subject and the content that goes into
 * the shared layout's slot. SES Handlebars syntax, {@code {{variables}}} left for SES.
 */
public record TemplateDefinition(String name, String subject, String content) {

    /**
     * Reads {@code <directory>/<name>.subject.txt} and {@code <directory>/<name>.html} from the
     * classpath for each name; the source files' own documentation comment isn't part of the e-mail.
     */
    public static List<TemplateDefinition> fromClasspath(String directory, String... names) {
        return Arrays.stream(names)
                .map(name -> new TemplateDefinition(name,
                        read(directory + "/" + name + ".subject.txt").strip(),
                        withoutLeadingComment(read(directory + "/" + name + ".html")).stripTrailing()))
                .toList();
    }

    /** Also used for the layout, which carries the same kind of comment. */
    public static String withoutLeadingComment(String html) {
        String trimmed = html.stripLeading();
        if (!trimmed.startsWith("<!--")) {
            return html;
        }
        return trimmed.substring(trimmed.indexOf("-->") + "-->".length()).stripLeading();
    }

    public static String read(String file) {
        try {
            return new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing e-mail template source " + file, e);
        }
    }
}
