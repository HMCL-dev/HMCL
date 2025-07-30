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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public class NetworkUtilsTest {
    @Test
    public void testGetEncodingFromUrl() {
        assertEquals(UTF_8, NetworkUtils.getCharsetFromContentType(null));
        assertEquals(UTF_8, NetworkUtils.getCharsetFromContentType(""));
        assertEquals(UTF_8, NetworkUtils.getCharsetFromContentType("text/html"));
        assertEquals(UTF_8, NetworkUtils.getCharsetFromContentType("text/html; charset=utf-8"));
        assertEquals(US_ASCII, NetworkUtils.getCharsetFromContentType("text/html; charset=ascii"));
    }
}
