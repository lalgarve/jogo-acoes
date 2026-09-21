package dev.leilaalgarve.jogoacoes.blackboxproxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Transparent reverse proxy (spec 05-020): everything except {@code /blackbox/proxy/headers}
 * (handled by {@link DeviceHeaderController} -- Spring picks that more specific mapping over
 * this catch-all for the same path, no explicit exclusion needed here) is forwarded to
 * {@code blackbox-proxy.target-base-url}, with the five headers {@link DeviceHeaderStore} holds
 * always replacing -- never merged with -- whatever the incoming request carried for them.
 */
@RestController
public class ReverseProxyController {

    // Stripped from the incoming request before building the outbound one: the hop-by-hop
    // headers (copying them verbatim breaks the connection -- wrong Content-Length, Host
    // pointing at this proxy's own port, etc.) plus the five headers this proxy itself controls
    // (added back separately, from DeviceHeaderStore, never from what came in).
    private static final Set<String> NEVER_FORWARDED_AS_IS = Set.of(
            "connection", "transfer-encoding", "keep-alive", "host", "content-length",
            "sec-ch-ua", "sec-ch-ua-platform", "sec-ch-ua-platform-version", "sec-ch-ua-mobile", "user-agent");

    private static final Set<String> RESPONSE_HOP_BY_HOP = Set.of("connection", "transfer-encoding", "keep-alive");

    private final RestClient restClient;
    private final DeviceHeaderStore store;

    public ReverseProxyController(DeviceHeaderStore store,
                                   @Value("${blackbox-proxy.target-base-url}") String targetBaseUrl) {
        this.store = store;
        this.restClient = RestClient.builder().baseUrl(targetBaseUrl).build();
    }

    @RequestMapping(value = "/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String query = request.getQueryString();
        String pathAndQuery = request.getRequestURI() + (query != null ? "?" + query : "");
        byte[] requestBody = StreamUtils.copyToByteArray(request.getInputStream());
        DeviceHeaders device = store.get();

        return restClient.method(HttpMethod.valueOf(request.getMethod()))
                // A relative String here goes through RestClient's UriBuilderFactory, which
                // resolves it against the configured base URL -- passing a java.net.URI object
                // instead skips that resolution entirely and silently drops the base URL.
                .uri(pathAndQuery)
                .headers(headers -> {
                    copyForwardableHeaders(request, headers);
                    applyDeviceHeaders(device, headers);
                })
                .body(requestBody)
                .exchange((outgoing, incoming) -> {
                    byte[] responseBody = StreamUtils.copyToByteArray(incoming.getBody());
                    HttpHeaders responseHeaders = new HttpHeaders();
                    incoming.getHeaders().forEach((name, values) -> {
                        if (!RESPONSE_HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                            responseHeaders.addAll(name, values);
                        }
                    });
                    return ResponseEntity.status(incoming.getStatusCode()).headers(responseHeaders).body(responseBody);
                });
    }

    private void copyForwardableHeaders(HttpServletRequest request, HttpHeaders outgoing) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return;
        }
        for (String name : Collections.list(names)) {
            if (NEVER_FORWARDED_AS_IS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            outgoing.addAll(name, Collections.list(request.getHeaders(name)));
        }
    }

    private void applyDeviceHeaders(DeviceHeaders device, HttpHeaders outgoing) {
        putIfPresent(outgoing, "Sec-CH-UA", device.secChUa());
        putIfPresent(outgoing, "Sec-CH-UA-Platform", device.secChUaPlatform());
        putIfPresent(outgoing, "Sec-CH-UA-Platform-Version", device.secChUaPlatformVersion());
        putIfPresent(outgoing, "Sec-CH-UA-Mobile", device.secChUaMobile());
        putIfPresent(outgoing, "User-Agent", device.userAgent());
    }

    private void putIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null) {
            headers.set(name, value);
        }
    }
}
