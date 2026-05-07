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

/**
 * @author Glavo
 */
public interface WinConstants {

    // https://learn.microsoft.com/windows/win32/debug/system-error-codes--0-499-
    int ERROR_SUCCESS = 0;
    int ERROR_FILE_NOT_FOUND = 2;
    int ERROR_PATH_NOT_FOUND = 3;
    int ERROR_ACCESS_DENIED = 5;
    int ERROR_INVALID_HANDLE = 6;
    int ERROR_INVALID_DATA = 13;
    int ERROR_NOT_SAME_DEVICE = 17;
    int ERROR_NOT_READY = 21;
    int ERROR_SHARING_VIOLATION = 32;
    int ERROR_FILE_EXISTS = 80;
    int ERROR_INVALID_PARAMETER = 87;
    int ERROR_DISK_FULL = 112;
    int ERROR_INSUFFICIENT_BUFFER = 122;
    int ERROR_INVALID_LEVEL = 124;
    int ERROR_DIR_NOT_ROOT = 144;
    int ERROR_DIR_NOT_EMPTY = 145;
    int ERROR_ALREADY_EXISTS = 183;
    int ERROR_MORE_DATA = 234;
    int ERROR_NO_MORE_ITEMS = 259;
    int ERROR_DIRECTORY = 267;
    int ERROR_NOTIFY_ENUM_DIR = 1022;
    int ERROR_PRIVILEGE_NOT_HELD = 1314;
    int ERROR_NONE_MAPPED = 1332;
    int ERROR_CANT_ACCESS_FILE = 1920;
    int ERROR_NOT_A_REPARSE_POINT = 4390;
    int ERROR_INVALID_REPARSE_DATA = 4392;

    // https://learn.microsoft.com/windows/win32/sysinfo/registry-key-security-and-access-rights
    int KEY_QUERY_VALUE = 0x0001;
    int KEY_ENUMERATE_SUB_KEYS = 0x0008;
    int KEY_READ = 0x20019;

    // https://learn.microsoft.com/windows/win32/sysinfo/registry-value-types
    int REG_NONE = 0;
    int REG_SZ = 1;
    int REG_EXPAND_SZ = 2;
    int REG_BINARY = 3;
    int REG_DWORD_LITTLE_ENDIAN = 4;
    int REG_DWORD_BIG_ENDIAN = 5;
    int REG_LINK = 6;
    int REG_MULTI_SZ = 7;
    int REG_RESOURCE_LIST = 8;
    int REG_FULL_RESOURCE_DESCRIPTOR = 9;
    int REG_RESOURCE_REQUIREMENTS_LIST = 10;
    int REG_QWORD_LITTLE_ENDIAN = 11;

    // https://learn.microsoft.com/windows/win32/sysinfo/predefined-keys
    long HKEY_CLASSES_ROOT = 0x80000000L;
    long HKEY_CURRENT_USER = 0x80000001L;
    long HKEY_LOCAL_MACHINE = 0x80000002L;
    long HKEY_USERS = 0x80000003L;
    long HKEY_PERFORMANCE_DATA = 0x80000004L;
    long HKEY_PERFORMANCE_TEXT = 0x80000050L;
    long HKEY_PERFORMANCE_NLSTEXT = 0x80000060L;
    long HKEY_CURRENT_CONFIG = 0x80000005L;
    long HKEY_DYN_DATA = 0x80000006L;
    long HKEY_CURRENT_USER_LOCAL_SETTINGS = 0x80000007L;

    // https://learn.microsoft.com/windows/win32/api/winnls/ne-winnls-sysgeoclass
    int GEOCLASS_NATION = 16;
    int GEOCLASS_REGION = 14;
    int GEOCLASS_ALL = 0;

    // https://learn.microsoft.com/windows/win32/api/winnt/ne-winnt-logical_processor_relationship
    int RelationProcessorCore = 0;
    int RelationNumaNode = 1;
    int RelationCache = 2;
    int RelationProcessorPackage = 3;
    int RelationGroup = 4;
    int RelationProcessorDie = 5;
    int RelationNumaNodeEx = 6;
    int RelationProcessorModule = 7;
    int RelationAll = 0xffff;

    // https://learn.microsoft.com/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute
    int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

}
