package dev.leilaalgarve.jogoacoes.emailservice.auth;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.Error;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;

/**
 * Spec 05-048: each route requires its permission (api-key 1.1.0 catalog in application.yml) --
 * {@value #EMAIL_SEND} for sending and templates, {@value #DISPOSABLE_EMAIL_ADMIN} for the
 * disposable-domain lists. The library only tells which permissions a valid key has
 * ({@link ApiKeyAuthenticationFilter} keeps them in {@link ApiKeyAuthenticationFilter#PERMISSIONS_ATTRIBUTE});
 * this service decides. A valid key without the route's permission gets 403.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    public static final String EMAIL_SEND = "email:send";
    public static final String DISPOSABLE_EMAIL_ADMIN = "disposable-email:admin";

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    private final ObjectMapper objectMapper;

    public PermissionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** The one place that maps a route to its permission; null for a route that needs none. */
    static String requiredPermission(String path) {
        if (path.equals("/emails") || path.equals("/templates") || path.startsWith("/templates/")) {
            return EMAIL_SEND;
        }
        if (path.startsWith("/disposable-domain-")) {
            return DISPOSABLE_EMAIL_ADMIN;
        }
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String required = requiredPermission(request.getServletPath());
        @SuppressWarnings("unchecked")
        Set<String> granted = (Set<String>) request.getAttribute(ApiKeyAuthenticationFilter.PERMISSIONS_ATTRIBUTE);
        if (required == null || (granted != null && granted.contains(required))) {
            return true;
        }
        log.info("Refused {} {} to client {}: missing permission {}", request.getMethod(), request.getRequestURI(),
                request.getAttribute(ApiKeyAuthenticationFilter.CLIENT_NAME_ATTRIBUTE), required);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new Error().message("Missing permission: " + required));
        return false;
    }
}
