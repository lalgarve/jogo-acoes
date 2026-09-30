package dev.leilaalgarve.jogoacoes.common;

import dev.leilaalgarve.jogoacoes.login.SecurityConfigContributor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * Opens up the interactive Swagger UI (spec 05-009) -- the static {@code docs/openapi.yaml}
 * contract it reads (app/pom.xml copies it to {@code static/openapi.yaml} at build time) plus
 * springdoc's own UI assets. Not owned by any single domain module, unlike the other
 * {@link SecurityConfigContributor}s (spec 05-006).
 */
@Component
public class SwaggerUiSecurityConfigContributor implements SecurityConfigContributor {

    @Override
    public void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        // The bundled swagger-ui.html always fetches /v3/api-docs/swagger-config on load (even
        // with springdoc.api-docs.enabled: false, which only empties the JSON, not the mapping)
        // -- without permitAll here it 401s and the UI shows "Failed to load remote configuration".
        registry.requestMatchers("/openapi.yaml", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
    }
}
