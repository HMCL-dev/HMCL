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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Reads system CPU counts and Apple Silicon performance levels from sysctl.
@NotNullByDefault
final class MacOSCPUDetector {

    /// Reads processor information from sysctl, assuming one package when its count is unknown.
    /// Multiple packages share the reported identity and receive equal counts when the totals divide evenly.
    static @Unmodifiable List<CentralProcessor> detect() {
        try {
            Map<String, String> values = SystemUtils.run(List.of("/usr/sbin/sysctl", "-i", "hw", "machdep.cpu"),
                    input -> KeyValuePairUtils.loadPairs(new BufferedReader(new InputStreamReader(input, OperatingSystem.NATIVE_CHARSET))));

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
            int physical = parseSysctlCount(values, "hw.physicalcpu");
            int logical = parseSysctlCount(values, "hw.logicalcpu");
            if (physical == 0)
                physical = parseSysctlCount(values, "machdep.cpu.core_count");
            if (logical == 0)
                logical = parseSysctlCount(values, "machdep.cpu.thread_count");
            int packages = parseSysctlCount(values, "hw.packages");
            if (packages == 0) {
                int perPackage = parseSysctlCount(values, "machdep.cpu.cores_per_package");
                if (perPackage > 0 && physical > 0 && physical % perPackage == 0)
                    packages = physical / perPackage;
                else
                    packages = 1;
            }
            if (logical > 0 && physical > 0 && logical % packages == 0 && physical % packages == 0) {
                // System-wide performance levels do not identify individual packages.
                int efficiency = packages == 1 ? countEfficiencyCores(values, physical, logical) : 0;
                builder.setCores(new CentralProcessor.Cores(logical / packages, physical / packages - efficiency, efficiency));
            }
            return List.copyOf(Collections.nCopies(packages, builder.build()));
        } catch (Exception e) {
            LOG.warning("Failed to get CPU info", e);
            return List.of();
        }
    }

    /// Parses a positive sysctl count, using zero for missing or malformed data.
    private static int parseSysctlCount(Map<String, String> values, String key) {
        @Nullable String value = values.get(key);
        if (value == null)
            return 0;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /// Counts named efficiency cores only when all performance levels match the package totals.
    private static int countEfficiencyCores(Map<String, String> values, int physical, int logical) {
        int levels = parseSysctlCount(values, "hw.nperflevels");
        if (levels == 0 || levels > 64)
            return 0;

        long efficiency = 0;
        long observedCores = 0;
        long observedThreads = 0;
        for (int i = 0; i < levels; i++) {
            String prefix = "hw.perflevel" + i + ".";
            int cores = parseSysctlCount(values, prefix + "physicalcpu");
            int threads = parseSysctlCount(values, prefix + "logicalcpu");
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
