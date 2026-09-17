/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.url.data;

import org.glavo.url.WebURL;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// Tests data URL parsing and decoding through both WebURL and the Java URL handler.
@NotNullByDefault
public final class DataURLTest {

    /// Parses a data URL and reads its text payload.
    private static String readString(String uri) throws IOException {
        return new DataURL(WebURL.parse(uri)).readString();
    }

    /// Parses a data URL and reads its byte payload.
    private static byte[] readBytes(String uri) throws IOException {
        return new DataURL(WebURL.parse(uri)).readBytes();
    }

    /// Ensures percent-encoded and Base64 text payloads are decoded.
    @Test
    public void testReadString() throws IOException {
        assertEquals("Hello, World!", readString("data:,Hello%2C%20World%21"));
        assertEquals("Hello, World!", readString("data:text/plain;base64,SGVsbG8sIFdvcmxkIQ=="));
        assertEquals("<h1>Hello, World!</h1>", readString("data:text/html,%3Ch1%3EHello%2C%20World%21%3C%2Fh1%3E"));
        assertEquals("<script>alert('hi');</script>", readString("data:text/html,%3Cscript%3Ealert%28%27hi%27%29%3B%3C%2Fscript%3E"));
    }

    /// Ensures queries remain part of the payload, fragments are excluded, and escapes are decoded once.
    @Test
    public void testPayloadDelimiters() throws IOException {
        assertEquals("Hello?name=a+b&path=/#end", readString("data:,Hello?name=a+b&path=%2F%23end#ignored"));
        assertEquals("Hello?", readString("data:,Hello?#ignored"));
        assertEquals("?a+b#c%23", readString("data:,%3Fa%2Bb%23c%2523#ignored"));
        assertEquals("a,b", readString("data:,a,b"));
        assertEquals("", readString("data:,"));
    }

    /// Ensures WebURL normalization accepts uppercase schemes and unescaped spaces.
    @Test
    public void testNormalizedInput() throws IOException {
        assertEquals("Hello, World!", readString("DATA:text/plain,Hello, World!"));
        assertTrue(DataURL.isDataUri(WebURL.parse("DATA:,value")));
        assertFalse(DataURL.isDataUri(WebURL.parse("https://example.com/")));
        assertFalse(DataURL.isDataUri(null));
    }

    /// Ensures payload bytes, Base64 escapes, charset metadata, and literal plus signs are retained.
    @Test
    public void testReadBytesAndMetadata() throws IOException {
        assertArrayEquals("Hello + World!".getBytes(UTF_8), readBytes("data:,Hello%20+%20World!"));
        assertArrayEquals(new byte[]{0, (byte) 0xFF, (byte) 0x80}, readBytes("data:application/octet-stream;base64,AP+A"));
        assertEquals("Hello", readString("data:;base64,SGVsbG8%3D#ignored"));

        DataURL data = new DataURL(WebURL.parse("data:text/plain;charset=ISO-8859-1;base64,Y2Fm6Q=="));
        assertEquals("text/plain;charset=ISO-8859-1", data.getMediaType());
        assertEquals("ISO-8859-1", data.getCharset().name());
        assertTrue(data.isBase64());
        assertEquals("Y2Fm6Q==", data.getRawData());
        assertEquals("caf\u00E9", data.readString());
    }

    /// Ensures invalid schemes, missing separators, and invalid Base64 are reported at the appropriate stage.
    @Test
    public void testInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new DataURL(WebURL.parse("https://example.com/")));
        assertThrows(IllegalArgumentException.class, () -> new DataURL(WebURL.parse("data:text/plain")));
        assertThrows(IOException.class, () -> readBytes("data:;base64,invalid!"));
        assertThrows(IOException.class, () -> readString("data:;base64,invalid!"));
    }

    /// Ensures Java URL connections use the WebURL parser and expose decoded payload bytes.
    @Test
    public void testURLHandler() throws IOException {
        URLConnection connection = new URL(null, "data:text/plain,Hello world?name=a+b%23c#ignored", new DataURLHandle())
                .openConnection();
        assertEquals("text/plain", connection.getContentType());
        try (var input = connection.getInputStream()) {
            assertEquals("Hello world?name=a+b#c", new String(input.readAllBytes(), UTF_8));
        }
        assertThrows(MalformedURLException.class,
                () -> new URL(null, "data:text/plain", new DataURLHandle()).openConnection());
    }
}
