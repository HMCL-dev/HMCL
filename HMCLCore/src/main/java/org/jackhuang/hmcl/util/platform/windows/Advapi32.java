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

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.jackhuang.hmcl.util.platform.NativeUtils;

/**
 * @author Glavo
 */
public interface Advapi32 extends StdCallLibrary {

    Advapi32 INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isWindows()
            ? NativeUtils.load("advapi32", Advapi32.class)
            : null;

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winreg/nf-winreg-regopenkeyexw">RegOpenKeyExW function</a>
     */
    int RegOpenKeyExW(Pointer hKey, WString lpSubKey, int ulOptions, int samDesired, PointerByReference phkResult);

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winreg/nf-winreg-regclosekey">RegCloseKey function</a>
     */
    int RegCloseKey(Pointer hKey);

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winreg/nf-winreg-regqueryvalueexw">RegQueryValueExW function</a>
     */
    int RegQueryValueExW(Pointer hKey, WString lpValueName, Pointer lpReserved, IntByReference lpType, Pointer lpData, IntByReference lpcbData);

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winreg/nf-winreg-regenumkeyexw">RegEnumKeyExW function</a>
     */
    int RegEnumKeyExW(Pointer hKey, int dwIndex,
                      Pointer lpName, IntByReference lpcchName,
                      IntByReference lpReserved,
                      Pointer lpClass, IntByReference lpcchClass,
                      Pointer lpftLastWriteTime);
}
