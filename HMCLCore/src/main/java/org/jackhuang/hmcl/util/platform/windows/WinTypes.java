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

import com.sun.jna.*;

import java.util.Arrays;
import java.util.List;

/**
 * @author Glavo
 */
public interface WinTypes {
    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/winnt/ns-winnt-osversioninfoexw">OSVERSIONINFOEXW structure</a>
     */
    final class OSVERSIONINFOEXW extends Structure {
        public int dwOSVersionInfoSize;
        public int dwMajorVersion;
        public int dwMinorVersion;
        public int dwBuildNumber;
        public int dwPlatformId;
        public char[] szCSDVersion;
        public short wServicePackMajor;
        public short wServicePackMinor;
        public short wSuiteMask;
        public byte wProductType;
        public byte wReserved;

        public OSVERSIONINFOEXW() {
            szCSDVersion = new char[128];
            dwOSVersionInfoSize = size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dwOSVersionInfoSize",
                    "dwMajorVersion", "dwMinorVersion", "dwBuildNumber",
                    "dwPlatformId",
                    "szCSDVersion",
                    "wServicePackMajor", "wServicePackMinor",
                    "wSuiteMask", "wProductType",
                    "wReserved"
            );
        }
    }

    /**
     * @see <a href="https://learn.microsoft.com/windows/win32/api/sysinfoapi/ns-sysinfoapi-memorystatusex">MEMORYSTATUSEX structure</a>
     */
    final class MEMORYSTATUSEX extends Structure {
        public int dwLength;
        public int dwMemoryLoad;
        public long ullTotalPhys;
        public long ullAvailPhys;
        public long ullTotalPageFile;
        public long ullAvailPageFile;
        public long ullTotalVirtual;
        public long ullAvailVirtual;
        public long ullAvailExtendedVirtual;

        public MEMORYSTATUSEX() {
            dwLength = size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dwLength", "dwMemoryLoad",
                    "ullTotalPhys", "ullAvailPhys", "ullTotalPageFile", "ullAvailPageFile",
                    "ullTotalVirtual", "ullAvailVirtual", "ullAvailExtendedVirtual");
        }
    }

    ;
}
