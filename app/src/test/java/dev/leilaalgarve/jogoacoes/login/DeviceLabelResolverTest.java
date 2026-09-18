package dev.leilaalgarve.jogoacoes.login;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceLabelResolverTest {

    private static final String CHROMIUM_SEC_CH_UA = "\"Not_A Brand\";v=\"24\", \"Chromium\";v=\"131\"";

    @Test
    void resolvesFullClientHintsIntoAFormattedLabel() {
        String label = DeviceLabelResolver.resolve(CHROMIUM_SEC_CH_UA, "\"Windows\"", "\"15.0.0\"", "?0", null);

        assertThat(label).isEqualTo("Windows 15.0.0 · Chromium 131");
    }

    @Test
    void appendsAMobileSuffixWhenSecChUaMobileIsTrue() {
        String label = DeviceLabelResolver.resolve(CHROMIUM_SEC_CH_UA, "\"Android\"", "\"14\"", "?1", null);

        assertThat(label).isEqualTo("Android 14 · Chromium 131 (mobile)");
    }

    @Test
    void ignoresTheGreasedFakeBrandEntry() {
        String secChUa = "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"131\", \"Google Chrome\";v=\"131\"";

        String label = DeviceLabelResolver.resolve(secChUa, "\"Windows\"", "\"15.0.0\"", "?0", null);

        assertThat(label).isEqualTo("Windows 15.0.0 · Chromium 131");
    }

    @Test
    void buildsSomethingFromPlatformAloneWhenTheOtherHintsAreMissing() {
        String label = DeviceLabelResolver.resolve(null, "\"Linux\"", null, null, null);

        assertThat(label).isEqualTo("Linux");
    }

    @Test
    void fallsBackToTheRawUserAgentWhenNoClientHintsArePresent() {
        String label = DeviceLabelResolver.resolve(null, null, null, null, "Mozilla/5.0 (Test)");

        assertThat(label).isEqualTo("Mozilla/5.0 (Test)");
    }

    @Test
    void fallsBackToUnknownDeviceWhenNeitherClientHintsNorUserAgentArePresent() {
        String label = DeviceLabelResolver.resolve(null, null, null, null, null);

        assertThat(label).isEqualTo(DeviceLabelResolver.UNKNOWN_DEVICE);
    }

    @Test
    void treatsABlankUserAgentTheSameAsAMissingOne() {
        String label = DeviceLabelResolver.resolve(null, null, null, null, "   ");

        assertThat(label).isEqualTo(DeviceLabelResolver.UNKNOWN_DEVICE);
    }

    @Test
    void aBlankPlatformFallsThroughToUserAgentInsteadOfAnEmptyClientHintsLabel() {
        String label = DeviceLabelResolver.resolve(CHROMIUM_SEC_CH_UA, "   ", "\"15.0.0\"", "?0", "Mozilla/5.0 (Test)");

        assertThat(label).isEqualTo("Mozilla/5.0 (Test)");
    }
}
