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

package org.jackhuang.hmcl.util.platform;

import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.java.JavaInfo.parseVersion;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public final class JavaRuntimeTest {
    @Test
    public void testParseVersion() {
        assertEquals(8, parseVersion("1.8.0_302"));
        assertEquals(8, parseVersion("1.8-internal"));
        assertEquals(8, parseVersion("8.0"));
        assertEquals(11, parseVersion("11"));
        assertEquals(11, parseVersion("11.0.12"));
        assertEquals(11, parseVersion("11-internal"));
        assertEquals(11, parseVersion("11+abc"));

        assertEquals(-1, parseVersion("abc"));
        assertEquals(-1, parseVersion("1."));
        assertEquals(-1, parseVersion("1.-internal"));
        assertEquals(-1, parseVersion(""));
    }
}
