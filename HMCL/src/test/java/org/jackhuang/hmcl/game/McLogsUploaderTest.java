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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    /// Verifies that the byte limit keeps the tail of an oversized log.
    @Test
    public void keepsTailBytesOverByteLimit() {
        String marker = "END-OF-LOG";
        String content = "a".repeat(10 * 1024 * 1024) + marker;

        String truncated = McLogsUploader.truncate(content);

        assertEquals(10 * 1024 * 1024, truncated.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(truncated.endsWith(marker));
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
