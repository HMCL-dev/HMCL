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
package org.jackhuang.hmcl.util.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class WenyanUtilsTest {

    private static void assertYearToString(String value, int year) {
        StringBuilder builder = new StringBuilder(2);
        WenyanUtils.appendYear(builder, year);
        assertEquals(value, builder.toString());
    }

    @Test
    public void testYearToString() {
        assertYearToString("甲子", 1984);
        assertYearToString("乙巳", 2025);
        assertYearToString("甲子", -2996);
        assertYearToString("庚子", 1000);
    }
}
