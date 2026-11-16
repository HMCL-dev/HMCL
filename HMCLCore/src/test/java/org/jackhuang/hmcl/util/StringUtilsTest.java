// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    public void testSerializeStringList() {
        assertEquals("[]", StringUtils.serializeStringList(null));
        assertEquals("[]", StringUtils.serializeStringList(List.of()));
        assertEquals("[]", StringUtils.serializeStringList(Arrays.asList((String) null)));
        assertEquals("[\"hello\"]", StringUtils.serializeStringList(List.of("hello")));
        assertEquals("[\"hello\"]", StringUtils.serializeStringList(Arrays.asList("hello", null)));
        assertEquals("[\"he\\\"llo\"]", StringUtils.serializeStringList(Arrays.asList("he\"llo", null)));
        assertEquals("[\"hello\",\"world\"]", StringUtils.serializeStringList(List.of("hello", "world")));
    }

    @Test
    public void testDeserializeStringList() {
        assertEquals(List.of(), StringUtils.deserializeStringList(null));
        assertEquals(List.of(), StringUtils.deserializeStringList("[]"));
        assertEquals(List.of(), StringUtils.deserializeStringList("[ ]"));
        assertThrows(JsonSyntaxException.class, () -> StringUtils.deserializeStringList("[\"]"));
        assertThrows(JsonSyntaxException.class, () -> StringUtils.deserializeStringList("[\"he\"llo\"]"));
        assertThrows(JsonSyntaxException.class, () -> StringUtils.deserializeStringList("\"hello\""));
        assertEquals(Arrays.asList((String) null), StringUtils.deserializeStringList("[null]"));
        assertEquals(List.of("hello"), StringUtils.deserializeStringList("[\"hello\"]"));
        assertEquals(List.of("he\"llo"), StringUtils.deserializeStringList("[\"he\\\"llo\"]"));
        assertEquals(List.of("hello", "world"), StringUtils.deserializeStringList("[\"hello\",\"world\"]"));
        assertEquals(List.of("hello", "world"), StringUtils.deserializeStringList("[\"hello\",\n\"world\"]"));
    }
}
