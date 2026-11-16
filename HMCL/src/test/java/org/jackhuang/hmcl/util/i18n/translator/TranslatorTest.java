// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.i18n.translator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class TranslatorTest {

    //region lzh

    private static void assertYearToLZH(String value, int year) {
        StringBuilder builder = new StringBuilder(2);
        Translator_lzh.appendYear(builder, year);
        assertEquals(value, builder.toString());
    }

    @Test
    public void testYearToLZH() {
        assertYearToLZH("甲子", 1984);
        assertYearToLZH("乙巳", 2025);
        assertYearToLZH("甲子", -2996);
        assertYearToLZH("庚子", 1000);
    }

    @Test
    public void testHourToLZH() {
        List<String> list = List.of(
                "子正", "丑初", "丑正", "寅初", "寅正", "卯初", "卯正", "辰初", "辰正", "巳初", "巳正", "午初",
                "午正", "未初", "未正", "申初", "申正", "酉初", "酉正", "戌初", "戌正", "亥初", "亥正", "子初"
        );
        for (int hour = 0; hour < list.size(); hour++) {
            StringBuilder builder = new StringBuilder(2);
            Translator_lzh.appendHour(builder, hour);
            assertEquals(list.get(hour), builder.toString());
        }
    }

    //endregion
}
