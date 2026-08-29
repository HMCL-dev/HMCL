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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// Tests for the mclo.gs log uploader's pure client-side logic.
@NotNullByDefault
public final class McLogsUploaderTest {
    /// Verifies that content under every limit is returned unchanged.
    @Test
    public void keepsShortContentUnchanged() {
        String content = "a short log";

        assertEquals(content, McLogsUploader.truncate(content));
    }

    /// Verifies that the byte limit keeps the tail of an oversized log.
    @Test
    public void keepsTailBytesOverByteLimit() {
        String marker = "END-OF-LOG";
        String content = "a".repeat(10 * 1024 * 1024) + marker;

        String truncated = McLogsUploader.truncate(content);

        assertEquals(10 * 1024 * 1024, truncated.getBytes(UTF_8).length);
        assertTrue(truncated.endsWith(marker));
    }

    /// Verifies that the line limit keeps the tail, where the crash details live.
    @Test
    public void keepsTailLinesOverLineLimit() {
        String[] all = new String[25_000 + 7];
        for (int i = 0; i < all.length; i++) {
            all[i] = "line " + i;
        }

        String truncated = McLogsUploader.truncate(String.join("\n", all));
        String[] result = truncated.split("\n", -1);

        assertEquals(25_000, result.length);
        assertEquals("line 7", result[0]);
        assertEquals("line " + (all.length - 1), result[result.length - 1]);
    }

    /// Verifies that both the line and byte limits are satisfied at once while the tail is kept.
    @Test
    public void keepsTailWhenBothLimitsAreExceeded() {
        String line = "0123456789".repeat(60); // ~600 bytes per line
        String[] all = new String[25_000 + 7];
        for (int i = 0; i < all.length; i++) {
            all[i] = line + "-line-" + i;
        }

        String truncated = McLogsUploader.truncate(String.join("\n", all));

        assertTrue(truncated.getBytes(UTF_8).length <= 10 * 1024 * 1024);
        assertTrue(truncated.split("\n", -1).length <= 25_000);
        assertTrue(truncated.contains("-line-" + (all.length - 1)));
    }

    /// Verifies that multi-byte characters survive byte truncation intact (no replacement characters).
    @Test
    public void keepsMultibyteCharactersIntact() {
        String tail = "崩溃日志" + "\uD83D\uDCDD"; // Chinese + emoji
        String content = "a".repeat(10 * 1024 * 1024) + tail;

        String truncated = McLogsUploader.truncate(content);

        assertFalse(truncated.contains("\uFFFD"));
        assertTrue(truncated.endsWith(tail));
    }

    /// Verifies that only the tail of an oversized file is read (the result is larger than the limit by the
    /// alignment guard, which a whole-file-read-then-truncate implementation would never produce).
    @Test
    public void readsTailInsteadOfWholeFile(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024 * 1024;
        Path file = tempDir.resolve("large.log");
        String marker = "TAIL-MARKER-END";
        byte[] head = new byte[maxBytes + 64 * 1024];
        Arrays.fill(head, (byte) 'a');
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(head);
            out.write(marker.getBytes(UTF_8));
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertTrue(tail.getBytes(UTF_8).length > maxBytes);
        assertTrue(tail.endsWith(marker));
    }

    /// Verifies that a UTF-8 multi-byte character is never split when only the tail is read.
    @Test
    public void neverSplitsUtf8Characters(@TempDir Path tempDir) throws IOException {
        int maxBytes = 10_000;
        Path file = tempDir.resolve("cjk.log");
        byte[] cjk = "汉".repeat(10_000).getBytes(UTF_8); // 30_000 bytes, 3 bytes each
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(cjk);
            out.write("-END".getBytes(UTF_8));
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertFalse(tail.contains("\uFFFD"));
        assertTrue(tail.endsWith("-END"));
        assertTrue(tail.startsWith("汉"));
    }

    /// Verifies that an empty file yields an empty result.
    @Test
    public void readsEmptyFile(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("empty.log"));

        assertEquals("", FileUtils.readTextTailMaybeNativeEncoding(file, 1024));
    }

    /// Verifies that a missing file raises IOException, which the uploader turns into a fallback.
    @Test
    public void failsOnMissingFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("missing.log");

        assertThrows(IOException.class, () -> FileUtils.readTextTailMaybeNativeEncoding(file, 1024));
    }

    /// Verifies that access and refresh token arguments are masked, wherever their value appears.
    @Test
    public void sanitizeMasksTokenArguments() {
        String sanitized = McLogsUploader.sanitize(
                "java --accessToken SECRET_VALUE_123 --version 8 && refresh_token=REFRESH_VALUE_456");

        assertFalse(sanitized.contains("SECRET_VALUE_123"));
        assertFalse(sanitized.contains("REFRESH_VALUE_456"));
        assertTrue(sanitized.contains("<access token>"));
    }

    /// Verifies that JSON-style tokens are masked while the surrounding name is preserved.
    @Test
    public void sanitizeMasksJsonStyleTokens() {
        String sanitized = McLogsUploader.sanitize("{ \"accessToken\" : \"SECRET_VALUE_123\" }");

        assertFalse(sanitized.contains("SECRET_VALUE_123"));
        assertTrue(sanitized.contains("accessToken"));
        assertTrue(sanitized.contains("<access token>"));
    }

    /// Verifies that IPv4 addresses are masked but a trailing port remains visible.
    @Test
    public void sanitizeMasksIpv4Addresses() {
        String sanitized = McLogsUploader.sanitize("Connecting to 192.168.1.1:25565");

        assertFalse(sanitized.contains("192.168.1.1"));
        assertTrue(sanitized.contains("<IPv4>:25565"));
    }

    /// Verifies that the current user's home directory is masked.
    @Test
    public void sanitizeMasksUserHome() {
        String home = System.getProperty("user.home");

        String sanitized = McLogsUploader.sanitize(home + "/.minecraft/logs/latest.log");

        assertTrue(sanitized.contains("<user home>"));
        assertFalse(sanitized.contains(home));
    }

    /// Verifies that a successful response yields the documented URL.
    @Test
    public void parsesSuccessUrl() throws IOException {
        String url = McLogsUploader.parseResponse(
                "{\"success\":true,\"id\":\"WnMMikq\",\"url\":\"https://mclo.gs/WnMMikq\"}");

        assertEquals("https://mclo.gs/WnMMikq", url);
    }

    /// Verifies that the URL is rebuilt from the id when the url field is absent.
    @Test
    public void rebuildsUrlFromId() throws IOException {
        String url = McLogsUploader.parseResponse("{\"success\":true,\"id\":\"WnMMikq\"}");

        assertEquals("https://mclo.gs/WnMMikq", url);
    }

    /// Verifies that a server-side error is surfaced with its message.
    @Test
    public void surfacesServerError() {
        IOException exception = assertThrows(IOException.class,
                () -> McLogsUploader.parseResponse(
                        "{\"success\":false,\"error\":\"Required field 'content' not found.\"}"));

        assertTrue(exception.getMessage().contains("Required field 'content' not found."));
    }

    /// Verifies that a successful response without any locator field is rejected.
    @Test
    public void rejectsResponseWithoutUrl() {
        assertThrows(IOException.class,
                () -> McLogsUploader.parseResponse("{\"success\":true}"));
    }
}
