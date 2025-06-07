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

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.jackhuang.hmcl.util.platform.NativeUtils;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public abstract class WinReg {

    public static final WinReg INSTANCE = NativeUtils.USE_JNA && Advapi32.INSTANCE != null
            ? new JNAWinReg(Advapi32.INSTANCE) : null;

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/sysinfo/predefined-keys">Predefined Keys</a>
     */
    public enum HKEY {
        HKEY_CLASSES_ROOT(0x80000000),
        HKEY_CURRENT_USER(0x80000001),
        HKEY_LOCAL_MACHINE(0x80000002),
        HKEY_USERS(0x80000003),
        HKEY_PERFORMANCE_DATA(0x80000004),
        HKEY_PERFORMANCE_TEXT(0x80000050),
        HKEY_PERFORMANCE_NLSTEXT(0x80000060),
        HKEY_CURRENT_CONFIG(0x80000005),
        HKEY_DYN_DATA(0x80000006),
        HKEY_CURRENT_USER_LOCAL_SETTINGS(0x80000007);
        private final int value;

        HKEY(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public Pointer toPointer() {
            return Pointer.createConstant((long) value);
        }
    }

    public abstract boolean exists(HKEY root, String key);

    public abstract Object queryValue(HKEY root, String key, String valueName);

    public abstract List<String> querySubKeyNames(HKEY root, String key);

    public List<String> querySubKeys(HKEY root, String key) {
        List<String> list = querySubKeyNames(root, key);
        if (list.isEmpty())
            return list;

        if (!(list instanceof ArrayList))
            list = new ArrayList<>(list);

        String prefix = key.endsWith("\\") ? key : key + "\\";
        list.replaceAll(str -> prefix + str);
        return list;
    }

    private static final class JNAWinReg extends WinReg {

        private final Advapi32 advapi32;

        JNAWinReg(Advapi32 advapi32) {
            this.advapi32 = advapi32;
        }

        @Override
        public boolean exists(HKEY root, String key) {
            PointerByReference phkKey = new PointerByReference();
            int status = advapi32.RegOpenKeyExW(root.toPointer(), new WString(key), 0, WinConstants.KEY_READ, phkKey);

            if (status == WinConstants.ERROR_SUCCESS) {
                advapi32.RegCloseKey(phkKey.getValue());
                return true;
            } else
                return false;
        }

        private static void checkLength(int expected, int actual) {
            if (expected != actual) {
                throw new IllegalStateException("Expected " + expected + " bytes, but got " + actual);
            }
        }

        @Override
        public Object queryValue(HKEY root, String key, String valueName) {
            PointerByReference phkKey = new PointerByReference();
            if (advapi32.RegOpenKeyExW(root.toPointer(), new WString(key), 0, WinConstants.KEY_READ, phkKey) != WinConstants.ERROR_SUCCESS)
                return null;

            Pointer hkey = phkKey.getValue();
            try {
                IntByReference lpType = new IntByReference();
                IntByReference lpcbData = new IntByReference();
                int status = advapi32.RegQueryValueExW(hkey, new WString(valueName), null, lpType, null, lpcbData);
                if (status != WinConstants.ERROR_SUCCESS) {
                    if (status == WinConstants.ERROR_FILE_NOT_FOUND)
                        return null;
                    else
                        throw new RuntimeException("Failed to query value: " + status);
                }

                int type = lpType.getValue();
                int cbData = lpcbData.getValue();

                try (Memory lpData = new Memory(cbData)) {
                    status = advapi32.RegQueryValueExW(hkey, new WString(valueName), null, null, lpData, lpcbData);
                    if (status != WinConstants.ERROR_SUCCESS) {
                        if (status == WinConstants.ERROR_FILE_NOT_FOUND)
                            return null;
                        else
                            throw new RuntimeException("Failed to query value: " + status);
                    }

                    checkLength(cbData, lpcbData.getValue());

                    switch (type) {
                        case WinConstants.REG_NONE:
                        case WinConstants.REG_BINARY:
                            return lpData.getByteArray(0L, cbData);
                        case WinConstants.REG_DWORD_LITTLE_ENDIAN:
                        case WinConstants.REG_DWORD_BIG_ENDIAN: {
                            checkLength(4, cbData);
                            int value = lpData.getInt(0L);
                            ByteOrder expectedOrder = type == WinConstants.REG_DWORD_LITTLE_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                            if (expectedOrder != ByteOrder.nativeOrder())
                                value = Integer.reverseBytes(value);
                            return value;
                        }
                        case WinConstants.REG_QWORD_LITTLE_ENDIAN: {
                            checkLength(8, cbData);
                            long value = lpData.getLong(0L);
                            if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN)
                                value = Long.reverseBytes(value);
                            return value;
                        }
                        case WinConstants.REG_SZ:
                        case WinConstants.REG_EXPAND_SZ:
                        case WinConstants.REG_LINK: {
                            if (cbData < 2)
                                throw new RuntimeException("Illegal length: " + cbData);
                            if (lpData.getChar(cbData - 2) != '\0')
                                throw new RuntimeException("The string does not end with \\0");

                            return lpData.getWideString(0L);
                        }
                        default:
                            throw new RuntimeException("Unknown reg type: " + type);
                    }
                }
            } catch (Throwable e) {
                LOG.warning("Failed to query value", e);
            } finally {
                advapi32.RegCloseKey(hkey);
            }

            return null;
        }

        @Override
        public List<String> querySubKeyNames(HKEY root, String key) {
            PointerByReference phkKey = new PointerByReference();
            if (advapi32.RegOpenKeyExW(root.toPointer(), new WString(key), 0, WinConstants.KEY_READ, phkKey) != WinConstants.ERROR_SUCCESS)
                return Collections.emptyList();

            Pointer hkey = phkKey.getValue();
            try {
                ArrayList<String> res = new ArrayList<>();
                int maxKeyLength = 256;
                try (Memory lpName = new Memory(maxKeyLength * 2)) {
                    IntByReference lpcchName = new IntByReference();
                    int i = 0;

                    while (true) {
                        lpcchName.setValue(maxKeyLength);
                        int status = advapi32.RegEnumKeyExW(hkey, i, lpName, lpcchName, null, null, null, null);
                        if (status == WinConstants.ERROR_SUCCESS) {
                            res.add(lpName.getWideString(0L));
                            i++;
                        } else {
                            if (status != WinConstants.ERROR_NO_MORE_ITEMS)
                                LOG.warning("Failed to enum key: " + status);
                            break;
                        }
                    }
                }
                return res;
            } catch (Throwable e) {
                LOG.warning("Failed to query keys", e);
            } finally {
                advapi32.RegCloseKey(hkey);
            }

            return Collections.emptyList();
        }
    }

}
