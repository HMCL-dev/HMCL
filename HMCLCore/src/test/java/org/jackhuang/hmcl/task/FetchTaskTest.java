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
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpHeaders;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// Tests HTTP download retry and resume behavior.
@NotNullByDefault
public final class FetchTaskTest {
    /// Marks HTTP connection dependencies as initialized for isolated unit tests.
    @org.junit.jupiter.api.BeforeAll
    public static void notifyFetchTaskInitialized() {
        FetchTask.notifyInitialized();
    }

    /// Ensures URL normalization survives cache-index persistence without merging escaped path separators.
    @Test
    public void cacheKeysPreserveEncodedPaths(@TempDir Path tempDir) throws IOException {
        CacheRepository repository = newRepository(tempDir);
        WebURL encoded = WebURL.parse("HTTPS://EXAMPLE.COM:443/a%2Fb?token=first#part");
        WebURL separated = WebURL.parse("https://example.com/a/b?token=second");
        HttpHeaders headers = HttpHeaders.of(Map.of("etag", List.of("\"version\"")), (name, value) -> true);
        repository.cacheText(new UrlResponseInfo(200, encoded, headers), "encoded");
        repository.cacheText(new UrlResponseInfo(200, separated, headers), "separated");

        CacheRepository reloaded = new CacheRepository();
        reloaded.changeDirectory(repository.getCommonDirectory());
        assertEquals("encoded", Files.readString(reloaded.getCachedRemoteFile(
                WebURL.parse("https://example.com/a%2Fb?token=changed"), false)));
        assertEquals("separated", Files.readString(reloaded.getCachedRemoteFile(separated, false)));
        assertEquals(Map.of("if-none-match", "\"version\""), reloaded.injectConnection(encoded));
        reloaded.removeRemoteEntry(encoded);
        assertThrows(IOException.class, () -> reloaded.getCachedRemoteFile(encoded, false));
        assertEquals("separated", Files.readString(reloaded.getCachedRemoteFile(separated, false)));
    }

    /// Ensures non-HTTP downloads still accept file URLs with escaped path characters.
    @Test
    public void readsFileURL(@TempDir Path tempDir) throws IOException {
        Path source = Files.writeString(tempDir.resolve("file with spaces.txt"), "file content");
        TextFetchTask task = new TextFetchTask(WebURL.of(source.toUri()));
        task.setCacheRepository(newRepository(tempDir));
        task.setRetry(1);
        assertTrue(task.test());
        assertEquals("file content", task.getResult());
    }

    /// Ensures query-only redirects retain the current path and preserve literal query spaces.
    @Test
    public void resolvesQueryOnlyRedirect(@TempDir Path tempDir) throws IOException {
        AtomicInteger requests = new AtomicInteger();
        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            if (requests.getAndIncrement() == 0) {
                exchange.getResponseHeaders().set("Location", "?token=a b&path=%2F");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                sendBytes(exchange, 200, exchange.getRequestURI().toASCIIString().getBytes(UTF_8));
            }
        })) {
            TextFetchTask task = new TextFetchTask(server.url());
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(1);
            assertTrue(task.test());
            assertEquals("/file?token=a%20b&path=%2F", task.getResult());
            assertEquals(2, requests.get());
        }
    }

    /// Ensures checksum failures are reported and do not replace an existing target file.
    @Test
    public void checksumMismatchFailsWithoutReplacingTarget(@TempDir Path tempDir) throws IOException {
        byte[] data = "downloaded".getBytes(UTF_8);
        Path target = tempDir.resolve("target.bin");
        Files.writeString(target, "original", UTF_8);

        try (TestHttpServer server = TestHttpServer.start(exchange -> sendBytes(exchange, 200, data))) {
            FileDownloadTask task = new FileDownloadTask(
                    server.url(),
                    target,
                    new FileDownloadTask.IntegrityCheck("SHA-1", "0000000000000000000000000000000000000000")
            );
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(1);

            assertFalse(task.test());
            assertEquals("original", Files.readString(target, UTF_8));
        }
    }

    /// Ensures matching content is published and reused by its expected SHA-1.
    @Test
    public void cacheFileTaskUsesExpectedSha1(@TempDir Path tempDir) throws IOException {
        byte[] data = "cached content".getBytes(UTF_8);
        String sha1 = DigestUtils.digestToString(CacheRepository.SHA1, data);
        WebURL url = WebURL.parse("https://example.invalid/file");
        CacheRepository repository = newRepository(tempDir);
        CacheFileTask first = new CacheFileTask(List.of(url), sha1);
        first.setCacheRepository(repository);

        try (FetchTask.Context context = first.getContext(null, false, null)) {
            context.write(data, 0, data.length);
            context.withResult(true);
        }

        Path cached = Objects.requireNonNull(first.getResult());
        assertArrayEquals(data, Files.readAllBytes(cached));

        CacheFileTask second = new CacheFileTask(List.of(url), sha1);
        second.setCacheRepository(repository);
        assertEquals(FetchTask.EnumCheckETag.CACHED, second.shouldCheckETag());
        assertEquals(cached, second.getResult());
    }

    /// Ensures mismatched content is not published under an expected SHA-1.
    @Test
    public void cacheFileTaskRejectsMismatchedSha1(@TempDir Path tempDir) throws IOException {
        byte[] data = "unexpected content".getBytes(UTF_8);
        String expectedSha1 = "0000000000000000000000000000000000000000";
        CacheRepository repository = newRepository(tempDir);
        CacheFileTask task = new CacheFileTask(
                List.of(WebURL.parse("https://example.invalid/file")), expectedSha1);
        task.setCacheRepository(repository);
        FetchTask.Context context = task.getContext(null, false, null);
        context.write(data, 0, data.length);
        context.withResult(true);

        assertThrows(ChecksumMismatchException.class, context::close);
        assertTrue(repository.checkExistentFile(
                null, CacheRepository.SHA1, expectedSha1).isEmpty());
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
            FileDownloadTask task = new FileDownloadTask(server.url(), target);
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(3);

            assertTrue(task.test(), () -> requestCount.get() + ": " + task.getException());
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
        List<@Nullable String> ifRanges = new ArrayList<>();

        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            ranges.add(exchange.getRequestHeaders().getFirst("Range"));
            ifRanges.add(exchange.getRequestHeaders().getFirst("If-Range"));
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
            FileDownloadTask task = new FileDownloadTask(server.url(), target);
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(3);

            assertTrue(task.test(), () -> requestCount.get() + ": " + task.getException());
            assertArrayEquals(data, Files.readAllBytes(target));
            assertEquals(Arrays.asList(null, "bytes=4-"), ranges);
            assertEquals(Arrays.asList(null, "Thu, 01 Jan 2026 00:00:00 GMT"), ifRanges);
            assertEquals(2, requestCount.get());
        }
    }

    /// Ensures a changed strong ETag prevents appending a partial response.
    @Test
    public void changedStrongETagFallsBackToFullDownload(@TempDir Path tempDir) throws IOException {
        byte[] data = "abcdefghij".getBytes(UTF_8);
        byte[] resumedData = "efghij".getBytes(UTF_8);
        AtomicInteger requestCount = new AtomicInteger();
        List<@Nullable String> ranges = new ArrayList<>();
        List<@Nullable String> ifRanges = new ArrayList<>();

        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            ranges.add(exchange.getRequestHeaders().getFirst("Range"));
            ifRanges.add(exchange.getRequestHeaders().getFirst("If-Range"));
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Last-Modified", "Thu, 01 Jan 2026 00:00:00 GMT");

            switch (requestCount.incrementAndGet()) {
                case 1 -> {
                    exchange.getResponseHeaders().set("ETag", "\"first\"");
                    exchange.sendResponseHeaders(200, data.length);
                    exchange.getResponseBody().write(data, 0, 4);
                    exchange.getResponseBody().flush();
                    exchange.close();
                }
                case 2 -> {
                    exchange.getResponseHeaders().set("ETag", "\"second\"");
                    exchange.getResponseHeaders().set("Content-Range", "bytes 4-9/10");
                    sendBytes(exchange, 206, resumedData);
                }
                default -> {
                    exchange.getResponseHeaders().set("ETag", "\"second\"");
                    sendBytes(exchange, 200, data);
                }
            }
        })) {
            Path target = tempDir.resolve("target.bin");
            FileDownloadTask task = new FileDownloadTask(server.url(), target);
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(3);

            assertTrue(task.test(), () -> String.valueOf(task.getException()));
            assertArrayEquals(data, Files.readAllBytes(target));
            assertEquals(Arrays.asList(null, "bytes=4-", null), ranges);
            assertEquals(Arrays.asList(null, "\"first\"", null), ifRanges);
        }
    }

    /// Ensures a full retry uses the latest response metadata instead of stale headers.
    @Test
    public void fullRetryUsesLatestResponseMetadata(@TempDir Path tempDir) throws IOException {
        byte[] data = "ok".getBytes(UTF_8);
        byte[] firstData = "abcdefghij".getBytes(UTF_8);
        byte[] wrongRangeData = "defghi".getBytes(UTF_8);
        AtomicInteger requestCount = new AtomicInteger();

        try (TestHttpServer server = TestHttpServer.start(exchange -> {
            switch (requestCount.incrementAndGet()) {
                case 1 -> {
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-16LE");
                    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                    exchange.getResponseHeaders().set("Last-Modified", "Thu, 01 Jan 2026 00:00:00 GMT");
                    exchange.sendResponseHeaders(200, firstData.length);
                    exchange.getResponseBody().write(firstData, 0, 4);
                    exchange.getResponseBody().flush();
                    exchange.close();
                }
                case 2 -> {
                    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                    exchange.getResponseHeaders().set("Last-Modified", "Thu, 01 Jan 2026 00:00:00 GMT");
                    exchange.getResponseHeaders().set("Content-Range", "bytes 3-8/10");
                    sendBytes(exchange, 206, wrongRangeData);
                }
                default -> {
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    sendBytes(exchange, 200, data);
                }
            }
        })) {
            TextFetchTask task = new TextFetchTask(server.url());
            task.setCacheRepository(newRepository(tempDir));
            task.setRetry(2);

            assertTrue(task.test(), () -> requestCount.get() + ": " + task.getException());
            assertEquals("ok", task.getResult());
            assertEquals(3, requestCount.get());
        }
    }

    /// Text fetch task that avoids JavaFX progress updates in isolated unit tests.
    private static final class TextFetchTask extends FetchTask<String> {
        /// Creates a text fetch task for one URL.
        TextFetchTask(WebURL url) {
            super(List.of(url));
        }

        @Override
        protected void useCachedResult(Path cachedFile) throws IOException {
            setResult(Files.readString(cachedFile));
        }

        @Override
        protected EnumCheckETag shouldCheckETag() {
            return EnumCheckETag.NOT_CHECK_E_TAG;
        }

        @Override
        protected Context getContext(@Nullable UrlResponseInfo response, boolean checkETag, @Nullable String bmclapiHash) {
            Charset charset = NetworkUtils.getCharsetFromContentType(response == null
                    ? null
                    : response.headers().firstValue("content-type").orElse(null));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            return new Context() {
                @Override
                public void reset() {
                    baos.reset();
                }

                @Override
                public void write(byte[] buffer, int offset, int len) {
                    baos.write(buffer, offset, len);
                }

                @Override
                public void close() {
                    if (isSuccess()) {
                        setResult(baos.toString(charset));
                    }
                }
            };
        }

        @Override
        protected void updateProgress(long count, long total) {
        }

        @Override
        protected void updateProgress(double progress) {
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

        /// Returns the file endpoint URL.
        WebURL url() {
            return WebURL.parse("http://127.0.0.1:" + server.getAddress().getPort() + "/file");
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
