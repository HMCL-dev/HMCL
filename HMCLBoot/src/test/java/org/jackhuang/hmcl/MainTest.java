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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MainTest {
    @Test
    public void testCheckJavaVersion() {
        assertFalse(Main.checkJavaVersion("1.6.0"));
        assertFalse(Main.checkJavaVersion("1.6.0_45"));
        assertFalse(Main.checkJavaVersion("1.7.0"));
        assertFalse(Main.checkJavaVersion("1.7.0_80"));
        assertFalse(Main.checkJavaVersion("1.8"));
        assertFalse(Main.checkJavaVersion("1.8.0_321"));

        assertTrue(Main.checkJavaVersion("11"));
        assertTrue(Main.checkJavaVersion("11.0.26"));
        assertTrue(Main.checkJavaVersion("21"));
    }
}
