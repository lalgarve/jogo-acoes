package dev.leilaalgarve.jogoacoes.emailservice.auth;

import dev.leilaalgarve.apikey.validation.ApiKeyFailureReason;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult;
import dev.leilaalgarve.apikey.validation.ApiKeyValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-030, T002: the api-key library is wired into this service for real -- the context
 * boots (this service's Flyway runs without a version clash with the library's migrations, and
 * {@code ddl-auto: validate} accepts {@code api_key.api_keys}), and the validator checks keys
 * against the {@code api_key} schema, with the permissions of each key (spec 05-048).
 *
 * <p>Needs the test key's schema restored beforehand: {@code ./scripts/test-api-key.sh restore}
 * (docker/postgres-email-service/test-data/README.md), with the test pepper that dump was
 * hashed with (src/test/resources/application.yml).
 */
@SpringBootTest
class ApiKeyIntegrationVerificationTest {

    /** The versioned test key (docker/postgres-email-service/test-data/README.md). */
    private static final String TEST_KEY = "dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU";

    /** Spec 05-048: operations' test key, issued with --permissions disposable-email:admin. */
    private static final String OPERATIONS_TEST_KEY = "dak_D9CebHAQ6cq2vAnYIomxJ1nX56qn_5dDRv2J-aHrQFc";

    /** Well-formed (prefix + 43 base64url characters), but never issued. */
    private static final String UNKNOWN_KEY = "dak_" + "A".repeat(43);

    @Autowired
    private ApiKeyValidator validator;

    /**
     * Spec 05-048: issued before api-key 1.1.0, with no permission stored, so it gets the
     * catalog's default (application.yml) -- sends e-mail, does not manage the lists.
     */
    @Test
    void theTestKeyIsValidForItsClientWithTheDefaultPermission() {
        assertThat(validator.validate(TEST_KEY))
                .isEqualTo(new ApiKeyValidationResult.Valid("jogo-acoes", Set.of("email:send")));
    }

    @Test
    void theOperationsTestKeyOnlyManagesTheDisposableDomainLists() {
        assertThat(validator.validate(OPERATIONS_TEST_KEY))
                .isEqualTo(new ApiKeyValidationResult.Valid("operations", Set.of("disposable-email:admin")));
    }

    @Test
    void aWellFormedKeyThatWasNeverIssuedIsNotFound() {
        assertThat(validator.validate(UNKNOWN_KEY))
                .isEqualTo(new ApiKeyValidationResult.Invalid(ApiKeyFailureReason.NOT_FOUND));
    }
}
