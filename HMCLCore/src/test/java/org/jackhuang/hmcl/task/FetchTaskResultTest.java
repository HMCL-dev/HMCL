// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import com.sun.net.httpserver.HttpServer;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies result publication and cleanup for streamed downloads and cache hits.
@NotNullByDefault
class FetchTaskResultTest {
    /// Streams through the supplied task's response sink without requiring a JavaFX event loop.
    private static <T extends @Nullable Object> FetchTask<T> streamWithoutUI(FetchTask<T> task, CacheRepository repository) {
        task.setCacheRepository(repository);
        FetchTask<T> stream = new FetchTask<>(task.candidates) {
            /// Cached responses use the same representation as the underlying task.
            @Override
            protected T useCachedResult(Path file) throws IOException {
                return task.useCachedResult(file);
            }

            /// Uses the underlying task's caching policy during HTTP requests.
            @Override
            protected boolean shouldCheckETag() {
                return task.shouldCheckETag();
            }

            /// Opens the production response sink for the transfer.
            @Override
            protected Context<T> getContext(@Nullable UrlResponseInfo response, boolean checkETag, @Nullable String hash) throws IOException {
                return task.getContext(response, checkETag, hash);
            }

            /// Discards progress notifications in this headless fixture.
            @Override
            protected void updateProgressImmediately(double progress) {
            }
        };
        stream.setCacheRepository(repository);
        return stream;
    }

    /// Checks HTTP decoding, verified file publication, and reuse of both cache representations.
    @Test
    void streamedAndCachedResults(@TempDir Path directory) throws Exception {
        CacheRepository repository = new CacheRepository();
        repository.changeDirectory(directory);
        String text = "caf\u00e9";
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        String sha1 = DigestUtils.digestToString(CacheRepository.SHA1, bytes);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=ISO-8859-1");
                exchange.getResponseHeaders().set("ETag", "\"revision\"");
                exchange.getResponseHeaders().set("Cache-Control", "max-age=3600");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            WebURL url = WebURL.parse("http://127.0.0.1:" + server.getAddress().getPort() + "/artifact");
            GetTask textTask = new GetTask(url);
            assertEquals(text, streamWithoutUI(textTask, repository).run());
            assertEquals(text, textTask.run());
            assertEquals(1, requests.get());

            CacheFileTask fileTask = new CacheFileTask(DownloadCandidates.of(url), sha1);
            Path streamedFile = streamWithoutUI(fileTask, repository).run();
            assertArrayEquals(bytes, Files.readAllBytes(streamedFile));
            assertEquals(streamedFile, fileTask.run());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    /// Checks that a rejected file never replaces the destination and its temporary sink is removed.
    @Test
    void failedIntegrityCheckDiscardsTemporaryFile(@TempDir Path directory) throws Exception {
        Path source = Files.writeString(directory.resolve("source"), "invalid");
        Path destination = Files.writeString(directory.resolve("destination"), "previous");
        DownloadCandidate candidate = new DownloadCandidate(WebURL.parse(source.toUri().toString()), null, 0, null, null);
        var task = new FileDownloadTask(DownloadCandidates.of(candidate), destination);
        List<Path> temporaryFiles = new ArrayList<>();
        task.addIntegrityCheckHandler((temporary, target) -> {
            temporaryFiles.add(temporary);
            assertEquals("invalid", Files.readString(temporary));
            throw new IOException("Invalid contents");
        });

        CacheRepository repository = new CacheRepository();
        repository.changeDirectory(directory);
        FetchTask<@Nullable Void> stream = streamWithoutUI(task, repository);
        assertThrows(DownloadException.class, stream::run);
        assertNull(stream.getResult());
        assertEquals("previous", Files.readString(destination));
        assertEquals(1, temporaryFiles.size());
        assertFalse(Files.exists(temporaryFiles.get(0)));
    }

    /// Cancelling during a transfer preserves the destination and skips the remaining attempts.
    @Test
    void cancellationDuringTransferStopsRetries(@TempDir Path directory) throws IOException {
        Path source = Files.writeString(directory.resolve("source"), "new contents");
        Path destination = Files.writeString(directory.resolve("destination"), "previous");
        var candidate = new DownloadCandidate(WebURL.parse(source.toUri().toString()), null, 2, null, null);
        AtomicInteger attempts = new AtomicInteger();
        var download = new FileDownloadTask(DownloadCandidates.of(candidate), destination) {
            /// The execution cancelled after the transfer starts writing.
            final TaskExecutor execution = executor();

            /// Counts each attempt and requests cancellation when bytes have reached the sink.
            @Override
            protected void updateProgress(double progress) {
                if (progress == 0)
                    attempts.incrementAndGet();
                else
                    execution.cancel();
            }
        };

        assertFalse(download.execution.test());
        assertInstanceOf(CancellationException.class, download.getException());
        assertEquals(1, attempts.get());
        assertEquals("previous", Files.readString(destination));
    }
}
