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

import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinTypes;
import org.jackhuang.hmcl.util.platform.windows.WindowsVersion;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
     * macOS.
     */
    MACOS("macos", "osx"),
    /**
     * FreeBSD.
     */
    FREEBSD("freebsd", "linux"),
    /**
     * Unknown operating system.
     */
    UNKNOWN("universal");

    private final String checkedName;
    private final String mojangName;

    OperatingSystem(String checkedName) {
        this.checkedName = checkedName;
        this.mojangName = checkedName;
    }

    OperatingSystem(String checkedName, String mojangName) {
        this.checkedName = checkedName;
        this.mojangName = mojangName;
    }

    public String getCheckedName() {
        return checkedName;
    }

    public String getMojangName() {
        return mojangName;
    }

    public boolean isLinuxOrBSD() {
        return this == LINUX || this == FREEBSD;
    }

    public String getJavaExecutable() {
        return this == WINDOWS ? "java.exe" : "java";
    }

    /**
     * The current operating system.
     */
    public static final OperatingSystem CURRENT_OS = parseOSName(System.getProperty("os.name"));

    /**
     * The system default charset.
     */
    public static final Charset NATIVE_CHARSET;

    /**
     * Windows system build number.
     * When the version number is not recognized or on another system, the value will be -1.
     */
    public static final int SYSTEM_BUILD_NUMBER;

    /**
     * The version number is non-null if and only if the operating system is {@linkplain #WINDOWS}.
     */
    public static final @Nullable WindowsVersion WINDOWS_VERSION;

    /**
     * The name of current operating system.
     */
    public static final String SYSTEM_NAME;

    /**
     * The version of current operating system.
     */
    public static final String SYSTEM_VERSION;

    public static final String OS_RELEASE_NAME;
    public static final String OS_RELEASE_PRETTY_NAME;

    public static final int CODE_PAGE;

    public static final Pattern INVALID_RESOURCE_CHARACTERS;
    private static final String[] INVALID_RESOURCE_BASENAMES;
    private static final String[] INVALID_RESOURCE_FULLNAMES;

    static {
        String nativeEncoding = System.getProperty("native.encoding");
        String hmclNativeEncoding = System.getProperty("hmcl.native.encoding");
        Charset nativeCharset = Charset.defaultCharset();

        try {
            if (hmclNativeEncoding != null) {
                nativeCharset = Charset.forName(hmclNativeEncoding);
            } else {
                if (nativeEncoding != null && !nativeEncoding.equalsIgnoreCase(nativeCharset.name())) {
                    nativeCharset = Charset.forName(nativeEncoding);
                }

                if (nativeCharset == StandardCharsets.UTF_8 || nativeCharset == StandardCharsets.US_ASCII) {
                    nativeCharset = StandardCharsets.UTF_8;
                } else if ("GBK".equalsIgnoreCase(nativeCharset.name()) || "GB2312".equalsIgnoreCase(nativeCharset.name())) {
                    nativeCharset = Charset.forName("GB18030");
                }
            }
        } catch (UnsupportedCharsetException e) {
            e.printStackTrace(System.err);
        }
        NATIVE_CHARSET = nativeCharset;

        if (CURRENT_OS == WINDOWS) {
            int codePage = -1;
            WindowsVersion windowsVersion = null;

            Kernel32 kernel32 = Kernel32.INSTANCE;
            // Get Windows version number
            if (kernel32 != null) {
                WinTypes.OSVERSIONINFOEXW osVersionInfo = new WinTypes.OSVERSIONINFOEXW();
                if (kernel32.GetVersionExW(osVersionInfo)) {
                    windowsVersion = new WindowsVersion(osVersionInfo.dwMajorVersion, osVersionInfo.dwMinorVersion, osVersionInfo.dwBuildNumber);
                } else
                    System.err.println("Failed to obtain OS version number (" + kernel32.GetLastError() + ")");
            }

            if (windowsVersion == null) {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"cmd", "ver"});
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), NATIVE_CHARSET))) {
                        Matcher matcher = Pattern.compile("(?<version>\\d+\\.\\d+\\.\\d+\\.\\d+?)]$")
                                .matcher(reader.readLine().trim());
                        if (matcher.find())
                            windowsVersion = new WindowsVersion(matcher.group("version"));
                    }
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }

            if (windowsVersion == null)
                windowsVersion = new WindowsVersion(System.getProperty("os.version"));

            // Get Code Page

            if (kernel32 != null)
                codePage = kernel32.GetACP();
            else {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"chcp.com"});
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), NATIVE_CHARSET))) {
                        Matcher matcher = Pattern.compile("(?<cp>[0-9]+)$")
                                .matcher(reader.readLine().trim());

                        if (matcher.find())
                            codePage = Integer.parseInt(matcher.group("cp"));
                    }
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }

            String osName = System.getProperty("os.name");

            // Java 17 or earlier recognizes Windows 11 as Windows 10
            if (osName.equals("Windows 10") && windowsVersion.compareTo(WindowsVersion.WINDOWS_11) >= 0)
                osName = "Windows 11";

            SYSTEM_NAME = osName;
            SYSTEM_VERSION = windowsVersion.toString();
            SYSTEM_BUILD_NUMBER = windowsVersion.getBuild();
            WINDOWS_VERSION = windowsVersion;
            CODE_PAGE = codePage;
        } else {
            SYSTEM_NAME = System.getProperty("os.name");
            SYSTEM_VERSION = System.getProperty("os.version");
            SYSTEM_BUILD_NUMBER = -1;
            WINDOWS_VERSION = null;
            CODE_PAGE = -1;
        }

        Map<String, String> osRelease = Collections.emptyMap();
        if (CURRENT_OS == LINUX || CURRENT_OS == FREEBSD) {
            Path osReleaseFile = Paths.get("/etc/os-release");
            if (Files.exists(osReleaseFile)) {
                try {
                    osRelease = KeyValuePairUtils.loadProperties(osReleaseFile);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                }
            }
        }
        OS_RELEASE_NAME = osRelease.get("NAME");
        OS_RELEASE_PRETTY_NAME = osRelease.get("PRETTY_NAME");

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

    public static OperatingSystem parseOSName(String name) {
        if (name == null) {
            return UNKNOWN;
        }

        name = name.trim().toLowerCase(Locale.ROOT);

        if (name.contains("mac") || name.contains("darwin") || name.contains("osx"))
            return MACOS;
        else if (name.contains("win"))
            return WINDOWS;
        else if (name.contains("solaris") || name.contains("linux") || name.contains("unix") || name.contains("sunos"))
            return LINUX;
        else if (name.equals("freebsd"))
            return FREEBSD;
        else
            return UNKNOWN;
    }

    public static boolean isWindows7OrLater() {
        return WINDOWS_VERSION != null && WINDOWS_VERSION.compareTo(WindowsVersion.WINDOWS_7) >= 0;
    }

    public static Path getWorkingDirectory(String folder) {
        String home = System.getProperty("user.home", ".");
        switch (OperatingSystem.CURRENT_OS) {
            case LINUX:
            case FREEBSD:
                return Paths.get(home, "." + folder).toAbsolutePath();
            case WINDOWS:
                String appdata = System.getenv("APPDATA");
                return Paths.get(appdata == null ? home : appdata, "." + folder).toAbsolutePath();
            case MACOS:
                return Paths.get(home, "Library", "Application Support", folder).toAbsolutePath();
            default:
                return Paths.get(home, folder).toAbsolutePath();
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
        if (name.equals("."))
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
            if (Arrays.binarySearch(INVALID_RESOURCE_BASENAMES, basename.toLowerCase(Locale.ROOT)) >= 0)
                return false;
            if (Arrays.binarySearch(INVALID_RESOURCE_FULLNAMES, name.toLowerCase(Locale.ROOT)) >= 0)
                return false;
            if (INVALID_RESOURCE_CHARACTERS.matcher(name).find())
                return false;
        }

        return true;
    }

}
