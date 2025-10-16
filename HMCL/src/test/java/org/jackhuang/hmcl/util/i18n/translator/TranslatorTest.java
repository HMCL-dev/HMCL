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
