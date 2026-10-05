package dev.leilaalgarve.jogoacoes.emailservice.auth;

import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult;
import dev.leilaalgarve.apikey.validation.ApiKeyValidator;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.Error;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Validates {@code X-API-Key} with the api-key library on every request (spec 05-030,
 * alternative A of its HTTP integration guide). A valid key's client name goes into the
 * {@link #CLIENT_NAME_ATTRIBUTE} request attribute, which {@link ClientIdentityResolver} reads.
 *
 * <p>Every rejection answers the same 401 body, whatever the reason (plan.md, "Política de
 * resposta"): a caller never learns whether a key existed once. The exact reason is logged, the
 * key never is.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String CLIENT_NAME_ATTRIBUTE = "apiKey.clientName";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final ApiKeyValidator validator;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(ApiKeyValidator validator, ObjectMapper objectMapper) {
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        switch (validator.validate(request.getHeader(API_KEY_HEADER))) {
            case ApiKeyValidationResult.Valid valid -> {
                request.setAttribute(CLIENT_NAME_ATTRIBUTE, valid.clientName());
                filterChain.doFilter(request, response);
            }
            case ApiKeyValidationResult.Invalid invalid -> {
                log.info("Rejected {} {}: API key {}", request.getMethod(), request.getRequestURI(), invalid.reason());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(), new Error().message("Missing or invalid X-API-Key"));
            }
        }
    }
}
