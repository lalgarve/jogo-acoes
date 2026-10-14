package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import com.sun.net.httpserver.HttpServer;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Spec 05-048: the lists a scenario imports, served over real HTTP. Files go to the S3 of the
 * Compose LocalStack (bucket {@value #BUCKET}, path-style URL), which answers an anonymous
 * {@code GET} -- no internet, content chosen by each scenario. Each scenario writes under its own
 * prefix, so scenarios never read each other's files.
 *
 * <p>For the cases S3 cannot play, a small HTTP server of the scenario's own that holds the
 * answer until the scenario ends, and the address of a port where nothing listens.
 */
@Component
@ScenarioScope
public class DisposableListFiles {

    private static final String S3 = "http://localhost:4566";
    private static final String BUCKET = "disposable-lists";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String prefix = UUID.randomUUID().toString();
    private final List<HttpServer> heldServers = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);

    /** Writes (or overwrites) {@code name} with {@code content} and returns its URL. */
    public String put(String name, String content) {
        send(HttpRequest.newBuilder(URI.create(S3 + "/" + BUCKET)).PUT(HttpRequest.BodyPublishers.noBody()));
        send(HttpRequest.newBuilder(URI.create(url(name)))
                .PUT(HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8)));
        return url(name);
    }

    public String url(String name) {
        return S3 + "/" + BUCKET + "/" + prefix + "/" + name;
    }

    /** A server that accepts the connection and answers only when the scenario ends. */
    public String heldServerUrl() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    release.await(60, TimeUnit.SECONDS);
                    byte[] body = "held.test\n".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    // the import already gave up on this connection
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            heldServers.add(server);
            return "http://localhost:" + server.getAddress().getPort() + "/held.conf";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A port that was free a moment ago: connecting to it is refused. */
    public static String refusedUrl() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://localhost:" + socket.getLocalPort() + "/list.conf";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void releaseHeldServers() {
        release.countDown();
        heldServers.forEach(server -> server.stop(0));
        heldServers.clear();
    }

    private void send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("LocalStack S3 answered " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
