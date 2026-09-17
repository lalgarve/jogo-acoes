package dev.leilaalgarve.jogoacoes.login;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Implemented by each module with its own HTTP endpoints ({@code login}, {@code competition},
 * ...), never by {@code login} itself for another module's routes -- same inversion as
 * {@link dev.leilaalgarve.jogoacoes.link.LinkHandler}. Each contributor must only register
 * matchers under its own module's route prefix; {@link SecurityConfig} applies every
 * contributor and appends {@code anyRequest().authenticated()} last, so contribution order
 * between modules never matters.
 */
public interface SecurityConfigContributor {

    void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry);
}
