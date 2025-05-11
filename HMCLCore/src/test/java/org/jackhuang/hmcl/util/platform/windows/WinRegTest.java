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

import com.sun.jna.platform.win32.Advapi32Util;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
@EnabledIf("isEnabled")
public final class WinRegTest {
    public static boolean isEnabled() {
        return WinReg.INSTANCE != null;
    }

    private static final byte[] TEST_DATA = new byte[128];

    static {
        for (int i = 0; i < TEST_DATA.length; i++) {
            TEST_DATA[i] = (byte) i;
        }
    }

    private static String key;

    @BeforeAll
    public static void setup() {
        com.sun.jna.platform.win32.WinReg.HKEY hkey = com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;
        key = "Software\\JavaSoft\\Prefs\\hmcl\\test\\" + UUID.randomUUID();
        if (!Advapi32Util.registryCreateKey(hkey, key)) {
            throw new AssertionError("Failed to create key");
        }

        Advapi32Util.registrySetBinaryValue(hkey, key, "BINARY", TEST_DATA);
        Advapi32Util.registrySetStringValue(hkey, key, "SZ", "Hello World!");
        Advapi32Util.registrySetIntValue(hkey, key, "DWORD", 0xCAFEBABE);
        Advapi32Util.registrySetLongValue(hkey, key, "QWORD", 0xCAFEBABEL);
    }

    @AfterAll
    public static void cleanUp() {
        if (key != null)
            Advapi32Util.registryDeleteKey(com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key);
    }

    @Test
    public void testQuery() {
        WinReg.HKEY hkey = WinReg.HKEY.HKEY_CURRENT_USER;
        WinReg reg = WinReg.INSTANCE;

        assertArrayEquals(TEST_DATA, (byte[]) reg.queryValue(hkey, key, "BINARY"));
        assertEquals("Hello World!", reg.queryValue(hkey, key, "SZ"));
        assertEquals(0xCAFEBABE, reg.queryValue(hkey, key, "DWORD"));
        assertEquals(0xCAFEBABEL, reg.queryValue(hkey, key, "QWORD"));
        assertNull(reg.queryValue(hkey, key, "UNKNOWN"));
    }
}
