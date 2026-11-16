// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.image;

import com.sun.net.httpserver.HttpServer;
import org.glavo.url.WebURL;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies file-name propagation and resource ownership in image-loader defaults.
@NotNullByDefault
public final class ImageLoaderTest {

    /// Checks that unnamed streams reach the implementation unchanged with an empty hint.
    @Test
    public void unnamedStreamUsesEmptyHint() throws Exception {
        var source = new ByteArrayInputStream(new byte[]{0, 1});
        assertEquals(0, source.read());
        ImageLoader<String> loader = (input, fileName, width, height, preserveRatio, smooth) -> {
            assertSame(source, input);
            assertEquals("", fileName);
            assertEquals(1, input.read());
            assertEquals(8, width);
            assertEquals(16, height);
            assertTrue(preserveRatio);
            assertTrue(smooth);
            return "decoded";
        };

        assertEquals("decoded", loader.load(source, 8, 16, true, true));
    }

    /// Checks that the named convenience overload forwards the hint and closes after failure.
    @Test
    public void namedStreamUsesIntrinsicSizeAndClosesOnFailure() {
        var captured = new AtomicReference<@Nullable InputStream>();
        var failure = new IOException("Decoding failed");
        ImageLoader<String> loader = (input, fileName, width, height, preserveRatio, smooth) -> {
            captured.set(input);
            assertEquals("image.webp", fileName);
            assertEquals(0, width);
            assertEquals(0, height);
            assertFalse(preserveRatio);
            assertFalse(smooth);
            throw failure;
        };

        assertSame(failure, assertThrows(IOException.class,
                () -> loader.load(new ByteArrayInputStream(new byte[]{1}), "image.webp")));
        assertThrows(IOException.class, Objects.requireNonNull(captured.get())::read);
    }

    /// Checks that file names and scaling options reach the implementation and files always close.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void filePropagatesHintAndCloses(boolean fail, @TempDir Path directory) throws Exception {
        Path path = Files.write(directory.resolve("image.webp"), new byte[]{1, 2, 3});
        var captured = new AtomicReference<@Nullable InputStream>();
        var failure = new IOException("Decoding failed");
        ImageLoader<String> loader = (input, fileName, width, height, preserveRatio, smooth) -> {
            captured.set(input);
            assertEquals("image.webp", fileName);
            assertArrayEquals(new byte[]{1, 2, 3}, input.readAllBytes());
            assertEquals(8, width);
            assertEquals(16, height);
            assertTrue(preserveRatio);
            assertTrue(smooth);
            if (fail)
                throw failure;
            return "decoded";
        };

        if (fail) {
            assertSame(failure, assertThrows(IOException.class, () -> loader.load(path, 8, 16, true, true)));
        } else {
            assertEquals("decoded", loader.load(path, 8, 16, true, true));
        }
        assertThrows(IOException.class, Objects.requireNonNull(captured.get())::read);
    }

    /// Checks that URL loading uses the redirected path without query text and always closes the response.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void urlPropagatesResolvedHintAndCloses(boolean fail) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Location", "/images/image%20name.webp?download=true");
                exchange.sendResponseHeaders(302, -1);
            }
        });
        server.createContext("/images/", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().write(1);
            }
        });
        server.start();

        try {
            var captured = new AtomicReference<@Nullable InputStream>();
            var failure = new IOException("Decoding failed");
            ImageLoader<String> loader = (input, fileName, width, height, preserveRatio, smooth) -> {
                captured.set(input);
                assertEquals("image%20name.webp", fileName);
                assertEquals(1, input.read());
                assertEquals(0, width);
                assertEquals(0, height);
                assertFalse(preserveRatio);
                assertFalse(smooth);
                if (fail)
                    throw failure;
                return "decoded";
            };
            WebURL url = WebURL.parse("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");

            if (fail) {
                assertSame(failure, assertThrows(IOException.class, () -> loader.load(url)));
            } else {
                assertEquals("decoded", loader.load(url));
            }
            assertThrows(IOException.class, Objects.requireNonNull(captured.get())::read);
        } finally {
            server.stop(0);
        }
    }
}
