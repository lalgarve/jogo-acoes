package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.loginsecurity.SecurityConfigContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

@Component
public class CompetitionSecurityConfigContributor implements SecurityConfigContributor {

    @Override
    public void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers("/competitions/*/entry-requests", "/competitions/public").permitAll();
        registry.requestMatchers(HttpMethod.POST, "/competitions").hasRole("ADMINISTRATOR");
        registry.requestMatchers("/competitions/*/invite-emails", "/competitions/*/players", "/competitions/*/players/**")
                .hasRole("ADMINISTRATOR");
        // Spec 05-038: looking up by e-mail is administrator-only; GET /players/{userId} only
        // needs a session (the own-profile rule is in PlayerDirectoryService).
        registry.requestMatchers(HttpMethod.GET, "/players").hasRole("ADMINISTRATOR");
        // Spec 05-039: searching players is administrator-only.
        registry.requestMatchers(HttpMethod.POST, "/players/search").hasRole("ADMINISTRATOR");
    }
}
