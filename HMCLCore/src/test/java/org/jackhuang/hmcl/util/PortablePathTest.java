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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for [PortablePath].
@NotNullByDefault
public final class PortablePathTest {
    /// Tests that relative paths use portable separators.
    @Test
    public void normalizesRelativeSeparators() {
        PortablePath path = PortablePath.of("versions\\1.20.1");

        assertEquals("versions/1.20.1", path.getPath());
        assertFalse(path.isAbsolute());
    }

    /// Tests that absolute paths keep their original separators.
    @Test
    public void preservesAbsoluteSeparators() {
        PortablePath windowsPath = PortablePath.of("C:\\Users\\Name");
        PortablePath posixPath = PortablePath.of("/home/user");

        assertEquals("C:\\Users\\Name", windowsPath.getPath());
        assertTrue(windowsPath.isAbsolute());
        assertEquals("/home/user", posixPath.getPath());
        assertTrue(posixPath.isAbsolute());
    }

    /// Tests that the string representation is the stored path.
    @Test
    public void returnsPathAsString() {
        PortablePath path = PortablePath.of("game\\dir");

        assertEquals(path.getPath(), path.toString());
    }
}
