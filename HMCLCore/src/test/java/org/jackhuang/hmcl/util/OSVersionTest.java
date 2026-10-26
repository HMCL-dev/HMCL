// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.platform.OSVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// @author Glavo
public class OSVersionTest {

    @Test
    public void testNotAcceptSystem() {
        assertThrows(IllegalArgumentException.class, () -> new OSVersion.Generic(OperatingSystem.WINDOWS, VersionNumber.ZERO));
    }

    @Test
    public void testParse() {
        assertEquals(OSVersion.WINDOWS_7, OSVersion.Windows.parse("6.1"));
        assertEquals(OSVersion.WINDOWS_10, OSVersion.Windows.parse("10.0"));
        assertEquals(new OSVersion.Windows(10, 0, 26120), OSVersion.Windows.parse("10.0.26120"));
        assertEquals(new OSVersion.Windows(10, 0, 26120), OSVersion.Windows.parse("10.0.26120-unknown"));
        assertEquals(new OSVersion.Windows(10, 0, 26120, 3964), OSVersion.Windows.parse("10.0.26120.3964"));
        assertEquals(new OSVersion.Windows(10, 0, 26120, 3964), OSVersion.Windows.parse("10.0.26120.3964-unknown"));
    }

    @Test
    public void testIsAtLeast() {
        assertTrue(OSVersion.WINDOWS_10.isAtLeast(OSVersion.WINDOWS_7));
        assertTrue(OSVersion.WINDOWS_10.isAtLeast(OSVersion.WINDOWS_10));
        assertFalse(OSVersion.WINDOWS_10.isAtLeast(OSVersion.WINDOWS_11));
        assertFalse(OSVersion.WINDOWS_10.isAtLeast(OSVersion.of(OperatingSystem.LINUX, "4.0")));

        assertTrue(OSVersion.of(OperatingSystem.LINUX, "4.0").isAtLeast(OSVersion.of(OperatingSystem.LINUX, "4.0")));
        assertTrue(OSVersion.of(OperatingSystem.LINUX, "4.0").isAtLeast(OSVersion.of(OperatingSystem.LINUX, "3.0")));
        assertFalse(OSVersion.of(OperatingSystem.LINUX, "4.0").isAtLeast(OSVersion.of(OperatingSystem.LINUX, "5.0")));
        assertFalse(OSVersion.of(OperatingSystem.LINUX, "4.0").isAtLeast(OSVersion.of(OperatingSystem.WINDOWS, "4.0")));
    }
}
