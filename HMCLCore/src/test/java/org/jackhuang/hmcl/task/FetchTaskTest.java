/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.task;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// Tests HTTP download retry and resume behavior.
@NotNullByDefault
public final class FetchTaskTest {
    /// Marks HTTP client dependencies as initialized for isolated unit tests.
    @org.junit.jupiter.api.BeforeAll
    public static void notifyFetchTaskInitialized() {
        FetchTask.notifyInitialized();
    }

    /// Ensures checksum failures are reported and do not replace an existing target file.
    @Test
    public void checksumMismatchFailsWithoutReplacingTarget(@TempDir Path tempDir) throws IOException {
        byte[] data = "downloaded".getBytes(UTF_8);
        Path target = tempDir.resolve("target.bin");
        Files.writeString(target, "original", UTF_8);

        try (TestHttpServer server = TestHttpServer.start(exchange -> sendBytes(exchange, 200, data))) {
            FileDownloadTask task = new FileDownloadTask(
                    server.uri(),
                    target,
                    new FileDownloadTask.IntegrityCheck("SHA-1", "0000000000000000000000000000000000000000")
            );
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(1);

            assertFalse(task.test());
            assertEquals("original", Files.readString(target, UTF_8));
        }
    }

    /// Ensures a mismatched Content-Range response is rejected before appending bytes.
    @Test
    public void invalidContentRangeFallsBackToFullDownload(@TempDir Path tempDir) throws IOException {
        byte[] data = "abcdefghij".getBytes(UTF_8);
        byte[] wrongRangeData = "defghi".getBytes(UTF_8);
        AtomicInteger requestCount = new AtomicInteger();
        List<@Nullable String> ranges = new ArrayList<>();

        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            ranges.add(exchange.getRequestHeaders().getFirst("Range"));
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Last-Modified", "Thu, 01 Jan 2026 00:00:00 GMT");

            switch (requestCount.incrementAndGet()) {
                case 1 -> {
                    exchange.sendResponseHeaders(200, data.length);
                    exchange.getResponseBody().write(data, 0, 4);
                    exchange.getResponseBody().flush();
                    exchange.close();
                }
                case 2 -> {
                    exchange.getResponseHeaders().set("Content-Range", "bytes 3-8/10");
                    sendBytes(exchange, 206, wrongRangeData);
                }
                default -> sendBytes(exchange, 200, data);
            }
        })) {
            Path target = tempDir.resolve("target.bin");
            FileDownloadTask task = new FileDownloadTask(server.uri(), target);
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(3);

            assertTrue(task.test(), () -> String.valueOf(task.getException()));
            assertArrayEquals(data, Files.readAllBytes(target));
            assertEquals(Arrays.asList(null, "bytes=4-", null), ranges);
        }
    }

    /// Ensures a valid Content-Range response is appended without a full redownload.
    @Test
    public void validContentRangeCompletesResume(@TempDir Path tempDir) throws IOException {
        byte[] data = "abcdefghij".getBytes(UTF_8);
        byte[] resumedData = "efghij".getBytes(UTF_8);
        AtomicInteger requestCount = new AtomicInteger();
        List<@Nullable String> ranges = new ArrayList<>();

        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            ranges.add(exchange.getRequestHeaders().getFirst("Range"));
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Last-Modified", "Thu, 01 Jan 2026 00:00:00 GMT");

            switch (requestCount.incrementAndGet()) {
                case 1 -> {
                    exchange.sendResponseHeaders(200, data.length);
                    exchange.getResponseBody().write(data, 0, 4);
                    exchange.getResponseBody().flush();
                    exchange.close();
                }
                case 2 -> {
                    exchange.getResponseHeaders().set("Content-Range", "bytes 4-9/10");
                    sendBytes(exchange, 206, resumedData);
                }
                default -> fail("Unexpected full redownload request");
            }
        })) {
            Path target = tempDir.resolve("target.bin");
            FileDownloadTask task = new FileDownloadTask(server.uri(), target);
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(3);

            assertTrue(task.test(), () -> String.valueOf(task.getException()));
            assertArrayEquals(data, Files.readAllBytes(target));
            assertEquals(Arrays.asList(null, "bytes=4-"), ranges);
            assertEquals(2, requestCount.get());
        }
    }

    /// Creates an isolated cache repository for one test.
    private static CacheRepository newRepository(Path tempDir) throws IOException {
        CacheRepository repository = new CacheRepository();
        repository.changeDirectory(Files.createDirectory(tempDir.resolve("common")));
        return repository;
    }

    /// Sends a byte array response.
    private static void sendBytes(HttpExchange exchange, int statusCode, byte[] data) throws IOException {
        exchange.sendResponseHeaders(statusCode, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    /// Minimal closeable HTTP server for local download tests.
    private record TestHttpServer(HttpServer server) implements AutoCloseable {
        /// Starts a server with one file endpoint.
        static TestHttpServer start(ThrowingHttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/file", handler::handle);
            server.start();
            return new TestHttpServer(server);
        }

        /// Returns the file endpoint URI.
        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    /// HTTP handler that may throw IOException.
    @FunctionalInterface
    private interface ThrowingHttpHandler {
        /// Handles one HTTP exchange.
        void handle(HttpExchange exchange) throws IOException;
    }
}
