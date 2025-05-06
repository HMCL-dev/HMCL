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
public final class WinReg {

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
    }



}
