// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

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

    @Test
    public void testParsePosixFilePermission() {
        assertEquals(EnumSet.noneOf(PosixFilePermission.class), FileUtils.parsePosixFilePermission(0));
        assertEquals(EnumSet.noneOf(PosixFilePermission.class), FileUtils.parsePosixFilePermission(0100000));
        assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ),
                FileUtils.parsePosixFilePermission(0100644)
        );
        assertEquals(
                EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                ),
                FileUtils.parsePosixFilePermission(0100755)
        );
    }
}
