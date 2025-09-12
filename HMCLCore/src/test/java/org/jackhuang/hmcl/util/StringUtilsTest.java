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
package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class StringUtilsTest {

    @Test
    public void testNormalizeWhitespaces() {
        assertEquals("", StringUtils.normalizeWhitespaces(""));
        assertEquals("", StringUtils.normalizeWhitespaces(" "));
        assertEquals("", StringUtils.normalizeWhitespaces(" \t"));
        assertEquals("", StringUtils.normalizeWhitespaces(" \t "));

        assertEquals("abc", StringUtils.normalizeWhitespaces("abc"));
        assertEquals("abc", StringUtils.normalizeWhitespaces(" abc"));
        assertEquals("abc", StringUtils.normalizeWhitespaces("abc "));
        assertEquals("abc", StringUtils.normalizeWhitespaces(" abc "));
        assertEquals("abc", StringUtils.normalizeWhitespaces(" \tabc \t"));

        assertEquals("a bc", StringUtils.normalizeWhitespaces("a bc"));
        assertEquals("a bc", StringUtils.normalizeWhitespaces("a  bc"));
        assertEquals("a bc", StringUtils.normalizeWhitespaces("a \tbc"));
        assertEquals("a bc", StringUtils.normalizeWhitespaces(" a \tbc "));
        assertEquals("a b c", StringUtils.normalizeWhitespaces(" a\tb c "));
        assertEquals("a b c", StringUtils.normalizeWhitespaces(" a \t b c "));
        assertEquals("a b c", StringUtils.normalizeWhitespaces(" a \t b  c "));
    }
}
