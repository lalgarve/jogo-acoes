package dev.leilaalgarve.jogoacoes.login;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a human-readable device label from User-Agent Client Hints (spec 05-009) when
 * present, falling back to the raw {@code User-Agent} header, then to a generic placeholder --
 * same three-tier fallback {@link LoginLinkSessionService} already had, just with a richer top
 * tier. Never used as a security/identity signal, only for display -- see spec 05-009's
 * "Decisões em aberto".
 */
public final class DeviceLabelResolver {

    static final String UNKNOWN_DEVICE = "unknown-device";

    private static final Pattern BRAND_ENTRY = Pattern.compile("\"([^\"]*)\"\\s*;\\s*v\\s*=\\s*\"([^\"]*)\"");

    private DeviceLabelResolver() {
    }

    public static String resolve(String secChUa, String secChUaPlatform, String secChUaPlatformVersion,
                                  String secChUaMobile, String userAgent) {
        String platform = unquote(secChUaPlatform);
        if (platform != null && !platform.isBlank()) {
            return clientHintsLabel(platform, unquote(secChUaPlatformVersion), secChUa, secChUaMobile);
        }
        if (userAgent != null && !userAgent.isBlank()) {
            return userAgent;
        }
        return UNKNOWN_DEVICE;
    }

    private static String clientHintsLabel(String platform, String platformVersion, String secChUa, String secChUaMobile) {
        StringBuilder label = new StringBuilder(platform);
        if (platformVersion != null && !platformVersion.isBlank()) {
            label.append(' ').append(platformVersion);
        }
        String browser = browserBrandAndVersion(secChUa);
        if (browser != null) {
            label.append(" \u00b7 ").append(browser);
        }
        if ("?1".equals(secChUaMobile)) {
            label.append(" (mobile)");
        }
        return label.toString();
    }

    private static String browserBrandAndVersion(String secChUa) {
        if (secChUa == null || secChUa.isBlank()) {
            return null;
        }
        Matcher matcher = BRAND_ENTRY.matcher(secChUa);
        while (matcher.find()) {
            String brand = matcher.group(1);
            if (!isGreased(brand)) {
                return brand + " " + matcher.group(2);
            }
        }
        return null;
    }

    /** Every real browser inserts one randomized "not a real brand" entry into {@code Sec-CH-UA}
     * per the spec, precisely so sites can't hardcode a fixed brand list -- the rotating
     * templates (e.g. "Not_A Brand", "Not;A Brand", "Not.A/Brand") all contain "not" by
     * convention, which is what every real-world parser keys off instead of an exact list. */
    private static boolean isGreased(String brand) {
        return brand.toLowerCase(Locale.ROOT).contains("not");
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
