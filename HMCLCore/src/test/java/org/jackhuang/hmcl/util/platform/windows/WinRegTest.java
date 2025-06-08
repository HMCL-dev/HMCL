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

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
@EnabledIf("isEnabled")
public final class WinRegTest {
    public static boolean isEnabled() {
        return WinReg.INSTANCE != null;
    }

    private static final com.sun.jna.platform.win32.WinReg.HKEY ROOT_KEY = com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;
    private static final String KEY_BASE = "Software\\JavaSoft\\Prefs\\hmcl\\test";
    private static String key;

    private static final String[] SUBKEYS = {
            "Sub0", "Sub1", "Sub2", "Sub3"
    };

    private static final byte[] TEST_DATA = new byte[128];

    static {
        for (int i = 0; i < TEST_DATA.length; i++) {
            TEST_DATA[i] = (byte) i;
        }
    }

    @BeforeAll
    public static void setup() {
        key = KEY_BASE + "\\" + UUID.randomUUID();
        if (!Advapi32Util.registryCreateKey(ROOT_KEY, key))
            throw new AssertionError("Failed to create key");

        Advapi32Util.registrySetBinaryValue(ROOT_KEY, key, "BINARY", TEST_DATA);
        Advapi32Util.registrySetStringValue(ROOT_KEY, key, "SZ", "Hello World!");
        Advapi32Util.registrySetIntValue(ROOT_KEY, key, "DWORD", 0xCAFEBABE);
        Advapi32Util.registrySetLongValue(ROOT_KEY, key, "QWORD", 0xCAFEBABEL);

        for (String subkey : SUBKEYS) {
            if (!Advapi32Util.registryCreateKey(ROOT_KEY, key, subkey))
                throw new AssertionError("Failed to create key");
        }
    }

    @AfterAll
    public static void cleanUp() {
        if (key != null) {
            if (!Advapi32Util.registryKeyExists(ROOT_KEY, key))
                return;

            for (String subKey : Advapi32Util.registryGetKeys(ROOT_KEY, key))
                Advapi32Util.registryDeleteKey(ROOT_KEY, key, subKey);

            Advapi32Util.registryDeleteKey(ROOT_KEY, key);
        }
    }

    @Test
    public void testQueryValue() {
        WinReg.HKEY hkey = WinReg.HKEY.HKEY_CURRENT_USER;
        WinReg reg = WinReg.INSTANCE;

        assertArrayEquals(TEST_DATA, (byte[]) reg.queryValue(hkey, key, "BINARY"));
        assertEquals("Hello World!", reg.queryValue(hkey, key, "SZ"));
        assertEquals(0xCAFEBABE, reg.queryValue(hkey, key, "DWORD"));
        assertEquals(0xCAFEBABEL, reg.queryValue(hkey, key, "QWORD"));
        assertNull(reg.queryValue(hkey, key, "UNKNOWN"));
        assertNull(reg.queryValue(hkey, KEY_BASE + "\\" + "NOT_EXIST", "UNKNOWN"));
    }

    @Test
    public void testQuerySubKeys() {
        WinReg.HKEY hkey = WinReg.HKEY.HKEY_CURRENT_USER;
        WinReg reg = WinReg.INSTANCE;

        assertEquals(Arrays.asList(SUBKEYS).stream().map(it -> key + "\\" + it).collect(Collectors.toList()),
                reg.querySubKeys(hkey, key).stream().sorted().collect(Collectors.toList()));
        for (String subkey : SUBKEYS) {
            assertEquals(Collections.emptyList(), reg.querySubKeys(hkey, key + "\\" + subkey));
        }
        assertEquals(Collections.emptyList(), reg.querySubKeys(hkey, key + "\\NOT_EXIST"));
    }
}
