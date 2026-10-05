package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import dev.leilaalgarve.apikey.core.ApiKey;
import dev.leilaalgarve.apikey.core.ApiKeyFormat;
import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.ApiKeyRepository;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Real API keys for a single scenario (spec 05-030, plan.md, "Chaves dos cenários Gherkin"):
 * written to {@code api_key.api_keys} with the library's own {@link ApiKeyHasher} (test pepper),
 * the same way the api-key CLI issues them, so the service validates them for real. Only rows
 * created here are deleted afterwards ({@link ApiKeyCleanupHooks}) -- never the versioned test
 * key restored by {@code scripts/test-api-key.sh}.
 */
@Component
@ScenarioScope
public class ScenarioApiKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository repository;
    private final ApiKeyHasher hasher;
    private final List<Long> issuedIds = new ArrayList<>();

    public ScenarioApiKeys(ApiKeyRepository repository, ApiKeyHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    /** An active key for {@code clientName}: no expiration, not revoked. */
    public String issueActive(String clientName) {
        return issue(clientName, null, false);
    }

    public String issueExpired(String clientName) {
        return issue(clientName, Instant.now().minus(Duration.ofDays(1)), false);
    }

    public String issueRevoked(String clientName) {
        return issue(clientName, null, true);
    }

    /** Shaped like a real key (ApiKeyFormat), but never written to the database. */
    public static String neverIssued() {
        return generate();
    }

    void deleteIssued() {
        repository.deleteAllById(issuedIds);
        issuedIds.clear();
    }

    private String issue(String clientName, Instant expiresAt, boolean revoked) {
        String plaintext = generate();
        Instant now = Instant.now();
        ApiKey key = new ApiKey(clientName, hasher.hash(plaintext), now, expiresAt);
        if (revoked) {
            key.revokeAt(now);
        }
        issuedIds.add(repository.save(key).getId());
        return plaintext;
    }

    private static String generate() {
        byte[] entropy = new byte[ApiKeyFormat.ENTROPY_BYTES];
        RANDOM.nextBytes(entropy);
        return ApiKeyFormat.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }
}
