// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.util.List;

/// Detects hardware information, with platform-specific subclasses providing native queries.
///
/// @author Glavo
@NotNullByDefault
public class HardwareDetector {

    /// Controls external-process detection without disabling native platform queries.
    private static final boolean USE_FAST_FETCH = "true".equalsIgnoreCase(System.getProperty("hmcl.hardware.fastfetch", "true"));

    /// Detects processor information, or returns `null` if unavailable.
    ///
    /// The default implementation runs Fastfetch when enabled.
    public @Nullable CentralProcessor detectCentralProcessor() {
        return USE_FAST_FETCH ? FastFetchUtils.detectCentralProcessor() : null;
    }

    /// Detects graphics cards, or returns `null` if unavailable; an empty list reports no cards.
    ///
    /// The default implementation runs Fastfetch when enabled.
    public @Nullable List<GraphicsCard> detectGraphicsCards() {
        return USE_FAST_FETCH ? FastFetchUtils.detectGraphicsCards() : null;
    }

    /// Returns total physical memory in bytes, or zero if the required management bean is unavailable.
    public long getTotalMemorySize() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
                return bean.getTotalMemorySize();
            }
        } catch (NoClassDefFoundError ignored) {
        }

        return 0L;
    }

    /// Returns free physical memory in bytes, or zero if the required management bean is unavailable.
    public long getFreeMemorySize() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean) {
                return bean.getFreeMemorySize();
            }
        } catch (NoClassDefFoundError ignored) {
        }

        return 0L;
    }
}
