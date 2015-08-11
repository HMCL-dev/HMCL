/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.utils.system;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import org.jackhuang.hellominecraft.HMCLog;

/**
 * @author huangyuhui
 */
public enum OS {

    LINUX('/', "linux"),
    WINDOWS('\\', "windows"),
    OSX('/', "osx"),
    UNKOWN('/', "universal");

    public final char fileSeparator;
    public final String checked_name;

    private OS(char fileSeparator, String n) {
        this.fileSeparator = fileSeparator;
        checked_name = n;
    }

    public static OS os() {
        String str;
        if ((str = System.getProperty("os.name").toLowerCase())
                .contains("win"))
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
        return OS.UNKOWN;
    }

    /**
     * @return Free Physical Memory Size (Byte)
     */
    public static long getTotalPhysicalMemory() {
        try {
            OperatingSystemMXBean o = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return o.getTotalPhysicalMemorySize();
        } catch (Throwable t) {
            HMCLog.warn("Failed to get total physical memory size");
            return -1;
        }
    }

}
