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
