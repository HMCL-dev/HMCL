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
package org.jackhuang.hmcl.util.io;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.io.NetworkUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public class NetworkUtilsTest {

    @Test
    public void testIsLoopbackAddress() {
        assertTrue(isLoopbackAddress(URI.create("https://127.0.0.1/test")));
        assertTrue(isLoopbackAddress(URI.create("https://127.0.0.1:8080/test")));
        assertTrue(isLoopbackAddress(URI.create("https://localhost/test")));
        assertTrue(isLoopbackAddress(URI.create("https://localhost:8080/test")));
        assertTrue(isLoopbackAddress(URI.create("https://[::1]/test")));
        assertTrue(isLoopbackAddress(URI.create("https://[::1]:8080/test")));

        assertFalse(isLoopbackAddress(URI.create("https://www.example.com/test")));
        assertFalse(isLoopbackAddress(URI.create("https://www.example.com:8080/test")));
    }

    @Test
    public void testEncodeLocation() {
        assertEquals("https://github.com", encodeLocation("https://github.com"));
        assertEquals("https://github.com/HMCL-dev/HMCL/commits?author=Glavo", encodeLocation("https://github.com/HMCL-dev/HMCL/commits?author=Glavo"));
        assertEquals("https://www.example.com/file%20with%20space", encodeLocation("https://www.example.com/file with space"));
        assertEquals("https://www.example.com/file%20with%20space", encodeLocation("https://www.example.com/file%20with%20space"));
        assertEquals("https://www.example.com/%5Bfile%5D", encodeLocation("https://www.example.com/[file]"));
        assertEquals("https://www.example.com/%7Bfile%7D", encodeLocation("https://www.example.com/{file}"));
        assertEquals("https://www.example.com/%E6%B5%8B%E8%AF%95", encodeLocation("https://www.example.com/测试"));
        assertEquals("https://www.example.com/%F0%9F%98%87", encodeLocation("https://www.example.com/\uD83D\uDE07"));
        assertEquals("https://www.example.com/test?a=10+20", encodeLocation("https://www.example.com/test?a=10 20"));
        assertEquals("https://www.example.com/test?a=10+20&b=[30]&c={40}", encodeLocation("https://www.example.com/test?a=10 20&b=[30]&c={40}"));
        assertEquals("https://www.example.com/%E6%B5%8B%E8%AF%95?a=10+20", encodeLocation("https://www.example.com/测试?a=10 20"));

        // Invalid surrogate pair
        assertEquals("https://www.example.com/%EF%BF%BD", encodeLocation("https://www.example.com/\uD83D"));
        assertEquals("https://www.example.com/%EF%BF%BD", encodeLocation("https://www.example.com/\uDE07"));
        assertEquals("https://www.example.com/%EF%BF%BDtest", encodeLocation("https://www.example.com/\uD83Dtest"));
        assertEquals("https://www.example.com/%EF%BF%BDtest", encodeLocation("https://www.example.com/\uDE07test"));
    }

    @Test
    public void testGetEncodingFromUrl() {
        assertEquals(UTF_8, getCharsetFromContentType(null));
        assertEquals(UTF_8, getCharsetFromContentType(""));
        assertEquals(UTF_8, getCharsetFromContentType("text/html"));
        assertEquals(UTF_8, getCharsetFromContentType("text/html; charset=utf-8"));
        assertEquals(US_ASCII, getCharsetFromContentType("text/html; charset=ascii"));
    }
}
