package dev.leilaalgarve.jogoacoes.emailservice.auth;

import dev.leilaalgarve.apikey.validation.ApiKeyFailureReason;
import dev.leilaalgarve.apikey.validation.ApiKeyValidationResult;
import dev.leilaalgarve.apikey.validation.ApiKeyValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-030, T002: the api-key library is wired into this service for real -- the context
 * boots (this service's Flyway runs without a version clash with the library's migrations, and
 * {@code ddl-auto: validate} accepts {@code api_key.api_keys}), and the validator checks keys
 * against the {@code api_key} schema.
 *
 * <p>Needs the test key's schema restored beforehand: {@code ./scripts/test-api-key.sh restore}
 * (docker/postgres-email-service/test-data/README.md), with the test pepper that dump was
 * hashed with (src/test/resources/application.yml).
 */
@SpringBootTest
class ApiKeyIntegrationVerificationTest {

    /** The versioned test key (docker/postgres-email-service/test-data/README.md). */
    private static final String TEST_KEY = "dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU";

    /** Well-formed (prefix + 43 base64url characters), but never issued. */
    private static final String UNKNOWN_KEY = "dak_" + "A".repeat(43);

    @Autowired
    private ApiKeyValidator validator;

    @Test
    void theTestKeyIsValidForItsClient() {
        assertThat(validator.validate(TEST_KEY))
                .isEqualTo(new ApiKeyValidationResult.Valid("jogo-acoes"));
    }

    @Test
    void aWellFormedKeyThatWasNeverIssuedIsNotFound() {
        assertThat(validator.validate(UNKNOWN_KEY))
                .isEqualTo(new ApiKeyValidationResult.Invalid(ApiKeyFailureReason.NOT_FOUND));
    }
}
