// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Detects Windows hardware information.
///
/// @author Glavo
@NotNullByDefault
public final class WindowsHardwareDetector extends HardwareDetector {

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
