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

    // https://learn.microsoft.com/windows/win32/sysinfo/registry-key-security-and-access-rights
    int KEY_READ = 0x20019;

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

    int GEOCLASS_NATION = 16;
    int GEOCLASS_REGION = 14;
    int GEOCLASS_ALL = 0;
}
