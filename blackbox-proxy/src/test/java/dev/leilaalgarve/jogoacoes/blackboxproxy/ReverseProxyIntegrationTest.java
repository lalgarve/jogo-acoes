package dev.leilaalgarve.jogoacoes.blackboxproxy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-020 -- against a fake target server (plain JDK {@link HttpServer}, no dependency
 * beyond the JDK itself) that just echoes back every request header it received, so the
 * assertions can check exactly what the proxy sent, not what a real app/ would have done with
 * it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReverseProxyIntegrationTest {

    private static HttpServer fakeTarget;
    private static volatile Map<String, List<String>> lastReceivedHeaders;

    @LocalServerPort
    private int proxyPort;

    private RestClient client;

    @BeforeAll
    static void startFakeTarget() throws IOException {
        fakeTarget = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeTarget.createContext("/", exchange -> {
            // com.sun.net.httpserver.Headers normalizes casing internally and does
            // case-insensitive lookups on itself, but a plain copy loses that -- copy into a
            // map that's explicitly case-insensitive too, or exact-case lookups below miss.
            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.putAll(exchange.getRequestHeaders());
            lastReceivedHeaders = headers;
            exchange.getResponseHeaders().add("Set-Cookie", "SESSION=fake-session-value; Path=/");
            byte[] responseBody = "{}".getBytes();
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.getResponseBody().close();
        });
        fakeTarget.setExecutor(Executors.newSingleThreadExecutor());
        fakeTarget.start();
    }

    @AfterAll
    static void stopFakeTarget() {
        fakeTarget.stop(0);
    }

    @DynamicPropertySource
    static void targetBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("blackbox-proxy.target-base-url",
                () -> "http://" + fakeTarget.getAddress().getHostString() + ":" + fakeTarget.getAddress().getPort());
    }

    @BeforeEach
    void createClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + proxyPort).build();
    }

    @AfterEach
    void resetDeviceHeaders() {
        client.post().uri("/blackbox/proxy/headers").body(Map.of()).retrieve().toBodilessEntity();
    }

    private String header(String name) {
        List<String> values = lastReceivedHeaders.get(name);
        return values == null ? null : values.get(0);
    }

    @Test
    void unconfigured_headers_never_leak_what_the_caller_actually_sent() {
        client.get().uri("/anything").header("User-Agent", "a real browser's real User-Agent")
                .retrieve().toBodilessEntity();

        // Sec-CH-UA* are ordinary custom headers to this proxy's own outbound HTTP client, so
        // "unconfigured" really means absent. User-Agent is different: the JDK's own HTTP
        // client (java.net.http.HttpClient, underlying RestClient here) always attaches its own
        // default ("Java-http-client/...") when the application doesn't set one -- there's no
        // public API to suppress it entirely, the same practical limitation real browsers have
        // for this one header. Either way the caller's real value never reaches the target --
        // that's the guarantee this test actually needs, not literal absence of the header.
        assertThat(header("User-Agent")).isNotEqualTo("a real browser's real User-Agent");
        assertThat(header("Sec-CH-UA")).isNull();
    }

    @Test
    void configured_headers_reach_the_target_exactly() {
        client.post().uri("/blackbox/proxy/headers")
                .body(Map.of(
                        "secChUa", "\"Chromium\";v=\"131\"",
                        "secChUaPlatform", "\"Windows\"",
                        "userAgent", "seed-test-agent"))
                .retrieve().toBodilessEntity();

        client.get().uri("/anything").retrieve().toBodilessEntity();

        assertThat(header("Sec-CH-UA")).isEqualTo("\"Chromium\";v=\"131\"");
        assertThat(header("Sec-CH-UA-Platform")).isEqualTo("\"Windows\"");
        assertThat(header("User-Agent")).isEqualTo("seed-test-agent");
    }

    @Test
    void a_second_post_without_merge_clears_fields_left_out() {
        client.post().uri("/blackbox/proxy/headers").body(Map.of("secChUaPlatform", "\"Windows\""))
                .retrieve().toBodilessEntity();
        client.post().uri("/blackbox/proxy/headers").body(Map.of("secChUaMobile", "?1"))
                .retrieve().toBodilessEntity();

        client.get().uri("/anything").retrieve().toBodilessEntity();

        assertThat(header("Sec-CH-UA-Platform")).isNull();
        assertThat(header("Sec-CH-UA-Mobile")).isEqualTo("?1");
    }

    @Test
    void set_cookie_from_the_target_is_relayed_back() {
        ResponseEntity<Void> response = client.get().uri("/anything").retrieve().toBodilessEntity();

        assertThat(response.getHeaders().get("Set-Cookie")).contains("SESSION=fake-session-value; Path=/");
    }

    @Test
    void get_headers_returns_what_was_last_configured() {
        client.post().uri("/blackbox/proxy/headers").body(Map.of("secChUaMobile", "?0"))
                .retrieve().toBodilessEntity();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("/blackbox/proxy/headers").retrieve().body(Map.class);

        assertThat(body).containsEntry("secChUaMobile", "?0");
        assertThat(body).containsEntry("secChUaPlatform", null);
    }
}
