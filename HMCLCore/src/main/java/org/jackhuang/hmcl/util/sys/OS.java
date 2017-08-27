/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util.sys;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import org.jackhuang.hmcl.api.HMCLog;

/**
 * @author huangyuhui
 */
public enum OS {

    LINUX('/', "linux"),
    WINDOWS('\\', "windows"),
    OSX('/', "osx"),
    UNKNOWN('/', "universal");

    public final char fileSeparator;
    public final String checkedName;

    private OS(char fileSeparator, String n) {
        this.fileSeparator = fileSeparator;
        checkedName = n;
    }

    public static OS os() {
        String str = System.getProperty("os.name").toLowerCase(Locale.US);
        if (str.contains("win"))
            return OS.WINDOWS;
        if (str.contains("mac"))
            return OS.OSX;
        if (str.contains("solaris"))
            return OS.LINUX;
        if (str.contains("sunos"))
            return OS.LINUX;
        if (str.contains("linux"))
            return OS.LINUX;
        if (str.contains("unix"))
            return OS.LINUX;
        return OS.UNKNOWN;
    }

    /**
     * @return Free Physical Memory Size (Byte)
     */
    public static long getTotalPhysicalMemory() {
        try {
            return ReflectionHelper.get(ManagementFactory.getOperatingSystemMXBean(), "getTotalPhysicalMemorySize");
        } catch (Throwable t) {
            HMCLog.warn("Failed to get total physical memory size", t);
            return 1024;
        }
    }

    public static int getSuggestedMemorySize() {
        long total = getTotalPhysicalMemory();
        int memory = (int) (total / 1024 / 1024) / 4;
        memory = Math.round((float) memory / 128.0f) * 128;
        return memory;
    }

    public static String getLinuxReleaseVersion() throws IOException {
        return FileUtils.read(new File("/etc/issue"));
    }

    public static String getSystemVersion() {
        if (os() == LINUX)
            try {
                return getLinuxReleaseVersion();
            } catch (IOException e) {
                HMCLog.warn("Failed to catch /etc/issue");
            }
        return System.getProperty("os.name") + " (" + System.getProperty("os.arch") + "), " + System.getProperty("os.version");
    }

}
