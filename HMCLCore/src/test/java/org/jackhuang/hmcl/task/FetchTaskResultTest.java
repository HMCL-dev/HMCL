// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import com.sun.net.httpserver.HttpServer;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
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
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies result publication and cleanup for streamed downloads and cache hits.
@NotNullByDefault
class FetchTaskResultTest {
    /// Runs a fresh task against the test's cache repository.
    private static <T extends @Nullable Object> T runWithCache(FetchTask<T> task, CacheRepository repository) throws Exception {
        task.setCacheRepository(repository);
        return task.run();
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
            assertEquals(text, runWithCache(new GetTask(url), repository));
            assertEquals(text, runWithCache(new GetTask(url), repository));
            assertEquals(1, requests.get());

            DownloadCandidates candidates = DownloadCandidates.of(url);
            Path streamedFile = Objects.requireNonNull(runWithCache(new CacheFileTask(candidates, sha1), repository));
            assertArrayEquals(bytes, Files.readAllBytes(streamedFile));
            assertEquals(streamedFile, runWithCache(new CacheFileTask(candidates, sha1), repository));
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
        task.setCacheRepository(repository);
        assertThrows(DownloadException.class, task::run);
        assertThrows(IllegalStateException.class, task::resultNow);
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
            /// Counts each attempt independently of progress delivery.
            @Override
            protected void beforeDownload(WebURL url) {
                attempts.incrementAndGet();
            }
        };
        TaskHandle handle = download.newHandle();
        handle.addTaskListener(new TaskListener() {
            /// Cancels the transfer after bytes have reached the sink.
            @Override
            public void onTaskProgress(Task<?> task, double fraction) {
                if (task == download && fraction > 0)
                    handle.cancel();
            }
        });

        assertFalse(handle.test());
        assertInstanceOf(CancellationException.class, download.exceptionNow());
        assertEquals(1, attempts.get());
        assertEquals("previous", Files.readString(destination));
    }
}
