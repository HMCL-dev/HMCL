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
package org.jackhuang.hmcl.util.platform.windows;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class WindowsVersionTest {

    @Test
    public void testParse() {
        assertEquals(WindowsVersion.WINDOWS_7, new WindowsVersion("6.1"));
        assertEquals(WindowsVersion.WINDOWS_10, new WindowsVersion("10.0"));
        assertEquals(new WindowsVersion(10, 0, 26120), new WindowsVersion("10.0.26120"));
        assertEquals(new WindowsVersion(10, 0, 26120), new WindowsVersion("10.0.26120-unknown"));
        assertEquals(new WindowsVersion(10, 0, 26120, 3964), new WindowsVersion("10.0.26120.3964"));
        assertEquals(new WindowsVersion(10, 0, 26120, 3964), new WindowsVersion("10.0.26120.3964-unknown"));

        assertEquals(WindowsVersion.UNKNOWN, new WindowsVersion("unknown"));
    }
}
