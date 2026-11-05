// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Detects hardware information, with platform-specific subclasses providing native queries.
///
/// @author Glavo
@NotNullByDefault
public class HardwareDetector {

    /// Controls external-process detection without disabling native platform queries.
    private static final boolean USE_FAST_FETCH = "true".equalsIgnoreCase(System.getProperty("hmcl.hardware.fastfetch", "true"));

    /// Lowercase placeholder names excluded by exact, case-insensitive matching after stripping whitespace.
    private static final @Unmodifiable Set<String> DEVICE_NAME_PLACEHOLDERS = Set.of(
            "unknown",
            "none",
            "n/a",
            "default string",
            "to be filled by o.e.m.",
            "to be filled by o.e.m",
            "system product name");

    /// Strips surrounding whitespace from a decoded hardware model name.
    /// Returns `null` for absent, blank, or known placeholder names, matching placeholders without case sensitivity.
    /// Other model names are preserved without requiring a known manufacturer or model.
    protected static @Nullable String cleanDeviceName(@Nullable String name) {
        if (name == null)
            return null;
        name = name.strip();
        return name.isEmpty() || DEVICE_NAME_PLACEHOLDERS.contains(name.toLowerCase(Locale.ROOT)) ? null : name;
    }

    /// Returns a nonempty immutable processor list, estimating missing counts for a single entry.
    ///
    /// The default implementation runs Fastfetch when enabled, falling back to an `Unknown` processor.
    /// Fallback counts use JVM availability for display, not the hardware's physical core count.
    public @Unmodifiable List<CentralProcessor> detectCentralProcessors() {
        @Unmodifiable List<CentralProcessor> processors = USE_FAST_FETCH ? FastFetchUtils.detectCentralProcessors() : List.of();
        if (!processors.isEmpty())
            return processors;
        int count = Runtime.getRuntime().availableProcessors();
        return List.of(new CentralProcessor.Builder().setCores(new CentralProcessor.Cores(count, count, 0)).build());
    }

    /// Returns an immutable graphics card list, empty if no cards are found or detection fails.
    ///
    /// The default implementation runs Fastfetch when enabled.
    public @Unmodifiable List<GraphicsCard> detectGraphicsCards() {
        return USE_FAST_FETCH ? FastFetchUtils.detectGraphicsCards() : List.of();
    }

    /// Returns the device's hardware identity, or `null` if unavailable or unsupported.
    ///
    /// This describes the hardware product, not the user-configured hostname.
    ///
    /// @implSpec The default implementation returns `null`.
    public @Nullable DeviceInfo detectDeviceInfo() {
        return null;
    }

    /// Returns a desktop environment description, or `null` if detection is unsupported or no name is available.
    ///
    /// The description may include the session type.
    ///
    /// @implSpec The default implementation returns `null`.
    public @Nullable String detectDesktopEnvironment() {
        return null;
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

    /// Queries current page file capacity in bytes; -1 means unsupported or failed, and zero means none.
    public long getTotalPageFileSize() {
        return -1;
    }

    /// Queries configured page file sizing policy, returning unknown if unsupported or unavailable.
    public PageFileConfiguration getPageFileConfiguration() {
        return PageFileConfiguration.UNKNOWN;
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
