package dev.leilaalgarve.jogoacoes.emailservice.auth;

import dev.leilaalgarve.apikey.core.ApiKeyHasher;
import dev.leilaalgarve.apikey.core.MissingHmacPepperException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacPepperStartupCheckTest {

    @Test
    void failsWhenThePepperIsMissing() {
        assertThatThrownBy(() -> new HmacPepperStartupCheck(new ApiKeyHasher("")))
                .isInstanceOf(MissingHmacPepperException.class);
    }

    @Test
    void failsWhenThePepperIsBlank() {
        assertThatThrownBy(() -> new HmacPepperStartupCheck(new ApiKeyHasher("   ")))
                .isInstanceOf(MissingHmacPepperException.class);
    }

    @Test
    void passesWhenThePepperIsConfigured() {
        assertThatCode(() -> new HmacPepperStartupCheck(new ApiKeyHasher("some-pepper")))
                .doesNotThrowAnyException();
    }
}
