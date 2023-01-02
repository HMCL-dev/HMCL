/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.io.File;
import java.util.Map;

/**
 * Utilize the Base Directory Specification
 * For default values and the specification, please refer to the freedesktop XDG
 * Base Directory Specification.
 */
public class XDGBaseDirectory {
    private static final String XDG_CACHE_HOME = "XDG_CACHE_HOME";
    private static final String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";
    private static final String XDG_CONFIG_DIRS = "XDG_CONFIG_DIRS";
    private static final String XDG_DATA_HOME = "XDG_DATA_HOME";
    private static final String XDG_DATA_DIRS = "XDG_DATA_DIRS";
    private static final String XDG_RUNTIME_DIR = "XDG_RUNTIME_DIR";

    private XDGBaseDirectory() {}



    private static Map<String, String> environment = System.getenv();

    public static String getCacheHome() {
        String value = environment.get(XDG_CACHE_HOME);
        if (value == null || value.trim().length() == 0) {
            String XDG_CACHE_HOME_DEFAULT = environment.get("HOME") + File.separator + ".cache";
            value = XDG_CACHE_HOME_DEFAULT;
        }
        return value;
    }

    public static String getConfigHome() {
        String value = environment.get(XDG_CONFIG_HOME);
        if (value == null || value.trim().length() == 0) {
            String XDG_CONFIG_HOME_DEFAULT = environment.get("HOME") + File.separator + ".config";
            value = XDG_CONFIG_HOME_DEFAULT;
        }
        return value;
    }

    public static String getConfigDirs() {
        String value = environment.get(XDG_CONFIG_DIRS);
        if (value == null || value.trim().length() == 0) {
            String XDG_CONFIG_DIRS_DEFAULT = File.separator + "etc" + File.separator + "xdg";
            value = XDG_CONFIG_DIRS_DEFAULT;
        }
        return value;
    }

    public static String getDataHome() {
        String value = environment.get(XDG_DATA_HOME);
        if (value == null || value.trim().length() == 0) {
            String XDG_DATA_HOME_DEFAULT = environment.get("HOME") +
                    File.separator + ".local" + File.separator + "share";
            value = XDG_DATA_HOME_DEFAULT;
        }
        return value;
    }

    public static String getDataDirs() {
        String value = environment.get(XDG_DATA_DIRS);
        if (value == null || value.trim().length() == 0) {
            String XDG_DATA_DIRS_DEFAULT = File.separator + "usr" + File.separator + "local" + File.separator + "share"
                    + File.separator;
            XDG_DATA_DIRS_DEFAULT = XDG_DATA_DIRS_DEFAULT + File.pathSeparator;
            XDG_DATA_DIRS_DEFAULT = XDG_DATA_DIRS_DEFAULT + File.separator + "usr" + File.separator + "share"
                    + File.separator;
            value = XDG_DATA_DIRS_DEFAULT;
        }
        return value;
    }

    public static String getRuntimeDir() {
        String value = environment.get(XDG_RUNTIME_DIR);
        return value;
    }

}
