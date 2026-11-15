// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.hardware.PageFileConfiguration;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Verifies page file aggregation, native layouts, and live Windows queries.
@NotNullByDefault
final class WindowsPageFileTest {

    /// Aggregates all page files and uses the supplied page size rather than a fixed 4 KiB.
    @Test
    void sumsCurrentCapacity() {
        Psapi psapi = (Psapi.PageFileCallback callback, @Nullable Pointer context) -> {
            assertTrue(callback.invoke(context, information(3), new WString("C:\\pagefile.sys")));
            assertTrue(callback.invoke(context, information(5), new WString("D:\\pagefile.sys")));
            return true;
        };
        assertEquals(8L * 16384, WindowsHardwareDetector.queryTotalPageFileSize(psapi, 16384));
    }

    /// Only a successful empty enumeration represents no page files.
    @Test
    void distinguishesEmptyFromFailure() {
        assertEquals(0, WindowsHardwareDetector.queryTotalPageFileSize((Psapi.PageFileCallback callback, @Nullable Pointer context) -> true, 4096));
        assertEquals(-1, WindowsHardwareDetector.queryTotalPageFileSize((Psapi.PageFileCallback callback, @Nullable Pointer context) -> false, 4096));
        assertEquals(-1, new org.jackhuang.hmcl.util.platform.hardware.HardwareDetector().getTotalPageFileSize());
    }

    /// Discards partial totals if enumeration subsequently fails.
    @Test
    void rejectsPartialResults() {
        Psapi psapi = (Psapi.PageFileCallback callback, @Nullable Pointer context) -> {
            assertTrue(callback.invoke(context, information(1024), new WString("C:\\pagefile.sys")));
            return false;
        };
        assertEquals(-1, WindowsHardwareDetector.queryTotalPageFileSize(psapi, 4096));
    }

    /// Rejects missing page size information before calling the native API.
    @Test
    void rejectsInvalidPageSize() {
        Psapi psapi = (Psapi.PageFileCallback callback, @Nullable Pointer context) -> {
            fail("Enumeration must not run with an invalid page size");
            return false;
        };
        assertEquals(-1, WindowsHardwareDetector.queryTotalPageFileSize(psapi, 0));
        assertEquals(-1, WindowsHardwareDetector.queryTotalPageFileSize(psapi, -1));
    }

    /// Stops enumeration on arithmetic overflow without throwing through the callback.
    @Test
    void rejectsOverflow() {
        Psapi multiplicationOverflow = (Psapi.PageFileCallback callback, @Nullable Pointer context) -> {
            assertFalse(callback.invoke(context, information(2), new WString("C:\\pagefile.sys")));
            return true;
        };
        assertEquals(-1, WindowsHardwareDetector.queryTotalPageFileSize(multiplicationOverflow, Long.MAX_VALUE));

        Psapi additionOverflow = (Psapi.PageFileCallback callback, @Nullable Pointer context) -> {
            assertTrue(callback.invoke(context, information(1), new WString("C:\\pagefile.sys")));
            assertFalse(callback.invoke(context, information(1), new WString("D:\\pagefile.sys")));
            return true;
        };
        assertEquals(-1, WindowsHardwareDetector.queryTotalPageFileSize(additionOverflow, Long.MAX_VALUE));
    }

    /// Reads fresh allocations on each query so automatic page file growth is visible.
    @Test
    void doesNotCacheCapacity() {
        long[] pages = {100};
        Psapi psapi = (Psapi.PageFileCallback callback, @Nullable Pointer context) -> callback.invoke(context, information(pages[0]), new WString("C:\\pagefile.sys"));
        assertEquals(100L * 4096, WindowsHardwareDetector.queryTotalPageFileSize(psapi, 4096));
        pages[0] = 200;
        assertEquals(200L * 4096, WindowsHardwareDetector.queryTotalPageFileSize(psapi, 4096));
    }

    /// Checks native structure sizes and the unsigned pointer-sized page count.
    @Test
    void matchesNativeLayouts() {
        assertEquals(new WinBase.SYSTEM_INFO().size(), new WinTypes.SYSTEM_INFO().size());
        assertEquals(8 + 3 * Native.SIZE_T_SIZE, new WinTypes.ENUM_PAGE_FILE_INFORMATION().size());
        assertEquals(0xFFFFFFFFL, new WinTypes.SIZE_T(0xFFFFFFFFL).longValue());
    }

    /// Sums all explicit maxima in MiB, preserving paths containing spaces.
    @Test
    void sumsConfiguredLimits() {
        PageFileConfiguration configuration = WindowsHardwareDetector.parsePageFileConfiguration(new String[]{
                "C:\\pagefile.sys 16384 32768",
                "\"D:\\Paging Files\\pagefile.sys\" 8192 16384"
        });
        assertEquals(PageFileConfiguration.Mode.MANUAL, configuration.mode());
        assertEquals(48L * 1024 * 1024 * 1024, configuration.maximumSize());
    }

    /// Recognizes automatic drive selection and per-file system-managed sizing without a numeric limit.
    @Test
    void recognizesSystemManagement() {
        for (String entry : new String[]{"?:\\pagefile.sys", "?:\\pagefile.sys 0 0", "C:\\pagefile.sys 0 0"}) {
            PageFileConfiguration configuration = WindowsHardwareDetector.parsePageFileConfiguration(new String[]{entry});
            assertEquals(PageFileConfiguration.Mode.SYSTEM_MANAGED, configuration.mode());
            assertEquals(-1, configuration.maximumSize());
        }
    }

    /// Does not present a partial manual sum as the aggregate maximum when managed files also exist.
    @Test
    void recognizesMixedManagement() {
        PageFileConfiguration configuration = WindowsHardwareDetector.parsePageFileConfiguration(new String[]{
                "C:\\pagefile.sys 4096 8192", "D:\\pagefile.sys 0 0"
        });
        assertEquals(PageFileConfiguration.Mode.MIXED, configuration.mode());
        assertEquals(-1, configuration.maximumSize());
    }

    /// Missing, malformed, reversed, duplicate, and overflowing limits must not produce a partial maximum.
    @Test
    void rejectsIncompleteConfiguration() {
        assertEquals(PageFileConfiguration.UNKNOWN, new org.jackhuang.hmcl.util.platform.hardware.HardwareDetector().getPageFileConfiguration());
        assertEquals(PageFileConfiguration.UNKNOWN, WindowsHardwareDetector.parsePageFileConfiguration(new String[0]));
        for (String invalid : new String[]{"", "invalid", "D:\\pagefile.sys", "D:\\pagefile.sys -1 4096",
                "D:\\pagefile.sys 8192 4096", "D:\\pagefile.sys 4096 0", "D:\\pagefile.sys 1 9223372036854775807",
                "D:\\pagefile.sys 1 9223372036854775808", "relative.sys 1 4096", "c:\\PAGEFILE.sys 1 4096"}) {
            assertEquals(PageFileConfiguration.UNKNOWN, WindowsHardwareDetector.parsePageFileConfiguration(new String[]{
                    "C:\\pagefile.sys 1024 2048", invalid
            }), invalid);
        }
    }

    /// Exercises native callbacks and page size marshalling on Windows.
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void queriesLiveWindowsPageFiles() {
        assumeTrue(NativeUtils.USE_JNA);
        long total = new WindowsHardwareDetector().getTotalPageFileSize();
        assertTrue(total >= 0, "The native page file query must succeed");
        System.out.println("Current page file capacity in bytes: " + total);
        System.out.println("Page file configuration: " + new WindowsHardwareDetector().getPageFileConfiguration());
    }

    /// Creates page file data with unrelated usage counters to verify capacity selection.
    private static WinTypes.ENUM_PAGE_FILE_INFORMATION information(long pages) {
        var information = new WinTypes.ENUM_PAGE_FILE_INFORMATION();
        information.TotalSize = new WinTypes.SIZE_T(pages);
        information.TotalInUse = new WinTypes.SIZE_T(1);
        information.PeakUsage = new WinTypes.SIZE_T(2);
        return information;
    }
}
