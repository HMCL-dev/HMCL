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
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.io.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Glavo
 */
public final class OperatingSystemTest {
    @Test
    public void testIsNameValid() {
        assertTrue(FileUtils.isNameValid("example"));
        assertTrue(FileUtils.isNameValid("example.zip"));
        assertTrue(FileUtils.isNameValid("example.tar.gz"));
        assertTrue(FileUtils.isNameValid("\uD83D\uDE00"));
        assertTrue(FileUtils.isNameValid("a\uD83D\uDE00b"));

        assertFalse(FileUtils.isNameValid("."));
        assertFalse(FileUtils.isNameValid(".."));
        assertFalse(FileUtils.isNameValid("exam\0ple"));
        assertFalse(FileUtils.isNameValid("example/0"));

        // Test for invalid surrogate pair
        assertFalse(FileUtils.isNameValid("\uD83D"));
        assertFalse(FileUtils.isNameValid("\uDE00"));
        assertFalse(FileUtils.isNameValid("\uDE00\uD83D"));
        assertFalse(FileUtils.isNameValid("\uD83D\uD83D"));
        assertFalse(FileUtils.isNameValid("a\uD83D"));
        assertFalse(FileUtils.isNameValid("a\uDE00"));
        assertFalse(FileUtils.isNameValid("a\uDE00\uD83D"));
        assertFalse(FileUtils.isNameValid("a\uD83Db"));
        assertFalse(FileUtils.isNameValid("a\uDE00b"));
        assertFalse(FileUtils.isNameValid("a\uDE00\uD83Db"));
    }
}
