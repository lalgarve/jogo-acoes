package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A local port that refuses connections until {@link #open()}, then forwards every connection to
 * {@code targetHost:targetPort} (spec 05-044): lets a test point a real client at "SES is down"
 * and later at the real LocalStack, without restarting anything. Plain sockets, no mock.
 */
public final class TcpForwarder implements AutoCloseable {

    private final String targetHost;
    private final int targetPort;
    private final int port;
    private volatile ServerSocket serverSocket;

    public TcpForwarder(String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.port = freePort();
    }

    /** A port nothing listens on: connecting to it is refused, the way a stopped SES would be. */
    public static int freePort() {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int port() {
        return port;
    }

    public void open() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Thread acceptor = new Thread(this::acceptLoop, "tcp-forwarder-" + port);
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                Socket target = new Socket(targetHost, targetPort);
                pipe(client, target);
                pipe(target, client);
            } catch (IOException e) {
                // closed, or the target refused: the client sees its connection drop
            }
        }
    }

    private static void pipe(Socket from, Socket to) {
        Thread thread = new Thread(() -> {
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                in.transferTo(out);
            } catch (IOException e) {
                // either side closed
            } finally {
                closeQuietly(from);
                closeQuietly(to);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // already closed
        }
    }

    @Override
    public void close() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // already closed
            }
        }
    }
}
