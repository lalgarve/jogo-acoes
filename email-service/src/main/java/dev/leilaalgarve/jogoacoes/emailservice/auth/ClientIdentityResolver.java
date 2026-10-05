package dev.leilaalgarve.jogoacoes.emailservice.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * The client that owns the templates of the current request: the client name the presented API
 * key was issued for (the api-key CLI's {@code --client}, spec 05-030), already validated by
 * {@link ApiKeyAuthenticationFilter} before any controller runs. Never the key text, so rotating
 * a client's key keeps its templates.
 */
@Component
@RequestScope
public class ClientIdentityResolver {

    private final HttpServletRequest request;

    public ClientIdentityResolver(HttpServletRequest request) {
        this.request = request;
    }

    public String currentClientId() {
        return (String) request.getAttribute(ApiKeyAuthenticationFilter.CLIENT_NAME_ATTRIBUTE);
    }
}
