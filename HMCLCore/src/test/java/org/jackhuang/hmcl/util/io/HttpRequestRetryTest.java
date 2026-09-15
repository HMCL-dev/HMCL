/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests which HTTP failures [#HttpRequest] repeats.
///
/// The retry policy decides both how fast a misconfigured request fails and whether a transient
/// failure can still succeed, so the two sides must stay distinguishable: a client error is the
/// server's answer about the request itself, while a rate limit or a server error is not.
@NotNullByDefault
public final class HttpRequestRetryTest {

    /// The number of attempts a request configured with `retry(3)` may make.
    private static final int ATTEMPTS = 3;

    /// A rejected request must be sent once, because repeating it only repeats the rejection.
    @Test
    public void testClientErrorIsNotRetried() throws IOException {
        try (StubServer server = new StubServer(403)) {
            ResponseCodeException exception = assertThrows(ResponseCodeException.class,
                    () -> HttpRequest.GET(server.url()).retry(ATTEMPTS).getString());

            assertEquals(403, exception.getResponseCode());
            assertEquals(1, server.requestCount(),
                    "a 403 is the server's final answer, so the request must not be repeated");
        }
    }

    /// A missing resource behaves the same way as any other client error.
    @Test
    public void testNotFoundIsNotRetried() throws IOException {
        try (StubServer server = new StubServer(404)) {
            ResponseCodeException exception = assertThrows(ResponseCodeException.class,
                    () -> HttpRequest.GET(server.url()).retry(ATTEMPTS).getString());

            assertEquals(404, exception.getResponseCode());
            assertEquals(1, server.requestCount(),
                    "a 404 is the server's final answer, so the request must not be repeated");
        }
    }

    /// A rate-limited request was never processed, so every attempt is made.
    @Test
    public void testTooManyRequestsIsRetried() throws IOException {
        try (StubServer server = new StubServer(429)) {
            assertThrows(ResponseCodeException.class,
                    () -> HttpRequest.GET(server.url()).retry(ATTEMPTS).getString());

            assertEquals(ATTEMPTS, server.requestCount(),
                    "a rate-limited request was never processed, so it must be repeated");
        }
    }

    /// A server error is transient by definition, so every attempt is made.
    @Test
    public void testServerErrorIsRetried() throws IOException {
        try (StubServer server = new StubServer(500)) {
            assertThrows(ResponseCodeException.class,
                    () -> HttpRequest.GET(server.url()).retry(ATTEMPTS).getString());

            assertEquals(ATTEMPTS, server.requestCount(),
                    "a 500 is transient, so the request must be repeated");
        }
    }

    /// A successful request is answered on the first attempt.
    @Test
    public void testSuccessIsNotRetried() throws IOException {
        try (StubServer server = new StubServer(200)) {
            assertEquals("stub", HttpRequest.GET(server.url()).retry(ATTEMPTS).getString());
            assertEquals(1, server.requestCount());
        }
    }

    /// A loopback server that answers every request with a fixed status code and counts the attempts.
    private static final class StubServer implements AutoCloseable {

        /// The body every response carries, so that error paths reading the error stream succeed.
        private static final byte[] BODY = "stub".getBytes(StandardCharsets.UTF_8);

        /// The underlying JDK server. Spelled out because this package declares its own `HttpServer`.
        private final com.sun.net.httpserver.HttpServer server;

        /// The number of requests the server has received.
        private final AtomicInteger requests = new AtomicInteger();

        /// Starts a server bound to an ephemeral loopback port.
        ///
        /// @param responseCode the status code to answer every request with
        /// @throws IOException if the server cannot be bound
        private StubServer(int responseCode) throws IOException {
            server = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                exchange.sendResponseHeaders(responseCode, BODY.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(BODY);
                }
            });

            server.start();
        }

        /// @return the absolute URL of the stub endpoint
        private String url() {
            InetSocketAddress address = server.getAddress();
            return "http://" + address.getAddress().getHostAddress() + ':' + address.getPort() + "/stub";
        }

        /// @return how many requests the stub has received
        private int requestCount() {
            return requests.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
