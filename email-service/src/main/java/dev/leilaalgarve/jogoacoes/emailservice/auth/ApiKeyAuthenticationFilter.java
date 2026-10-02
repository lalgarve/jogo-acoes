package dev.leilaalgarve.jogoacoes.emailservice.auth;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.Error;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Esqueleto (spec 05-025, spec.md "Decisões em aberto"): só rejeita uma chave ausente/vazia,
 * sem validar formato/hash/expiração/revogação -- isso fica para quando a biblioteca de
 * validação real existir. {@link ClientIdentityResolver} é quem lê o valor que este filtro já
 * garantiu estar presente.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new Error().message("Missing or invalid X-API-Key"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
