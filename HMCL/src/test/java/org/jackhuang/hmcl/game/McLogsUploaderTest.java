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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    /// Verifies that byte truncation never splits a multi-byte character, for every possible byte limit.
    @Test
    public void truncateNeverProducesReplacementCharacters() {
        String content = "é汉😀日ア" + "abcd"; // 2-byte, 3-byte and 4-byte characters mixed with ASCII
        byte[] all = content.getBytes(UTF_8);

        for (int limit = 1; limit <= all.length; limit++) {
            String result = FileUtils.truncateUtf8ToByteLimit(content, limit);
            assertFalse(result.contains("\uFFFD"), "limit=" + limit);
            assertTrue(result.getBytes(UTF_8).length <= limit, "limit=" + limit);
        }
    }

    /// Verifies that a file smaller than the limit is read in full.
    @Test
    public void readsWholeFileWhenSmall(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("small.log");
        Files.write(file, "hello log".getBytes(UTF_8));

        assertEquals("hello log", FileUtils.readTextTailMaybeNativeEncoding(file, 1024));
    }

    /// Verifies that a file exactly at the limit is read in full.
    @Test
    public void readsFileExactlyAtLimit(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        Path file = tempDir.resolve("exact.log");
        Files.write(file, "a".repeat(maxBytes).getBytes(UTF_8));

        assertEquals("a".repeat(maxBytes), FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes));
    }

    /// Verifies that a file larger than the limit keeps its tail within the byte limit.
    @Test
    public void readsTailWhenLargerThanLimit(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        String marker = "END-MARKER";
        Path file = tempDir.resolve("medium.log");
        byte[] head = new byte[maxBytes + 512];
        Arrays.fill(head, (byte) 'a');
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(head);
            out.write(marker.getBytes(UTF_8));
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertTrue(tail.getBytes(UTF_8).length <= maxBytes);
        assertTrue(tail.endsWith(marker));
    }

    /// Verifies that a 100 MiB file is read by seeking to its tail instead of loading it whole.
    @Test
    public void readsTailOfLargeFileBySeeking(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024 * 1024;
        String marker = "CRASH-AT-END";
        Path file = tempDir.resolve("sparse.log");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(100L * 1024 * 1024);
            channel.write(ByteBuffer.wrap(marker.getBytes(UTF_8)));
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertTrue(tail.getBytes(UTF_8).length <= maxBytes);
        assertTrue(tail.endsWith(marker));
    }

    /// Verifies that an empty file reads as an empty string.
    @Test
    public void readsEmptyFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty.log");
        Files.write(file, new byte[0]);

        assertEquals("", FileUtils.readTextTailMaybeNativeEncoding(file, 1024));
    }

    /// Verifies that a missing file surfaces an exception rather than crashing.
    @Test
    public void failsOnMissingFile(@TempDir Path tempDir) {
        assertThrows(IOException.class,
                () -> FileUtils.readTextTailMaybeNativeEncoding(tempDir.resolve("missing.log"), 1024));
    }

    /// Verifies that a UTF-8 cut in the middle of a 3-byte character is aligned to a character boundary.
    @Test
    public void neverSplitsUtf8Characters(@TempDir Path tempDir) throws IOException {
        int maxBytes = 10_000;
        Path file = tempDir.resolve("chinese.log");
        Files.write(file, ("汉".repeat(10_000) + "-END").getBytes(UTF_8));

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertTrue(tail.getBytes(UTF_8).length <= maxBytes);
        assertFalse(tail.contains("\uFFFD"));
        assertTrue(tail.startsWith("汉"));
        assertTrue(tail.endsWith("-END"));
    }

    /// Verifies that the tail never opens with a truncated first line.
    @Test
    public void dropsTruncatedLeadingLine(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        Path file = tempDir.resolve("lines.log");
        byte[] head = "skipped-partial-line-".repeat(200).getBytes(UTF_8); // no newline
        String tailContent = "\nCOMPLETE-LINE-1\nCOMPLETE-LINE-2";
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(head);
            out.write(tailContent.getBytes(UTF_8));
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertTrue(tail.startsWith("COMPLETE-LINE-1"));
        assertTrue(tail.endsWith("COMPLETE-LINE-2"));
    }

    /// Verifies that credentials in command-line argument form have their values masked.
    @Test
    public void sanitizeMasksTokenArguments() {
        String content = String.join("\n",
                "--accessToken abc123",
                "--refresh_token refresh456",
                "--clientToken client789",
                "--session-token sessionABC");

        String sanitized = McLogsUploader.sanitize(content);

        assertFalse(sanitized.contains("abc123"));
        assertFalse(sanitized.contains("refresh456"));
        assertFalse(sanitized.contains("client789"));
        assertFalse(sanitized.contains("sessionABC"));
        assertTrue(sanitized.contains("--accessToken <access token>"));
    }

    /// Verifies that credentials in JSON form have their values masked, with and without surrounding spaces.
    @Test
    public void sanitizeMasksJsonStyleTokens() {
        String content = "{\"accessToken\":\"SECRET_TOKEN_1\",\"refreshToken\" : \"SECRET_TOKEN_2\"}";

        String sanitized = McLogsUploader.sanitize(content);

        assertFalse(sanitized.contains("SECRET_TOKEN_1"));
        assertFalse(sanitized.contains("SECRET_TOKEN_2"));
        assertTrue(sanitized.contains("\"accessToken\":\"<access token>\""));
        assertTrue(sanitized.contains("\"refreshToken\" : \"<access token>\""));
    }

    /// Verifies that `authToken` and its hyphenated/underscored variants are masked.
    @Test
    public void sanitizeMasksAuthToken() {
        String content = "authToken=ghp_S3cr3t\n--auth_token tkn123\n\"auth-token\": \"tkn-456\"";

        String sanitized = McLogsUploader.sanitize(content);

        assertFalse(sanitized.contains("ghp_S3cr3t"));
        assertFalse(sanitized.contains("tkn123"));
        assertFalse(sanitized.contains("tkn-456"));
        assertTrue(sanitized.contains("authToken=<access token>"));
    }

    /// Verifies that valid IPv4 addresses are masked while keeping a trailing port.
    @Test
    public void sanitizeMasksIpv4Addresses() {
        String sanitized = McLogsUploader.sanitize(
                "Connecting to 192.168.1.1:25565 and 127.0.0.1 and 255.255.255.255");

        assertFalse(sanitized.contains("192.168.1.1"));
        assertFalse(sanitized.contains("127.0.0.1"));
        assertFalse(sanitized.contains("255.255.255.255"));
        assertTrue(sanitized.contains("<IPv4>:25565"));
    }

    /// Verifies that invalid dotted numbers and unrelated dotted versions are left untouched.
    @Test
    public void sanitizeDoesNotTouchInvalidIpv4() {
        String content = "256.1.1.1 and 1.2.3.999 and version 1.20.4";

        String sanitized = McLogsUploader.sanitize(content);

        assertTrue(sanitized.contains("256.1.1.1"));
        assertTrue(sanitized.contains("1.2.3.999"));
        assertTrue(sanitized.contains("1.20.4"));
    }

    /// Verifies that IPv6 addresses in full and compressed form are masked.
    @Test
    public void sanitizeMasksIpv6Addresses() {
        String[] addresses = {"::1", "fe80::1", "2001:db8::1", "2001:db8:1234:5678::abcd"};

        for (String address : addresses) {
            String sanitized = McLogsUploader.sanitize("Connecting to [" + address + "]:25565");
            assertFalse(sanitized.contains(address), address);
            assertTrue(sanitized.contains("<IPv6>]:25565"), address);
        }
    }

    /// Verifies that a MAC address, which is also colon-separated hex, is not mistaken for IPv6.
    @Test
    public void sanitizeDoesNotTouchMacAddress() {
        String mac = "00:1A:2B:3C:4D:5E";

        String sanitized = McLogsUploader.sanitize("Shaders: device " + mac);

        assertTrue(sanitized.contains(mac));
    }

    /// Verifies that the current user's home directory, in either slash style, is masked.
    @Test
    public void sanitizeMasksUserHome() {
        String home = System.getProperty("user.home", "");
        String content = home + "/.minecraft/logs/latest.log";

        String sanitized = McLogsUploader.sanitize(content);

        assertFalse(sanitized.contains(home));
        assertTrue(sanitized.contains("<user home>"));
    }

    /// Verifies that Unix-style paths are compared case-sensitively, so a sibling directory is kept intact.
    @Test
    public void redactPathVariantIsCaseSensitiveForUnix() {
        assertEquals("<user home>/.minecraft",
                McLogsUploader.redactPathVariant("/home/alice/.minecraft", "/home/alice", false));
        assertEquals("/home/Alice/.minecraft",
                McLogsUploader.redactPathVariant("/home/Alice/.minecraft", "/home/alice", false));
    }

    /// Verifies that Windows-style paths are compared case-insensitively.
    @Test
    public void redactPathVariantIsCaseInsensitiveForWindows() {
        assertEquals("<user home>/.minecraft",
                McLogsUploader.redactPathVariant("/home/Alice/.minecraft", "/home/alice", true));
    }

    /// Verifies that a macOS home directory style is supported.
    @Test
    public void redactPathVariantCoversMacOsHome() {
        assertEquals("<user home>/Library/Application Support",
                McLogsUploader.redactPathVariant("/Users/alice/Library/Application Support", "/Users/alice", false));
    }

    /// Verifies that a successful response returns the URL it contains.
    @Test
    public void parsesSuccessUrl() throws IOException {
        String url = McLogsUploader.parseResponse("{\"success\":true,\"url\":\"https://mclo.gs/abcd\"}");

        assertEquals("https://mclo.gs/abcd", url);
    }

    /// Verifies that a successful response without an explicit URL falls back to the id.
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

    /// Verifies that a malformed response body is treated as a failure instead of crashing.
    @Test
    public void treatsMalformedJsonAsFailure() {
        assertThrows(IOException.class,
                () -> McLogsUploader.parseResponse("this is not json"));
    }

    /// Verifies that the byte limit keeps the tail of an over-long single line, since no line start is
    /// available within the read range.
    @Test
    public void readsTailOfSingleOversizedLine(@TempDir Path tempDir) throws IOException {
        int maxBytes = 10 * 1024 * 1024;
        Path file = tempDir.resolve("single-long-line.log");
        byte[] line = new byte[20 * 1024 * 1024];
        Arrays.fill(line, (byte) 'A');
        Files.write(file, line);

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertEquals(maxBytes, tail.getBytes(UTF_8).length);
        assertEquals("A".repeat(maxBytes), tail);
    }

    /// Verifies that an over-long final line after normal lines is read from its tail.
    @Test
    public void readsTailAfterOversizedFinalLine(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        Path file = tempDir.resolve("mixed-long-line.log");
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write("normal line\n".repeat(400).getBytes(UTF_8));
            byte[] longLine = new byte[8 * maxBytes];
            Arrays.fill(longLine, (byte) 'D');
            out.write(longLine);
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertEquals("D".repeat(maxBytes), tail);
    }

    /// Verifies that a single line exactly at the byte limit is returned in full.
    @Test
    public void readsSingleLineExactlyAtLimit(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        Path file = tempDir.resolve("exact-line.log");
        byte[] line = new byte[maxBytes];
        Arrays.fill(line, (byte) 'B');
        Files.write(file, line);

        assertEquals("B".repeat(maxBytes), FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes));
    }

    /// Verifies that a single line just over the byte limit is truncated to exactly that limit.
    @Test
    public void readsSingleLineJustOverLimit(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        Path file = tempDir.resolve("over-line.log");
        byte[] line = new byte[maxBytes + 1];
        Arrays.fill(line, (byte) 'C');
        Files.write(file, line);

        assertEquals("C".repeat(maxBytes), FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes));
    }

    /// Verifies that, when a newline follows an over-long line inside the read range, the tail starts at that
    /// complete line instead.
    @Test
    public void readsCompleteLineAfterOversizedLine(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        Path file = tempDir.resolve("long-then-line.log");
        try (OutputStream out = Files.newOutputStream(file)) {
            byte[] longLine = new byte[8 * maxBytes];
            Arrays.fill(longLine, (byte) 'E');
            out.write(longLine);
            out.write("\nTAIL-MARKER".getBytes(UTF_8));
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertEquals("TAIL-MARKER", tail);
    }

    /// Verifies that a UTF-8 BOM in the file head never leaks into the returned tail.
    @Test
    public void ignoresLeadingBomInTailRead(@TempDir Path tempDir) throws IOException {
        int maxBytes = 1024;
        Path file = tempDir.resolve("bom.log");
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM
            byte[] head = new byte[maxBytes + 100];
            Arrays.fill(head, (byte) 'A');
            out.write(head);
            out.write("-END".getBytes(UTF_8));
        }

        String tail = FileUtils.readTextTailMaybeNativeEncoding(file, maxBytes);

        assertFalse(tail.contains("\uFEFF"));
        assertTrue(tail.endsWith("-END"));
    }

    /// Verifies that a log just under the line limit is kept whole.
    @Test
    public void keepsAllLinesBelowLineLimit() {
        assertEquals(24_999, McLogsUploader.truncate(shortLog(24_999)).split("\n", -1).length);
    }

    /// Verifies that a log exactly at the line limit is kept whole.
    @Test
    public void keepsLinesAtExactLineLimit() {
        assertEquals(25_000, McLogsUploader.truncate(shortLog(25_000)).split("\n", -1).length);
    }

    /// Verifies that a log one line over the limit drops only the earliest line.
    @Test
    public void dropsEarliestLineJustOverLineLimit() {
        String[] result = McLogsUploader.truncate(shortLog(25_001)).split("\n", -1);

        assertEquals(25_000, result.length);
        assertEquals("line 1", result[0]);
    }

    /// Builds a log with [lines] short lines, so the byte limit never kicks in.
    private static String shortLog(int lines) {
        String[] all = new String[lines];
        for (int i = 0; i < lines; i++) {
            all[i] = "line " + i;
        }
        return String.join("\n", all);
    }
}
