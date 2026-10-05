package dev.leilaalgarve.jogoacoes.emailservice.auth;

import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.MissingHmacPepperException;
import org.springframework.stereotype.Component;

/**
 * Fails startup when {@code API_KEY_HMAC_PEPPER} is missing (spec 05-030, plan.md, "Pepper
 * ausente em production"). {@link ApiKeyHasher} only checks the pepper when it hashes a key, so
 * without this the service would start "healthy" and answer 500 on every request. Hashing a
 * throwaway value reuses the library's own rule for what counts as missing.
 */
@Component
public class HmacPepperStartupCheck {

    private static final String PROBE = "startup-check";

    /** @throws MissingHmacPepperException if the pepper is not configured */
    public HmacPepperStartupCheck(ApiKeyHasher hasher) {
        hasher.hash(PROBE);
    }
}
