package dev.leilaalgarve.jogoacoes.blackboxproxy;

/**
 * The five headers {@link ReverseProxyController} controls on every forwarded request. Every
 * field is nullable on purpose: {@code null} means "remove this header from the outbound
 * request" -- never "leave whatever the browser sent" (spec 05-020, corrected design -- the
 * point of this proxy is that these headers always come from here, never from the browser).
 */
public record DeviceHeaders(
        String secChUa,
        String secChUaPlatform,
        String secChUaPlatformVersion,
        String secChUaMobile,
        String userAgent) {

    static final DeviceHeaders BLANK = new DeviceHeaders(null, null, null, null, null);
}
