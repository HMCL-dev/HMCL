// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.jackhuang.hmcl.util.platform.NativeUtils;

/**
 * @author Glavo
 */
public interface Kernel32 extends StdCallLibrary {

    Kernel32 INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isWindows()
            ? NativeUtils.load("kernel32", Kernel32.class)
            : null;

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/errhandlingapi/nf-errhandlingapi-getlasterror">GetLastError function</a>
     */
    int GetLastError();

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/sysinfoapi/nf-sysinfoapi-getversionexw">GetVersionExW function</a>
     */
    boolean GetVersionExW(WinTypes.OSVERSIONINFOEXW lpVersionInfo);

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winnls/nf-winnls-getacp">GetACP function</a>
     */
    int GetACP();

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winnls/nf-winnls-getusergeoid">GetUserGeoID function</a>
     */
    int GetUserGeoID(int geoClass);

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/sysinfoapi/nf-sysinfoapi-globalmemorystatusex">GlobalMemoryStatusEx function</a>
     */
    boolean GlobalMemoryStatusEx(WinTypes.MEMORYSTATUSEX lpBuffer);

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/sysinfoapi/nf-sysinfoapi-getlogicalprocessorinformationex">GetLogicalProcessorInformationEx function</a>
     */
    boolean GetLogicalProcessorInformationEx(int relationshipType, Pointer buffer, IntByReference returnedLength);
}
