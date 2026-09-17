package dev.leilaalgarve.jogoacoes.login;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;
import java.util.List;

/**
 * URL-based authorization (decision 4 in docs/context/iteracao-3.md): no UserDetailsService/
 * password login exists, so there's nothing for Spring Boot's default security
 * auto-configuration to work with -- this SecurityFilterChain replaces it entirely.
 * Authentication itself is established by LinkService/LoginLinkSessionService after
 * LoginController validates a login link, not by anything in this filter chain.
 *
 * Per-module route rules live in each module's own {@link SecurityConfigContributor} (spec
 * 05-006) -- this class only aggregates them and appends the final
 * {@code anyRequest().authenticated()} catch-all, plus configuration that isn't any module's
 * concern (csrf/formLogin/httpBasic/session repository/exception handling).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
                                                     List<SecurityConfigContributor> contributors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> {
                    contributors.forEach(contributor -> contributor.contribute(authorize));
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(SecurityConfig::writeNotLoggedIn)
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeAccessDenied(response)));

        return http.build();
    }

    private static void writeNotLoggedIn(jakarta.servlet.http.HttpServletRequest request,
                                          jakarta.servlet.http.HttpServletResponse response,
                                          org.springframework.security.core.AuthenticationException authException) throws IOException {
        writeJsonError(response, 401, "Not logged in");
    }

    private static void writeAccessDenied(jakarta.servlet.http.HttpServletResponse response) throws IOException {
        writeJsonError(response, 403, "Not an administrator");
    }

    private static void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
