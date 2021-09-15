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
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the operating system.
 *
 * @author huangyuhui
 */
public enum OperatingSystem {
    /**
     * Microsoft Windows.
     */
    WINDOWS("windows"),
    /**
     * Linux and Unix like OS, including Solaris.
     */
    LINUX("linux"),
    /**
     * Mac OS X.
     */
    OSX("osx"),
    /**
     * Unknown operating system.
     */
    UNKNOWN("universal");

    private final String checkedName;

    OperatingSystem(String checkedName) {
        this.checkedName = checkedName;
    }

    public String getCheckedName() {
        return checkedName;
    }

    /**
     * The current operating system.
     */
    public static final OperatingSystem CURRENT_OS;

    /**
     * The total memory/MB this computer have.
     */
    public static final int TOTAL_MEMORY;

    /**
     * The suggested memory size/MB for Minecraft to allocate.
     */
    public static final int SUGGESTED_MEMORY;

    public static final String PATH_SEPARATOR = File.pathSeparator;
    public static final String FILE_SEPARATOR = File.separator;
    public static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * The system default encoding.
     */
    public static final String ENCODING = System.getProperty("sun.jnu.encoding", Charset.defaultCharset().name());

    /**
     * The version of current operating system.
     */
    public static final String SYSTEM_VERSION = System.getProperty("os.version");

    public static final Pattern INVALID_RESOURCE_CHARACTERS;
    private static final String[] INVALID_RESOURCE_BASENAMES;
    private static final String[] INVALID_RESOURCE_FULLNAMES;

    private static final Pattern MEMINFO_PATTERN = Pattern.compile("^(?<key>.*?):\\s+(?<value>\\d+) kB?$");

    static {
        CURRENT_OS = parseOS(System.getProperty("os.name"));

        TOTAL_MEMORY = getPhysicalMemoryStatus()
                .map(PhysicalMemoryStatus::getTotal)
                .map(bytes -> (int) (bytes / 1024 / 1024))
                .orElse(1024);

        SUGGESTED_MEMORY = (int) (Math.round(1.0 * TOTAL_MEMORY / 4.0 / 128.0) * 128);

        // setup the invalid names
        if (CURRENT_OS == WINDOWS) {
            // valid names and characters taken from http://msdn.microsoft.com/library/default.asp?url=/library/en-us/fileio/fs/naming_a_file.asp
            INVALID_RESOURCE_CHARACTERS = Pattern.compile("[/\"<>|?*:\\\\]");
            INVALID_RESOURCE_BASENAMES = new String[]{"aux", "com1", "com2", "com3", "com4",
                    "com5", "com6", "com7", "com8", "com9", "con", "lpt1", "lpt2",
                    "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "nul", "prn"};
            Arrays.sort(INVALID_RESOURCE_BASENAMES);
            //CLOCK$ may be used if an extension is provided
            INVALID_RESOURCE_FULLNAMES = new String[]{"clock$"};
        } else {
            //only front slash and null char are invalid on UNIXes
            //taken from http://www.faqs.org/faqs/unix-faq/faq/part2/section-2.html
            INVALID_RESOURCE_CHARACTERS = null;
            INVALID_RESOURCE_BASENAMES = null;
            INVALID_RESOURCE_FULLNAMES = null;
        }
    }

    public static OperatingSystem parseOS(String name) {
        if (name == null) {
            return UNKNOWN;
        }

        name = name.trim().toLowerCase(Locale.ROOT);

        if (name.contains("win"))
            return WINDOWS;
        else if (name.contains("mac"))
            return OSX;
        else if (name.contains("solaris") || name.contains("linux") || name.contains("unix") || name.contains("sunos"))
            return LINUX;
        else
            return UNKNOWN;
    }

    public static Optional<PhysicalMemoryStatus> getPhysicalMemoryStatus() {
        if (CURRENT_OS == LINUX) {
            try {
                long free = 0, available = 0, total = 0;
                for (String line : Files.readAllLines(Paths.get("/proc/meminfo"))) {
                    Matcher matcher = MEMINFO_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String key = matcher.group("key");
                        String value = matcher.group("value");
                        if ("MemAvailable".equals(key)) {
                            available = Long.parseLong(value) * 1024;
                        }
                        if ("MemFree".equals(key)) {
                            free = Long.parseLong(value) * 1024;
                        }
                        if ("MemTotal".equals(key)) {
                            total = Long.parseLong(value) * 1024;
                        }
                    }
                }
                if (total > 0) {
                    return Optional.of(new PhysicalMemoryStatus(total, available > 0 ? available : free));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        java.lang.management.OperatingSystemMXBean bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunBean =
                    (com.sun.management.OperatingSystemMXBean)
                            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return Optional.of(new PhysicalMemoryStatus(sunBean.getTotalPhysicalMemorySize(), sunBean.getFreePhysicalMemorySize()));
        }
        return Optional.empty();
    }

    public static void forceGC() {
        System.gc();
        System.runFinalization();
        System.gc();
    }

    public static Path getWorkingDirectory(String folder) {
        String home = System.getProperty("user.home", ".");
        switch (OperatingSystem.CURRENT_OS) {
            case LINUX:
                return Paths.get(home, "." + folder);
            case WINDOWS:
                String appdata = System.getenv("APPDATA");
                return Paths.get(appdata == null ? home : appdata, "." + folder);
            case OSX:
                return Paths.get(home, "Library", "Application Support", folder);
            default:
                return Paths.get(home, folder);
        }
    }

    /**
     * Returns true if the given name is a valid file name on this operating system,
     * and false otherwise.
     */
    public static boolean isNameValid(String name) {
        // empty filename is not allowed
        if (name.isEmpty())
            return false;
        // . and .. have special meaning on all platforms
        if (name.isEmpty() || name.equals("."))
            return false;
        // \0 and / are forbidden on all platforms
        if (name.indexOf('/') != -1 || name.indexOf('\0') != -1)
            return false;

        if (CURRENT_OS == WINDOWS) { // Windows only
            char lastChar = name.charAt(name.length() - 1);
            // filenames ending in dot are not valid
            if (lastChar == '.')
                return false;
            // file names ending with whitespace are truncated (bug 118997)
            if (Character.isWhitespace(lastChar))
                return false;
            int dot = name.indexOf('.');
            // on windows, filename suffixes are not relevant to name validity
            String basename = dot == -1 ? name : name.substring(0, dot);
            if (Arrays.binarySearch(INVALID_RESOURCE_BASENAMES, basename.toLowerCase()) >= 0)
                return false;
            if (Arrays.binarySearch(INVALID_RESOURCE_FULLNAMES, name.toLowerCase()) >= 0)
                return false;
            if (INVALID_RESOURCE_CHARACTERS.matcher(name).find())
                return false;
        }

        return true;
    }

    public static class PhysicalMemoryStatus {
        private final long total;
        private final long available;

        public PhysicalMemoryStatus(long total, long available) {
            this.total = total;
            this.available = available;
        }

        public long getTotal() {
            return total;
        }

        public double getTotalGB() {
            return toGigaBytes(total);
        }

        public long getUsed() {
            return hasAvailable() ? total - available : 0;
        }

        public double getUsedGB() {
            return toGigaBytes(getUsed());
        }

        public long getAvailable() {
            return available;
        }

        public double getAvailableGB() {
            return toGigaBytes(available);
        }

        public boolean hasAvailable() {
            return available >= 0;
        }

        public static double toGigaBytes(long bytes) {
            return bytes / 1024. / 1024. / 1024.;
        }

        public static final PhysicalMemoryStatus INVALID = new PhysicalMemoryStatus(0, -1);
    }
}
