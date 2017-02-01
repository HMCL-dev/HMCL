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
package org.jackhuang.hellominecraft.util.sys;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.StringTokenizer;
import org.jackhuang.hellominecraft.util.code.Charsets;
import org.jackhuang.hellominecraft.util.log.HMCLog;

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
        return OS.UNKOWN;
    }

    /**
     * @return Free Physical Memory Size (Byte)
     */
    public static long getTotalPhysicalMemory() {
        try {
            if (os() == LINUX)
                return memoryInfoForLinux()[0] * 1024;
            else {
                OperatingSystemMXBean o = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                return o.getTotalPhysicalMemorySize();
            }
        } catch (Throwable t) {
            HMCLog.warn("Failed to get total physical memory size", t);
            return -1;
        }
    }

    public static int getSuggestedMemorySize() {
        long total = getTotalPhysicalMemory();
        if (total == -1)
            return 1024;
        int memory = (int) (total / 1024 / 1024) / 4;
        memory = Math.round((float) memory / 128.0f) * 128;
        return memory;
    }

    public static long[] memoryInfoForLinux() throws IOException {
        File file = new File("/proc/meminfo");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(FileUtils.openInputStream(file), Charsets.UTF_8))) {
            long[] result = new long[4];
            String str;
            StringTokenizer token;
            while ((str = br.readLine()) != null) {
                token = new StringTokenizer(str);
                if (!token.hasMoreTokens())
                    continue;

                str = token.nextToken();
                if (!token.hasMoreTokens())
                    continue;

                if (str.equalsIgnoreCase("MemTotal:"))
                    result[0] = Long.parseLong(token.nextToken());
                else if (str.equalsIgnoreCase("MemFree:"))
                    result[1] = Long.parseLong(token.nextToken());
                else if (str.equalsIgnoreCase("SwapTotal:"))
                    result[2] = Long.parseLong(token.nextToken());
                else if (str.equalsIgnoreCase("SwapFree:"))
                    result[3] = Long.parseLong(token.nextToken());
            }

            return result;
        }
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
