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

package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class KeyValuePairUtilsTest {
    @Test
    public void test() throws IOException {
        String content = "#test: key0=value0\n \n" +
                "key1=value1\n" +
                "key2=\"value2\"\n" +
                "key3=\"\\\" \\n\"\n";

        Map<String, String> properties = KeyValuePairUtils.loadProperties(new BufferedReader(new StringReader(content)));

        assertEquals(Lang.mapOf(
                pair("key1", "value1"),
                pair("key2", "value2"),
                pair("key3", "\" \n")
        ), properties);
    }
}
