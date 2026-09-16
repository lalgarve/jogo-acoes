package dev.leilaalgarve.jogoacoes.login;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated test for the open-redirect guard (spec 05-005) — no Spring context. */
class ReturnToValidatorTest {

    @Test
    void acceptsASameOriginRelativePath() {
        assertThat(ReturnToValidator.validate("/competitions/mine")).contains("/competitions/mine");
        assertThat(ReturnToValidator.validate("/competitions/42")).contains("/competitions/42");
    }

    @Test
    void rejectsAnAbsoluteUrl() {
        assertThat(ReturnToValidator.validate("https://evil.com")).isEmpty();
        assertThat(ReturnToValidator.validate("http://evil.com/competitions/mine")).isEmpty();
    }

    @Test
    void rejectsAProtocolRelativeUrl() {
        assertThat(ReturnToValidator.validate("//evil.com")).isEmpty();
    }

    @Test
    void rejectsABackslashBypass() {
        assertThat(ReturnToValidator.validate("/\\evil.com")).isEmpty();
    }

    @Test
    void rejectsAPathWithNoLeadingSlash() {
        assertThat(ReturnToValidator.validate("competitions/mine")).isEmpty();
    }

    @Test
    void rejectsNullAndBlank() {
        assertThat(ReturnToValidator.validate(null)).isEmpty();
        assertThat(ReturnToValidator.validate("")).isEmpty();
        assertThat(ReturnToValidator.validate("   ")).isEmpty();
    }

    @Test
    void aSingleSlashIsAcceptedAsIs() {
        // Edge case, not a security concern -- there's no second character to inspect.
        Optional<String> result = ReturnToValidator.validate("/");
        assertThat(result).contains("/");
    }
}
