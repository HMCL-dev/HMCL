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
package org.jackhuang.hmcl.task;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Tests sequential downloads, node reselection, and resume integrity using local HTTP servers.
@NotNullByDefault
@Timeout(40)
public final class FetchTaskTest {
    /// Directory containing this test's download target and cache.
    @TempDir
    Path directory;

    /// Reselects a slow node and resumes through an object-storage redirect without a final hash header.
    @Test
    public void reselectsSlowNodeAndResumesThroughStorageRedirect() throws Exception {
        byte[] content = content(2 * 1024 * 1024);
        String hash = sha1(content);
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger slowRequests = new AtomicInteger();
        CopyOnWriteArrayList<String> ranges = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> ifRanges = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer()) {
            server.handle("/master", exchange -> {
                int attempt = dispatches.incrementAndGet();
                redirect(exchange, attempt == 1 ? "/slow" : "/node", hash);
            });
            server.handle("/slow", exchange -> {
                slowRequests.incrementAndGet();
                exchange.getResponseHeaders().set("ETag", "W/\"node-a\"");
                exchange.sendResponseHeaders(200, content.length);
                try {
                    for (int offset = 0; offset < content.length; offset += 4096) {
                        exchange.getResponseBody().write(content, offset, Math.min(4096, content.length - offset));
                        exchange.getResponseBody().flush();
                        TimeUnit.MILLISECONDS.sleep(100);
                    }
                } catch (IOException ignored) {
                    // The client closes this connection after detecting a slow node.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            server.handle("/node", exchange -> redirect(exchange, "/storage", hash));
            server.handle("/storage", exchange -> {
                @Nullable String range = exchange.getRequestHeaders().getFirst("Range");
                ranges.add(Objects.requireNonNullElse(range, ""));
                ifRanges.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("If-Range"), ""));
                int offset = rangeOffset(range);
                exchange.getResponseHeaders().set("ETag", "\"node-b\"");
                sendContent(exchange, content, offset, content.length - offset);
            });

            Path target = download(server.url("/master"));
            assertArrayEquals(content, Files.readAllBytes(target));
            assertEquals(2, dispatches.get());
            assertEquals(1, slowRequests.get());
            assertEquals(1, ranges.size());
            assertTrue(ranges.get(0).startsWith("bytes="));
            assertTrue(rangeOffset(ranges.get(0)) > 0);
            assertEquals("", ifRanges.get(0));
        }
    }

    /// Validates hashes, ranges, and the completed digest, discarding partial content after invalid responses.
    @ParameterizedTest
    @EnumSource(ResumeResponse.class)
    public void validatesResumptionAcrossNodes(ResumeResponse responseType) throws Exception {
        byte[] content = content(64 * 1024);
        String hash = sha1(content);
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger entries = new AtomicInteger();
        CopyOnWriteArrayList<String> ranges = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> ifRanges = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer()) {
            server.handle("/entry", exchange -> {
                entries.incrementAndGet();
                redirect(exchange, "/master", null);
            });
            server.handle("/master", exchange -> {
                int attempt = dispatches.incrementAndGet();
                String selectedHash = responseType == ResumeResponse.CHANGED_HASH && attempt == 2 ? "0".repeat(40) : hash;
                redirect(exchange, "/node" + attempt, selectedHash);
            });
            server.handle("/node", exchange -> {
                int attempt = Integer.parseInt(exchange.getRequestURI().getPath().substring("/node".length()));
                @Nullable String range = exchange.getRequestHeaders().getFirst("Range");
                ranges.add(Objects.requireNonNullElse(range, ""));
                ifRanges.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("If-Range"), ""));
                if (attempt == 1) {
                    // This node provides a content hash and byte ranges without reusable HTTP validators.
                    if (responseType == ResumeResponse.OMITTED_ACCEPT_RANGES) {
                        exchange.sendResponseHeaders(200, content.length);
                        exchange.getResponseBody().write(content, 0, 4096);
                    } else {
                        sendContent(exchange, content, 0, 4096);
                    }
                } else if (attempt == 2 && responseType == ResumeResponse.UNSATISFIABLE_RANGE) {
                    exchange.sendResponseHeaders(416, -1);
                } else if (attempt == 2 && responseType == ResumeResponse.CHUNKED_RANGE) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes 4096-" + (content.length - 1) + "/" + content.length);
                    exchange.sendResponseHeaders(206, 0);
                    exchange.getResponseBody().write(content, 4096, content.length - 4096);
                } else {
                    int offset = responseType == ResumeResponse.IGNORED_RANGE ? 0 : rangeOffset(range);
                    if (responseType == ResumeResponse.INVALID_RANGE && attempt == 2)
                        offset++;
                    byte[] response = content.clone();
                    if (responseType == ResumeResponse.CORRUPT_BODY && attempt == 2)
                        response[response.length - 1] ^= 1;
                    sendContent(exchange, response, offset, response.length - offset);
                }
            });

            Path target = download(server.url("/entry"));
            assertArrayEquals(content, Files.readAllBytes(target));
            assertEquals(dispatches.get(), entries.get());
            boolean valid = switch (responseType) {
                case VALID, OMITTED_ACCEPT_RANGES, CHUNKED_RANGE -> true;
                default -> false;
            };
            assertEquals(valid ? 2 : 3, dispatches.get());
            assertEquals("", ranges.get(0));
            assertEquals("bytes=4096-", ranges.get(1));
            assertTrue(ifRanges.stream().allMatch(String::isEmpty));
            if (!valid)
                assertEquals("", ranges.get(2));
        }
    }

    /// Rejects corrupt content even when the final response omits the redirect chain's hash.
    @Test
    public void retainsHashUntilFinalResponse() throws Exception {
        byte[] content = content(8192);
        String hash = sha1(content);
        AtomicInteger requests = new AtomicInteger();
        try (TestServer server = new TestServer()) {
            server.handle("/master", exchange -> redirect(exchange, "/storage", hash));
            server.handle("/storage", exchange -> {
                byte[] response = content.clone();
                if (requests.incrementAndGet() == 1)
                    response[0] ^= 1;
                sendContent(exchange, response, 0, response.length);
            });
            Path target = download(server.url("/master"));
            assertArrayEquals(content, Files.readAllBytes(target));
            assertEquals(2, requests.get());
        }
    }

    /// Preserves strong ETag and If-Range validation for ordinary HTTP downloads.
    @Test
    public void preservesStandardHttpResumption() throws Exception {
        byte[] content = content(64 * 1024);
        AtomicInteger requests = new AtomicInteger();
        CopyOnWriteArrayList<String> ifRanges = new CopyOnWriteArrayList<>();
        try (TestServer server = new TestServer()) {
            server.handle("/file", exchange -> {
                int attempt = requests.incrementAndGet();
                ifRanges.add(Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("If-Range"), ""));
                exchange.getResponseHeaders().set("ETag", "\"stable\"");
                int offset = rangeOffset(exchange.getRequestHeaders().getFirst("Range"));
                sendContent(exchange, content, offset, attempt == 1 ? 4096 : content.length - offset);
            });
            assertArrayEquals(content, Files.readAllBytes(download(server.url("/file"))));
            assertEquals(2, requests.get());
            assertEquals("\"stable\"", ifRanges.get(1));
        }
    }

    /// Reports total file progress without resetting it when a partial response is appended.
    @Test
    public void preservesProgressAcrossResumption() throws Exception {
        byte[] content = content(64 * 1024);
        AtomicInteger requests = new AtomicInteger();
        List<Double> progressValues = new ArrayList<>();
        try (TestServer server = new TestServer()) {
            server.handle("/file", exchange -> {
                int attempt = requests.incrementAndGet();
                exchange.getResponseHeaders().set("ETag", "\"stable\"");
                int offset = rangeOffset(exchange.getRequestHeaders().getFirst("Range"));
                sendContent(exchange, content, offset, attempt == 1 ? 4096 : content.length - offset);
            });
            Path target = directory.resolve("download.bin");
            FileDownloadTask task = new FileDownloadTask(List.of(server.url("/file")), target) {
                /// Captures progress synchronously instead of dispatching JavaFX property updates.
                @Override
                protected void updateProgress(double progress) {
                    progressValues.add(progress);
                }
            };
            configureRepository(task);
            task.execute();
            assertArrayEquals(content, Files.readAllBytes(target));
            assertEquals(2, requests.get());
            assertEquals(0.0, progressValues.get(0));
            assertEquals(1.0, progressValues.get(progressValues.size() - 1));
            for (int i = 1; i < progressValues.size(); i++)
                assertTrue(progressValues.get(i) >= progressValues.get(i - 1), "Progress decreased: " + progressValues);
        }
    }

    /// Verifies resumed cache content even when the cache task has no explicit expected checksum.
    @Test
    public void verifiesResumedCacheContentBeforePublishing() throws Exception {
        byte[] content = content(64 * 1024);
        String hash = sha1(content);
        AtomicInteger requests = new AtomicInteger();
        try (TestServer server = new TestServer()) {
            server.handle("/master", exchange -> redirect(exchange, "/node", hash));
            server.handle("/node", exchange -> {
                int attempt = requests.incrementAndGet();
                byte[] response = content.clone();
                if (attempt == 2)
                    response[response.length - 1] ^= 1;
                exchange.getResponseHeaders().set("ETag", "W/\"cache-node\"");
                int offset = rangeOffset(exchange.getRequestHeaders().getFirst("Range"));
                sendContent(exchange, response, offset, attempt == 1 ? 4096 : response.length - offset);
            });
            CacheFileTask task = new CacheFileTask(server.url("/master"));
            configureRepository(task);
            task.execute();
            assertArrayEquals(content, Files.readAllBytes(task.getResult()));
            assertEquals(3, requests.get());
        }
    }

    /// Verifies text before decoding, even when the response cannot be resumed.
    @Test
    public void verifiesUnresumableTextContent() throws Exception {
        String text = "BMCLAPI content integrity";
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        String hash = sha1(content);
        AtomicInteger requests = new AtomicInteger();
        try (TestServer server = new TestServer()) {
            server.handle("/master", exchange -> redirect(exchange, "/node", hash));
            server.handle("/node", exchange -> {
                byte[] response = content.clone();
                if (requests.incrementAndGet() == 1)
                    response[0] ^= 1;
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            });
            GetTask task = new GetTask(server.url("/master"));
            configureRepository(task);
            task.execute();
            assertEquals(text, task.getResult());
            assertEquals(2, requests.get());
        }
    }

    /// Verifies cache content independently of the server's range support.
    @Test
    public void verifiesUnresumableCacheContent() throws Exception {
        byte[] content = content(8192);
        String hash = sha1(content);
        AtomicInteger requests = new AtomicInteger();
        try (TestServer server = new TestServer()) {
            server.handle("/master", exchange -> redirect(exchange, "/node", hash));
            server.handle("/node", exchange -> {
                byte[] response = content.clone();
                if (requests.incrementAndGet() == 1)
                    response[0] ^= 1;
                exchange.getResponseHeaders().set("ETag", "\"cache-node\"");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            });
            CacheFileTask task = new CacheFileTask(server.url("/master"));
            configureRepository(task);
            task.execute();
            assertArrayEquals(content, Files.readAllBytes(task.getResult()));
            assertEquals(2, requests.get());
        }
    }

    /// Downloads a file using an isolated cache and a bounded retry budget.
    private Path download(WebURL url) throws Exception {
        Path target = directory.resolve("download.bin");
        DownloadCandidates candidates = DownloadCandidates.of(new DownloadCandidate(url, null, 3,
                Duration.ofSeconds(2), Duration.ofSeconds(2)));
        FileDownloadTask task = new FileDownloadTask(candidates, target);
        configureRepository(task);
        task.execute();
        return target;
    }

    /// Keeps the task's cache within the current test directory.
    private void configureRepository(FetchTask<?> task) {
        CacheRepository repository = new CacheRepository();
        repository.changeDirectory(directory);
        task.setCacheRepository(repository);
    }

    /// Creates reproducible, incompressible file content.
    private static byte[] content(int size) {
        byte[] result = new byte[size];
        new Random(7002).nextBytes(result);
        return result;
    }

    /// Computes the content hash supplied by the simulated dispatcher.
    private static String sha1(byte[] content) {
        return DigestUtils.digestToString("SHA-1", content);
    }

    /// Sends a dispatcher or node redirect with an optional content hash.
    private static void redirect(HttpExchange exchange, String location, @Nullable String hash) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        if (hash != null)
            exchange.getResponseHeaders().set("X-BMCLAPI-Hash", hash);
        exchange.sendResponseHeaders(302, -1);
    }

    /// Parses a single open-ended byte range used by these tests.
    private static int rangeOffset(@Nullable String range) {
        return range == null ? 0 : Integer.parseInt(range.substring("bytes=".length(), range.length() - 1));
    }

    /// Sends full or partial content, optionally truncating the body to simulate an interrupted download.
    private static void sendContent(HttpExchange exchange, byte[] content, int offset, int count) throws IOException {
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        int length = content.length - offset;
        if (offset > 0)
            exchange.getResponseHeaders().set("Content-Range", "bytes " + offset + "-" + (content.length - 1) + "/" + content.length);
        exchange.sendResponseHeaders(offset > 0 ? 206 : 200, length);
        exchange.getResponseBody().write(content, offset, count);
    }

    /// Response behaviors relevant to cross-node resumption.
    @NotNullByDefault
    private enum ResumeResponse {
        /// Matching content hash and byte range.
        VALID,
        /// Range support was not advertised in the initial response.
        OMITTED_ACCEPT_RANGES,
        /// A valid partial response with chunked framing instead of Content-Length.
        CHUNKED_RANGE,
        /// A complete response instead of the requested range.
        IGNORED_RANGE,
        /// A byte range starting at the wrong offset.
        INVALID_RANGE,
        /// A different content hash advertised by the dispatcher.
        CHANGED_HASH,
        /// A matching range whose body fails integrity checking.
        CORRUPT_BODY,
        /// A server rejecting the requested range with HTTP 416.
        UNSATISFIABLE_RANGE
    }

    /// Handles requests independently, allowing tests to observe requests made before an old response finishes.
    @NotNullByDefault
    private static final class TestServer implements AutoCloseable {
        /// Local HTTP server listening only on the loopback interface.
        private final HttpServer server;
        /// First unexpected handler failure, propagated to the test thread on close.
        private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
        /// Executor for responses, stopped when the test server is closed.
        private final ExecutorService executor = Executors.newCachedThreadPool();

        /// Starts a server on an automatically assigned port.
        TestServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.start();
        }

        /// Registers a handler that closes its exchange after responding.
        public void handle(String path, HttpHandler handler) {
            server.createContext(path, exchange -> {
                try {
                    handler.handle(exchange);
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                    throw e;
                } finally {
                    exchange.close();
                }
            });
        }

        /// Creates a download URL for this server.
        WebURL url(String path) {
            return WebURL.parse("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        /// Releases the listening port, stops handlers, and reports their unexpected failures.
        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "HTTP handlers did not stop");
            @Nullable Throwable error = failure.get();
            if (error != null)
                throw new AssertionError("HTTP handler failed", error);
        }
    }
}
