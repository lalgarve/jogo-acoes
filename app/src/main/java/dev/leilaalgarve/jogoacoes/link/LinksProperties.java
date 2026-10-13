package dev.leilaalgarve.jogoacoes.link;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Spec 05-041: the base every link sent by e-mail starts with -- an absolute http(s) URL that
 * already includes the API's context path (e.g. http://localhost:8080/api), set per environment
 * through LINKS_BASE_URL. Validated at startup: a missing or relative base stops the app instead
 * of sending e-mails whose links can't be opened.
 */
@Validated
@ConfigurationProperties("links")
public record LinksProperties(@NotBlank @Pattern(regexp = "^https?://\\S+$") String baseUrl) {

    /** The URL of {@code GET /login-links/{token}}, the route that consumes every link. */
    public String loginLinkUrl(String token) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/login-links/" + token;
    }
}
