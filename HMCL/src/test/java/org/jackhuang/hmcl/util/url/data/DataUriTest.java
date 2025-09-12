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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class DataUriTest {

    private static String readString(String uri) throws IOException {
        return new DataUri(URI.create(uri)).readString();
    }

    private static byte[] readBytes(String uri) throws IOException {
        return new DataUri(URI.create(uri)).readBytes();
    }

    @Test
    public void testReadString() throws IOException {
        assertEquals("Hello, World!", readString("data:,Hello%2C%20World%21"));
        assertEquals("Hello, World!", readString("data:text/plain;base64,SGVsbG8sIFdvcmxkIQ=="));
        assertEquals("<h1>Hello, World!</h1>", readString("data:text/html,%3Ch1%3EHello%2C%20World%21%3C%2Fh1%3E"));
        assertEquals("<script>alert('hi');</script>", readString("data:text/html,%3Cscript%3Ealert%28%27hi%27%29%3B%3C%2Fscript%3E"));
    }
}
