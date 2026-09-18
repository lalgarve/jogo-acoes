package dev.leilaalgarve.jogoacoes.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Asks the browser (spec 05-009) to start sending User-Agent Client Hints on every response,
 * not just the login routes -- Client Hints only attach to a request once the browser has seen
 * {@code Accept-CH} on an earlier response to this origin, so casting the widest possible net
 * is what gives the magic-link click (usually the very first request from that browser) any
 * chance of already carrying them. A plain {@code @Component} filter bean is enough for Spring
 * Boot to register it for every request; it doesn't need to be a security concern, so it isn't
 * wired into {@link SecurityConfig}.
 */
@Component
public class AcceptChFilter extends OncePerRequestFilter {

    private static final String ACCEPT_CH_HEADER = "Accept-CH";
    private static final String ACCEPT_CH_VALUE =
            "Sec-CH-UA, Sec-CH-UA-Platform, Sec-CH-UA-Platform-Version, Sec-CH-UA-Mobile";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(ACCEPT_CH_HEADER, ACCEPT_CH_VALUE);
        filterChain.doFilter(request, response);
    }
}
