package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.login.SecurityConfigContributor;
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
    }
}
