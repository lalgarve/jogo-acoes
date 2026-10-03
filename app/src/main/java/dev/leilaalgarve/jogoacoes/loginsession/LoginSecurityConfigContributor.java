package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.loginsecurity.SecurityConfigContributor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

@Component
public class LoginSecurityConfigContributor implements SecurityConfigContributor {

    @Override
    public void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers("/login-requests", "/login-links/**").permitAll();
    }
}
