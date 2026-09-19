package dev.leilaalgarve.jogoacoes.blackbox;

import dev.leilaalgarve.jogoacoes.login.SecurityConfigContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * Opens up {@link BlackboxController}'s route -- satisfies
 * {@code ArchitectureTest.everyModuleWithARestControllerHasASecurityConfigContributor}, and
 * this bean itself is only ever registered when {@link BlackboxController} is (both live in
 * this package, both gated by {@code @Profile("blackbox")}), so there's no risk of this
 * contributor opening a route in a profile where the controller doesn't even exist.
 */
@Component
@Profile("blackbox")
public class BlackboxSecurityConfigContributor implements SecurityConfigContributor {

    @Override
    public void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers("/blackbox/last-email").permitAll();
    }
}
