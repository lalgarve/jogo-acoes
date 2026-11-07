package dev.leilaalgarve.jogoacoes.email.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-059: a lost response to POST /emails is retried with the same Idempotency-Key, and
 * email-service answers the retry with the send it already recorded. App talks to the real
 * email-service (as in EmailServiceClientIntegrationTest) through a pass-through proxy that, once
 * asked to, forwards one POST /emails and answers 503 in place of email-service's 202 -- the
 * request reached email-service, the answer never reached app.
 */
@SpringBootTest(properties = "email.sender=email-service")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailSendRetryIntegrationTest {

    private static final LossyProxy PROXY = LossyProxy.start(EmailServiceClientIntegrationTest.EMAIL_SERVICE_URL);

    @Autowired
    private EmailServiceGateway gateway;

    @DynamicPropertySource
    static void emailServiceThroughTheProxy(DynamicPropertyRegistry registry) {
        registry.add("email-service.base-url", PROXY::baseUrl);
    }

    @AfterAll
    static void stopProxy() {
        PROXY.stop();
    }

    @Test
    void aLostResponseIsRetriedWithTheSameKeyAndGetsTheSameSend() {
        String key = UUID.randomUUID().toString();
        PROXY.loseTheNextSendResponse();

        UUID id = gateway.sendEmail("login-link", unique("lost"), Map.of("name", "Ana", "link", "https://x/login-links/1"), key);

        List<Forwarded> sends = PROXY.forwardedSends();
        assertThat(sends).hasSize(2);
        assertThat(sends).allSatisfy(send -> {
            assertThat(send.idempotencyKey()).isEqualTo(key);
            assertThat(send.status()).isEqualTo(202);
            assertThat(new JSONObject(send.body()).getString("id")).isEqualTo(id.toString());
        });
    }

    record Forwarded(String idempotencyKey, int status, String body) {
    }

    /** Forwards every request to email-service; drops the answer to one POST /emails when asked. */
    static final class LossyProxy {

        private final HttpServer server;
        private final String upstream;
        private final HttpClient client = HttpClient.newHttpClient();
        private final AtomicBoolean loseNextSend = new AtomicBoolean();
        private final List<Forwarded> sends = new CopyOnWriteArrayList<>();

        private LossyProxy(HttpServer server, String upstream) {
            this.server = server;
            this.upstream = upstream;
        }

        static LossyProxy start(String upstreamBaseUrl) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                // Upstream base URL ends in /api, like the one app is given.
                LossyProxy proxy = new LossyProxy(server, upstreamBaseUrl.substring(0, upstreamBaseUrl.length() - "/api".length()));
                server.createContext("/", proxy::forward);
                server.start();
                return proxy;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort() + "/api";
        }

        void loseTheNextSendResponse() {
            loseNextSend.set(true);
        }

        List<Forwarded> forwardedSends() {
            return sends;
        }

        void stop() {
            server.stop(0);
        }

        private void forward(HttpExchange exchange) throws IOException {
            try (exchange) {
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upstream + exchange.getRequestURI()))
                        .method(exchange.getRequestMethod(), requestBody.length == 0
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(requestBody));
                for (String header : List.of("X-API-Key", "Idempotency-Key", "Content-Type", "Accept")) {
                    String value = exchange.getRequestHeaders().getFirst(header);
                    if (value != null) {
                        request.header(header, value);
                    }
                }
                HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());

                int status = response.statusCode();
                byte[] body = response.body();
                if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/api/emails")) {
                    sends.add(new Forwarded(exchange.getRequestHeaders().getFirst("Idempotency-Key"), status,
                            new String(body, java.nio.charset.StandardCharsets.UTF_8)));
                    if (loseNextSend.compareAndSet(true, false)) {
                        status = 503;
                        body = "{\"message\":\"response lost by the test proxy\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
    }
}
