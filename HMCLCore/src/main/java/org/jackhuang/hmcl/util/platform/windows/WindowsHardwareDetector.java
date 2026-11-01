// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jackhuang.hmcl.util.platform.hardware.PageFileConfiguration;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Detects Windows hardware information.
///
/// @author Glavo
@NotNullByDefault
public final class WindowsHardwareDetector extends HardwareDetector {

    /// A page file path followed by its initial and maximum sizes in MiB.
    private static final Pattern PAGE_FILE_SETTING = Pattern.compile("^(.+)\\s+([0-9]+)\\s+([0-9]+)$");

    /// An absolute drive path, including the wildcard drive used by automatic management.
    private static final Pattern PAGE_FILE_PATH = Pattern.compile("(?i)^[a-z?]:\\\\[^\\r\\n]+$");

    /// Detects Windows processors, using the default detector when native information is unavailable.
    @Override
    public @Unmodifiable List<CentralProcessor> detectCentralProcessors() {
        @Unmodifiable List<CentralProcessor> processors = OperatingSystem.isWindows7OrLater() ? WindowsCPUDetector.detect() : List.of();
        return processors.isEmpty() ? super.detectCentralProcessors() : processors;
    }

    /// Returns an immutable graphics card list, empty if detection is unsupported or fails.
    @Override
    public @Unmodifiable List<GraphicsCard> detectGraphicsCards() {
        if (!OperatingSystem.isWindows7OrLater())
            return List.of();
        return WindowsGPUDetector.detect();
    }

    /// Queries current page file capacity in bytes; returns zero for no page files, or -1 on failure.
    @Override
    public long getTotalPageFileSize() {
        if (!NativeUtils.USE_JNA)
            return -1;

        try {
            @Nullable Kernel32 kernel32 = Kernel32.INSTANCE;
            @Nullable Psapi psapi = Psapi.INSTANCE;
            if (kernel32 == null || psapi == null)
                return -1;

            var systemInfo = new WinTypes.SYSTEM_INFO();
            kernel32.GetSystemInfo(systemInfo);
            return queryTotalPageFileSize(psapi, Integer.toUnsignedLong(systemInfo.dwPageSize));
        } catch (RuntimeException | LinkageError e) {
            LOG.warning("Failed to query page file capacity", e);
            return -1;
        }
    }

    /// Sums a complete enumeration in bytes; rejects invalid sizes, overflow, and partial results.
    static long queryTotalPageFileSize(Psapi psapi, long pageSize) {
        if (pageSize <= 0)
            return -1;

        long[] totalBytes = {0};
        boolean success = psapi.EnumPageFilesW((@Nullable Pointer context, WinTypes.ENUM_PAGE_FILE_INFORMATION information, WString fileName) -> {
            try {
                long pages = information.TotalSize.longValue();
                if (pages < 0)
                    throw new ArithmeticException("Page count exceeds the signed 64-bit range");
                totalBytes[0] = Math.addExact(totalBytes[0], Math.multiplyExact(pages, pageSize));
                return true;
            } catch (ArithmeticException e) {
                // Exceptions must not escape through the native callback boundary.
                totalBytes[0] = -1;
                return false;
            }
        }, null);
        if (!success) {
            LOG.warning("Failed to enumerate page files: " + Native.getLastError());
            return -1;
        }
        return totalBytes[0];
    }

    /// Reads configured limits independently of active allocations; unavailable or invalid settings remain unknown.
    ///
    /// @see <a href="https://learn.microsoft.com/windows/win32/cimwin32prov/win32-pagefilesetting">Win32_PageFileSetting and its PagingFiles registry mapping</a>
    @Override
    public PageFileConfiguration getPageFileConfiguration() {
        if (!NativeUtils.USE_JNA)
            return PageFileConfiguration.UNKNOWN;

        try {
            @Nullable WinReg registry = WinReg.INSTANCE;
            if (registry == null)
                return PageFileConfiguration.UNKNOWN;

            @Nullable Object value = registry.queryValue(WinReg.HKEY.HKEY_LOCAL_MACHINE,
                    "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Memory Management", "PagingFiles");
            return value instanceof String[] entries ? parsePageFileConfiguration(entries) : PageFileConfiguration.UNKNOWN;
        } catch (RuntimeException | LinkageError e) {
            LOG.warning("Failed to query page file configuration", e);
            return PageFileConfiguration.UNKNOWN;
        }
    }

    /// Aggregates complete registry entries; any malformed or duplicate entry makes the result unknown.
    ///
    /// @see <a href="https://learn.microsoft.com/windows/win32/cimwin32prov/win32-pagefilesetting">Win32_PageFileSetting</a>
    static PageFileConfiguration parsePageFileConfiguration(String[] entries) {
        if (entries.length == 0)
            return PageFileConfiguration.UNKNOWN;

        boolean hasManual = false;
        boolean hasSystemManaged = false;
        long maximumBytes = 0;
        Set<String> paths = new HashSet<>();
        try {
            for (String entry : entries) {
                String path = entry.strip();
                long initialSize = 0;
                long maximumSize = 0;
                Matcher matcher = PAGE_FILE_SETTING.matcher(path);
                if (matcher.matches()) {
                    path = matcher.group(1).strip();
                    initialSize = Long.parseLong(matcher.group(2));
                    maximumSize = Long.parseLong(matcher.group(3));
                } else if (!path.equalsIgnoreCase("?:\\pagefile.sys")) {
                    return PageFileConfiguration.UNKNOWN;
                }

                if (path.startsWith("\"") && path.endsWith("\"") && path.length() >= 2)
                    path = path.substring(1, path.length() - 1);
                if (!PAGE_FILE_PATH.matcher(path).matches() || !paths.add(path.toLowerCase(Locale.ROOT)))
                    return PageFileConfiguration.UNKNOWN;

                // A wildcard entry without sizes and explicit 0/0 sizes both request system management.
                if (initialSize == 0 && maximumSize == 0) {
                    hasSystemManaged = true;
                } else {
                    if (maximumSize < initialSize || maximumSize == 0 || path.charAt(0) == '?')
                        return PageFileConfiguration.UNKNOWN;
                    maximumBytes = Math.addExact(maximumBytes, Math.multiplyExact(maximumSize, 1024L * 1024));
                    hasManual = true;
                }
            }
        } catch (NumberFormatException | ArithmeticException e) {
            return PageFileConfiguration.UNKNOWN;
        }

        if (hasSystemManaged)
            return new PageFileConfiguration(-1, hasManual ? PageFileConfiguration.Mode.MIXED : PageFileConfiguration.Mode.SYSTEM_MANAGED);
        return new PageFileConfiguration(maximumBytes, PageFileConfiguration.Mode.MANUAL);
    }

    @Override
    public long getTotalMemorySize() {
        if (NativeUtils.USE_JNA) {
            Kernel32 kernel32 = Kernel32.INSTANCE;
            if (kernel32 != null) {
                WinTypes.MEMORYSTATUSEX status = new WinTypes.MEMORYSTATUSEX();
                if (kernel32.GlobalMemoryStatusEx(status))
                    return status.ullTotalPhys;
                else
                    LOG.warning("Failed to get memory status: " + kernel32.GetLastError());
            }
        }

        return super.getTotalMemorySize();
    }

    @Override
    public long getFreeMemorySize() {
        if (NativeUtils.USE_JNA) {
            Kernel32 kernel32 = Kernel32.INSTANCE;
            if (kernel32 != null) {
                WinTypes.MEMORYSTATUSEX status = new WinTypes.MEMORYSTATUSEX();
                if (kernel32.GlobalMemoryStatusEx(status))
                    return status.ullAvailPhys;
                else
                    LOG.warning("Failed to get memory status: " + kernel32.GetLastError());
            }
        }

        return super.getFreeMemorySize();
    }
}
