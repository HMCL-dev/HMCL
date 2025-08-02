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
package org.jackhuang.hmcl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class MainTest {
    @Test
    public void testGetJavaFeatureVersion() {
        assertEquals(6, Main.getJavaFeatureVersion("1.6.0"));
        assertEquals(6, Main.getJavaFeatureVersion("1.6.0_45"));
        assertEquals(7, Main.getJavaFeatureVersion("1.7.0"));
        assertEquals(7, Main.getJavaFeatureVersion("1.7.0_80"));
        assertEquals(8, Main.getJavaFeatureVersion("1.8"));
        assertEquals(8, Main.getJavaFeatureVersion("1.8u321"));
        assertEquals(8, Main.getJavaFeatureVersion("1.8.0_321"));
        assertEquals(11, Main.getJavaFeatureVersion("11"));
        assertEquals(11, Main.getJavaFeatureVersion("11.0.26"));
        assertEquals(21, Main.getJavaFeatureVersion("21"));
        assertEquals(26, Main.getJavaFeatureVersion("26-ea"));

        assertEquals(-1, Main.getJavaFeatureVersion(null));
        assertEquals(-1, Main.getJavaFeatureVersion(""));
        assertEquals(-1, Main.getJavaFeatureVersion("0"));
        assertEquals(-1, Main.getJavaFeatureVersion("0.8"));
        assertEquals(-1, Main.getJavaFeatureVersion("abc"));
        assertEquals(-1, Main.getJavaFeatureVersion("1.abc"));
        assertEquals(-1, Main.getJavaFeatureVersion(".1"));
        assertEquals(-1, Main.getJavaFeatureVersion("1111111111111111111111"));
    }
}
