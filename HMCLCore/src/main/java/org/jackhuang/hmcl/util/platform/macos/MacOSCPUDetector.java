// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.macos;

import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Reads system CPU counts and Apple Silicon performance levels from sysctl.
@NotNullByDefault
final class MacOSCPUDetector {

    /// Reads optional sysctl nodes; unsupported nodes are ignored by sysctl.
    static @Unmodifiable List<CentralProcessor> detect() {
        try {
            Map<String, String> values = SystemUtils.run(List.of("/usr/sbin/sysctl", "-i", "hw", "machdep.cpu"),
                    input -> KeyValuePairUtils.loadPairs(new BufferedReader(new InputStreamReader(input, OperatingSystem.NATIVE_CHARSET))));
            return parse(values);
        } catch (Exception e) {
            LOG.warning("Failed to get CPU info", e);
            return List.of();
        }
    }

    /// Reads a positive count, using zero for missing or malformed data.
    private static int count(Map<String, String> values, String key) {
        @Nullable String value = values.get(key);
        if (value == null)
            return 0;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /// Parses a single package; multi-package sysctl output supplies only the first identity without core counts.
    static @Unmodifiable List<CentralProcessor> parse(Map<String, String> values) {
        CentralProcessor.Builder builder = new CentralProcessor.Builder();
        @Nullable String name = CentralProcessor.cleanName(values.get("machdep.cpu.brand_string"));
        builder.setName(name);
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("apple"))
                builder.setVendor(HardwareVendor.APPLE);
            else if (lower.startsWith("intel"))
                builder.setVendor(HardwareVendor.INTEL);
        }
        int physical = count(values, "hw.physicalcpu");
        int logical = count(values, "hw.logicalcpu");
        if (physical == 0)
            physical = count(values, "machdep.cpu.core_count");
        if (logical == 0)
            logical = count(values, "machdep.cpu.thread_count");
        int packages = count(values, "hw.packages");
        if (packages == 0) {
            int perPackage = count(values, "machdep.cpu.cores_per_package");
            if (perPackage > 0 && physical > 0 && physical % perPackage == 0)
                packages = physical / perPackage;
            else if (builder.getVendor() == HardwareVendor.APPLE)
                packages = 1;
        }
        if (logical == 0 || packages != 1)
            return List.of(builder.build());
        builder.setId(0);
        if (physical == 0)
            return List.of(builder.build());
        int efficiency = efficiencyCount(values, physical, logical);
        return List.of(builder.setCores(new CentralProcessor.Cores(logical, physical - efficiency, efficiency)).build());
    }

    /// Counts named efficiency cores only when all performance levels match the package totals.
    private static int efficiencyCount(Map<String, String> values, int physical, int logical) {
        int levels = count(values, "hw.nperflevels");
        if (levels == 0 || levels > 64)
            return 0;

        long efficiency = 0;
        long observedCores = 0;
        long observedThreads = 0;
        for (int i = 0; i < levels; i++) {
            String prefix = "hw.perflevel" + i + ".";
            int cores = count(values, prefix + "physicalcpu");
            int threads = count(values, prefix + "logicalcpu");
            if (cores == 0 || threads == 0)
                return 0;
            if ("Efficiency".equalsIgnoreCase(values.get(prefix + "name")))
                efficiency += cores;
            observedCores += cores;
            observedThreads += threads;
        }
        return observedCores == physical && observedThreads == logical ? (int) efficiency : 0;
    }

    /// Prevents instantiation.
    private MacOSCPUDetector() {
    }
}
