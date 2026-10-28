// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.linux;

import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Reads online processor identities and topology from procfs and sysfs.
@NotNullByDefault
final class LinuxCPUDetector {

    /// Reads logical processors, ignoring non-processor sections.
    private static @Nullable TreeMap<Integer, Map<String, String>> loadCPUInfo() {
        try {
            List<Map<String, String>> list = KeyValuePairUtils.loadList(Path.of("/proc/cpuinfo"));
            TreeMap<Integer, Map<String, String>> result = new TreeMap<>();
            for (Map<String, String> map : list) {
                @Nullable String id = map.get("processor");
                if (id != null) {
                    result.put(Integer.parseInt(id), map);
                }
            }
            return result;
        } catch (Throwable e) {
            LOG.warning("Failed to load /proc/cpuinfo", e);
            return null;
        }
    }

    /// Resolves lowercase Apple M-series SoC codes to display names, preserving shared product names.
    /// Returns `null` for unrecognized codes.
    ///
    /// @see <a href="https://asahilinux.org/docs/hw/soc/soc-codenames/">SoC Codenames - Asahi Linux Documentation</a>
    /// @see <a href="https://asahilinux.org/docs/platform/feature-support/m5/">M5 Series Feature Support</a>
    /// @see <a href="https://github.com/AsahiLinux/kisd#base-addresses">Asahi Linux kisd chip identifiers</a>
    private static @Nullable String appleCodeToName(String code) {
        return switch (code) {
            case "t8103" -> "Apple M1";
            case "t6000" -> "Apple M1 Pro";
            case "t6001" -> "Apple M1 Max";
            case "t6002" -> "Apple M1 Ultra";
            case "t8112" -> "Apple M2";
            case "t6020" -> "Apple M2 Pro";
            case "t6021" -> "Apple M2 Max";
            case "t6022" -> "Apple M2 Ultra";
            case "t8122" -> "Apple M3";
            case "t6030" -> "Apple M3 Pro";
            case "t6031", "t6034" -> "Apple M3 Max";
            case "t6032" -> "Apple M3 Ultra";
            case "t8132" -> "Apple M4";
            case "t6040" -> "Apple M4 Pro";
            case "t6041" -> "Apple M4 Max";
            case "t8142" -> "Apple M5";
            case "t6050" -> "Apple M5 Pro / Max / Ultra";
            case "t8152" -> "Apple M6";
            default -> null;
        };
    }

    /// Reads the processor name and vendor, falling back to the machine device tree.
    private static void detectNameAndVendor(CentralProcessor.Builder builder, TreeMap<Integer, Map<String, String>> cpuInfo) {
        // assert !cpuInfo.isEmpty();
        Map<String, String> firstCore = cpuInfo.firstEntry().getValue();

        @Nullable String modelName = firstCore.get("model name");
        if (modelName == null)
            modelName = firstCore.get("Model Name");
        if (modelName == null)
            modelName = firstCore.get("Model name");
        if (modelName == null)
            modelName = firstCore.get("cpu model");

        if (modelName != null) {
            builder.setName(CentralProcessor.cleanName(modelName));
            builder.setVendor(HardwareVendor.of(firstCore.get("vendor_id")));

            if (builder.getVendor() == null && modelName.startsWith("Loongson"))
                builder.setVendor(HardwareVendor.LOONGSON);

            return;
        }

        try {
            Path compatiblePath = Path.of("/proc/device-tree/compatible");
            if (Files.isRegularFile(compatiblePath)) {
                // device-vendor,device-model\0soc-vendor,soc-model\0
                String[] data = Files.readString(compatiblePath).split("\0");

                for (int i = data.length - 1; i >= 0; i--) {
                    String device = data[i];
                    int idx = device.indexOf(',');
                    if (idx <= 0 || idx >= device.length() - 1)
                        continue;

                    String vendor = device.substring(0, idx);
                    String model = device.substring(idx + 1);

                    if (model.startsWith("generic-"))
                        continue;

                    builder.setVendor(HardwareVendor.getKnown(vendor));
                    if (builder.getVendor() == null)
                        builder.setVendor(HardwareVendor.of(StringUtils.capitalizeFirst(vendor)));

                    if (builder.getVendor() == HardwareVendor.APPLE) {
                        builder.setName(appleCodeToName(model));

                        if (builder.getName() == null)
                            builder.setName("Apple Silicon " + model);
                    } else if (builder.getVendor() == HardwareVendor.QUALCOMM) {
                        // https://elixir.bootlin.com/linux/v6.11/source/arch/arm64/boot/dts/qcom
                        if (model.startsWith("x"))
                            builder.setName("Qualcomm Snapdragon X Elite " + model.toUpperCase(Locale.ROOT));
                    } else if (builder.getVendor() == HardwareVendor.BROADCOM)
                        // Raspberry Pi
                        builder.setName("Broadcom " + model.toUpperCase(Locale.ROOT));

                    if (builder.getName() == null)
                        builder.setName(builder.getVendor() + " " + model.toUpperCase(Locale.ROOT));

                    return;
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to detect CPU name from /proc/device-tree/compatible", e);
        }
    }

    /// Reads an optional sysfs attribute without making missing attributes fatal.
    private static @Nullable String readSysfsAttribute(Path path) {
        try {
            return Files.readString(path).trim();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /// Parses a nonnegative integer, returning `null` for missing or malformed values.
    private static @Nullable Integer parseNonNegativeInt(@Nullable String value) {
        if (value == null)
            return null;
        try {
            int result = Integer.parseInt(value);
            return result >= 0 ? result : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /// Parses a kernel CPU list, returning an empty set if unavailable or malformed.
    private static @Unmodifiable Set<Integer> parseCpuList(@Nullable String value) {
        if (value == null || value.isBlank())
            return Set.of();
        Set<Integer> result = new TreeSet<>();
        try {
            for (String item : value.split(",")) {
                String[] range = item.trim().split("-", -1);
                int first = Integer.parseInt(range[0]);
                int last = range.length == 1 ? first : Integer.parseInt(range[1]);
                if (range.length > 2 || first < 0 || last < first || last > 1048575)
                    return Set.of();
                for (int id = first; id <= last; id++)
                    result.add(id);
            }
            return Collections.unmodifiableSet(result);
        } catch (NumberFormatException e) {
            return Set.of();
        }
    }

    /// Classification evidence for one physical core.
    @NotNullByDefault
    private record CoreData(boolean performance, boolean efficiency, @Nullable Integer capacity) {
    }

    /// Counts one nonempty package, falling back to reported totals if per-core identities are incomplete.
    ///
    /// Sysfs CPU directories and procfs entries identify logical processors; SMT siblings must be counted once.
    /// See the [Linux topology ABI](https://www.kernel.org/doc/html/latest/admin-guide/cputopology.html).
    private static CentralProcessor.@Nullable Cores detectCores(TreeMap<Integer, Map<String, String>> packageInfo,
                                                              Set<Integer> onlineProcessors, Path cpuRoot,
                                                              Set<Integer> performance, Set<Integer> efficiency) {
        Map<Integer, @Unmodifiable Set<Integer>> siblingSets = new HashMap<>();
        for (int id : packageInfo.keySet()) {
            Path topology = cpuRoot.resolve("cpu" + id + "/topology");
            // core_cpus_list lists logical CPU IDs sharing this physical core, e.g. "0,8" for two SMT threads.
            // thread_siblings_list is the older name for the same information.
            @Unmodifiable Set<Integer> siblings = parseCpuList(readSysfsAttribute(topology.resolve("core_cpus_list")));
            if (siblings.isEmpty())
                siblings = parseCpuList(readSysfsAttribute(topology.resolve("thread_siblings_list")));
            siblingSets.put(id, siblings);
        }
        // Use one identity scheme per package so a missing sibling file cannot double-count a core.
        // Each online sibling must report the same set; offline siblings need no readable entry of their own.
        boolean useSiblings = siblingSets.entrySet().stream().allMatch(entry -> entry.getValue().contains(entry.getKey())
                && entry.getValue().stream().filter(onlineProcessors::contains)
                .allMatch(id -> entry.getValue().equals(siblingSets.get(id))));
        Map<String, CoreData> cores = new LinkedHashMap<>();
        for (var entry : packageInfo.entrySet()) {
            int id = entry.getKey();
            Path cpu = cpuRoot.resolve("cpu" + id);
            String key;
            if (useSiblings) {
                key = "siblings:" + siblingSets.get(id);
            } else {
                // core_id identifies the physical core containing this logical CPU; it is not globally unique.
                // Some architectures also expose it as "core id" in /proc/cpuinfo.
                @Nullable Integer coreId = parseNonNegativeInt(readSysfsAttribute(cpu.resolve("topology/core_id")));
                if (coreId == null)
                    coreId = parseNonNegativeInt(entry.getValue().get("core id"));
                if (coreId == null) {
                    // /proc/cpuinfo's "cpu cores" supplies a package-wide physical count without core identities.
                    // Keep that total as unclassified performance cores; do not infer physical cores from threads.
                    @Nullable Integer physical = parseNonNegativeInt(packageInfo.firstEntry().getValue().get("cpu cores"));
                    return physical != null && physical > 0
                            ? new CentralProcessor.Cores(packageInfo.size(), physical, 0) : null;
                }
                // die_id identifies the containing die, separating repeated core IDs across dies in one package.
                // Without it, the fallback has only one core-ID namespace for this package.
                @Nullable Integer dieId = parseNonNegativeInt(readSysfsAttribute(cpu.resolve("topology/die_id")));
                key = "core:" + (dieId != null ? dieId : 0) + ":" + coreId;
            }
            // The caller obtains these logical CPU sets from the cpu_core, cpu_atom and cpu_lowpower PMUs.
            // Membership in both categories is ambiguous, so it does not establish an explicit core type.
            boolean isPerformance = performance.contains(id) && !efficiency.contains(id);
            boolean isEfficiency = efficiency.contains(id) && !performance.contains(id);
            // cpu_capacity describes relative maximum compute capacity for scheduling, not current utilization.
            // It is optional; missing or zero values cannot support capacity-based classification.
            @Nullable Integer capacity = parseNonNegativeInt(readSysfsAttribute(cpu.resolve("cpu_capacity")));
            if (capacity != null && capacity == 0)
                capacity = null;
            // Conflicting sibling observations cannot establish a core's classification.
            cores.merge(key, new CoreData(isPerformance, isEfficiency, capacity),
                    (first, next) -> first.equals(next) ? first : new CoreData(false, false, null));
        }
        // Infer P/E tiers only from complete, differing capacities. Equal values do not prove identical core types.
        IntSummaryStatistics capacities = cores.values().stream().mapToInt(c -> c.capacity != null ? c.capacity : 0).summaryStatistics();
        boolean classifyByCapacity = capacities.getMin() > 0 && capacities.getMin() < capacities.getMax();
        int efficiencyCount = 0;
        for (CoreData core : cores.values()) {
            boolean isEfficiency = core.efficiency;
            if (!core.performance && !isEfficiency && classifyByCapacity) {
                // For display, x86 keeps only the highest tier as P; other architectures keep only the lowest as E.
                // This deliberately merges multiple tiers rather than reconstructing physical clusters.
                isEfficiency = Architecture.SYSTEM_ARCH.isX86() ? core.capacity < capacities.getMax() : core.capacity == capacities.getMin();
            }
            if (isEfficiency)
                efficiencyCount++;
        }
        // Unclassified physical cores join the performance count as a display convention.
        return new CentralProcessor.Cores(packageInfo.size(), cores.size() - efficiencyCount, efficiencyCount);
    }

    /// Detects online processors using package and core topology, or returns an empty list if procfs is unavailable.
    static @Unmodifiable List<CentralProcessor> detect() {
        @Nullable TreeMap<Integer, Map<String, String>> cpuInfo = loadCPUInfo();
        if (cpuInfo == null || cpuInfo.isEmpty())
            return List.of();
        Path cpuRoot = Path.of("/sys/devices/system/cpu");
        Path pmuRoot = Path.of("/sys/bus/event_source/devices");
        Map<Integer, TreeMap<Integer, Map<String, String>>> packages = new TreeMap<>();
        for (var entry : cpuInfo.entrySet()) {
            @Nullable Integer packageId = parseNonNegativeInt(readSysfsAttribute(cpuRoot.resolve("cpu" + entry.getKey() + "/topology/physical_package_id")));
            if (packageId == null)
                packageId = parseNonNegativeInt(entry.getValue().get("physical id"));
            if (packageId == null) {
                // Without package boundaries, retain only the first reported identity.
                CentralProcessor.Builder builder = new CentralProcessor.Builder();
                detectNameAndVendor(builder, cpuInfo);
                return List.of(builder.build());
            }
            packages.computeIfAbsent(packageId, ignored -> new TreeMap<>()).put(entry.getKey(), entry.getValue());
        }
        @Unmodifiable Set<Integer> performance = parseCpuList(readSysfsAttribute(pmuRoot.resolve("cpu_core/cpus")));
        Set<Integer> efficiency = new HashSet<>(parseCpuList(readSysfsAttribute(pmuRoot.resolve("cpu_atom/cpus"))));
        efficiency.addAll(parseCpuList(readSysfsAttribute(pmuRoot.resolve("cpu_lowpower/cpus"))));
        List<CentralProcessor> result = new ArrayList<>();
        for (var packageInfo : packages.values()) {
            CentralProcessor.Builder identity = new CentralProcessor.Builder();
            detectNameAndVendor(identity, packageInfo);
            identity.setCores(detectCores(packageInfo, cpuInfo.keySet(), cpuRoot, performance, efficiency));
            result.add(identity.build());
        }
        return List.copyOf(result);
    }

    /// Prevents instantiation.
    private LinuxCPUDetector() {
    }
}
