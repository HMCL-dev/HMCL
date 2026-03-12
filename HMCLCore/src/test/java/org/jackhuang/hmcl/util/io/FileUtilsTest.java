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

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/// @author Glavo
public class FileUtilsTest {

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    public void testIsNameValid(OperatingSystem os) {
        assertTrue(FileUtils.isNameValid(os, "example"));
        assertTrue(FileUtils.isNameValid(os, "example.zip"));
        assertTrue(FileUtils.isNameValid(os, "example.tar.gz"));
        assertTrue(FileUtils.isNameValid(os, "\uD83D\uDE00"));
        assertTrue(FileUtils.isNameValid(os, "a\uD83D\uDE00b"));

        assertFalse(FileUtils.isNameValid(os, ""));
        assertFalse(FileUtils.isNameValid(os, "."));
        assertFalse(FileUtils.isNameValid(os, ".."));
        assertFalse(FileUtils.isNameValid(os, "exam\0ple"));
        assertFalse(FileUtils.isNameValid(os, "example/0"));

        // Test for invalid surrogate pair
        assertFalse(FileUtils.isNameValid(os, "\uD83D"));
        assertFalse(FileUtils.isNameValid(os, "\uDE00"));
        assertFalse(FileUtils.isNameValid(os, "\uDE00\uD83D"));
        assertFalse(FileUtils.isNameValid(os, "\uD83D\uD83D"));
        assertFalse(FileUtils.isNameValid(os, "a\uD83D"));
        assertFalse(FileUtils.isNameValid(os, "a\uDE00"));
        assertFalse(FileUtils.isNameValid(os, "a\uDE00\uD83D"));
        assertFalse(FileUtils.isNameValid(os, "a\uD83Db"));
        assertFalse(FileUtils.isNameValid(os, "a\uDE00b"));
        assertFalse(FileUtils.isNameValid(os, "a\uDE00\uD83Db"));
        assertFalse(FileUtils.isNameValid(os, "f:oo"));

        // Platform-specific tests
        boolean isWindows = os == OperatingSystem.WINDOWS;
        boolean isNotWindows = !isWindows;
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "com1"));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "com1.txt"));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "foo."));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "foo "));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "f<oo"));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "f>oo"));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "f?oo"));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "f*oo"));
        assertEquals(isNotWindows, FileUtils.isNameValid(os, "f\\oo"));
    }
}
