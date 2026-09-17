package dev.leilaalgarve.jogoacoes.login;

import java.util.Optional;

/**
 * Spec 05-005 — {@code POST /login-requests} requires no session, so an unvalidated {@code
 * returnTo} would be an open redirect. Only a same-origin relative path is accepted; anything
 * else is silently dropped (caller falls back to the role-based default), not rejected as an
 * error — the exact value isn't security-critical, only where it's allowed to point.
 */
public final class ReturnToValidator {

    private ReturnToValidator() {
    }

    public static Optional<String> validate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        if (!candidate.startsWith("/")) {
            return Optional.empty();
        }
        if (candidate.length() > 1) {
            char second = candidate.charAt(1);
            if (second == '/' || second == '\\') {
                // "//evil.com" (protocol-relative) or "/\evil.com" (a known browser bypass
                // for naive same-origin checks) -- both resolve to an external host.
                return Optional.empty();
            }
        }
        return Optional.of(candidate);
    }
}
