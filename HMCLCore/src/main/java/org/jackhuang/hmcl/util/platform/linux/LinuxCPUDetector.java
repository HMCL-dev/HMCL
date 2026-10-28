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
import java.nio.file.Paths;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Reads online processor identities and topology from procfs and sysfs.
@NotNullByDefault
final class LinuxCPUDetector {

    /// Reads logical processors, ignoring non-processor sections.
    private static @Nullable TreeMap<Integer, Map<String, String>> loadCPUInfo() {
        try {
            List<Map<String, String>> list = KeyValuePairUtils.loadList(Paths.get("/proc/cpuinfo"));
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

    /// Resolves known Apple SoC codes, or returns `null` for an unknown code.
    private static @Nullable String appleCodeToName(int code) {
        switch (code) {
            case 8103:
                return "Apple M1";
            case 6000:
                return "Apple M1 Pro";
            case 6001:
                return "Apple M1 Max";
            case 6002:
                return "Apple M1 Ultra";
            case 8112:
                return "Apple M2";
            case 6020:
                return "Apple M2 Pro";
            case 6021:
                return "Apple M2 Max";
            case 6022:
                return "Apple M2 Ultra";
            case 8122:
                return "Apple M3";
            case 6030:
                return "Apple M3 Pro";
            case 6031:
            case 6034:
                return "Apple M3 Max";
            case 8132:
                return "Apple M4";
            case 6040:
                return "Apple M4 Pro";
            case 6041:
                return "Apple M4 Max";
            default:
                return null;
        }
    }

    /// Reads the processor name, falling back to the machine device tree.
    private static void detectName(CentralProcessor.Builder builder, TreeMap<Integer, Map<String, String>> cpuInfo) {
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
            Path compatiblePath = Paths.get("/proc/device-tree/compatible");
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
                        // https://elixir.bootlin.com/linux/v6.11/source/arch/arm64/boot/dts/apple
                        if (model.matches("t[0-9]+"))
                            builder.setName(appleCodeToName(Integer.parseInt(model.substring(1))));

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
    private static @Nullable String read(Path path) {
        try {
            return Files.readString(path).trim();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /// Parses a nonnegative integer, returning `null` for missing or malformed values.
    private static @Nullable Integer number(@Nullable String value) {
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
    private static @Unmodifiable Set<Integer> cpuList(@Nullable String value) {
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
    private static CentralProcessor.@Nullable Cores detectCores(TreeMap<Integer, Map<String, String>> packageInfo,
                                                              Set<Integer> onlineProcessors, Path cpuRoot,
                                                              Set<Integer> performance, Set<Integer> efficiency, Architecture architecture) {
        Map<Integer, @Unmodifiable Set<Integer>> siblingSets = new HashMap<>();
        for (int id : packageInfo.keySet()) {
            Path topology = cpuRoot.resolve("cpu" + id + "/topology");
            @Unmodifiable Set<Integer> siblings = cpuList(read(topology.resolve("core_cpus_list")));
            if (siblings.isEmpty())
                siblings = cpuList(read(topology.resolve("thread_siblings_list")));
            siblingSets.put(id, siblings);
        }
        // Use one identity scheme per package so a missing sibling file cannot double-count a core.
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
                @Nullable Integer coreId = number(read(cpu.resolve("topology/core_id")));
                if (coreId == null)
                    coreId = number(entry.getValue().get("core id"));
                if (coreId == null) {
                    // A package-wide count remains useful even when per-core identifiers are absent.
                    @Nullable Integer physical = number(packageInfo.firstEntry().getValue().get("cpu cores"));
                    return physical != null && physical > 0
                            ? new CentralProcessor.Cores(packageInfo.size(), physical, 0) : null;
                }
                @Nullable Integer dieId = number(read(cpu.resolve("topology/die_id")));
                key = "core:" + (dieId != null ? dieId : 0) + ":" + coreId;
            }
            boolean isPerformance = performance.contains(id) && !efficiency.contains(id);
            boolean isEfficiency = efficiency.contains(id) && !performance.contains(id);
            @Nullable Integer capacity = number(read(cpu.resolve("cpu_capacity")));
            if (capacity != null && capacity == 0)
                capacity = null;
            // Conflicting sibling observations cannot establish a core's classification.
            cores.merge(key, new CoreData(isPerformance, isEfficiency, capacity),
                    (first, next) -> first.equals(next) ? first : new CoreData(false, false, null));
        }
        IntSummaryStatistics capacities = cores.values().stream().mapToInt(c -> c.capacity != null ? c.capacity : 0).summaryStatistics();
        boolean classifyByCapacity = capacities.getMin() > 0 && capacities.getMin() < capacities.getMax();
        int efficiencyCount = 0;
        for (CoreData core : cores.values()) {
            boolean isEfficiency = core.efficiency;
            if (!core.performance && !isEfficiency && classifyByCapacity) {
                // Merge upper ARM tiers and lower x86 tiers for a coarse P/E display.
                isEfficiency = architecture.isX86() ? core.capacity < capacities.getMax() : core.capacity == capacities.getMin();
            }
            if (isEfficiency)
                efficiencyCount++;
        }
        return new CentralProcessor.Cores(packageInfo.size(), cores.size() - efficiencyCount, efficiencyCount);
    }

    /// Groups online logical processors by package, then by SMT sibling set or die/core identifiers.
    static @Unmodifiable List<CentralProcessor> detect(TreeMap<Integer, Map<String, String>> cpuInfo, Path cpuRoot, Path pmuRoot, Architecture architecture) {
        if (cpuInfo.isEmpty())
            return List.of();
        Map<Integer, TreeMap<Integer, Map<String, String>>> packages = new TreeMap<>();
        for (var entry : cpuInfo.entrySet()) {
            @Nullable Integer packageId = number(read(cpuRoot.resolve("cpu" + entry.getKey() + "/topology/physical_package_id")));
            if (packageId == null)
                packageId = number(entry.getValue().get("physical id"));
            if (packageId == null) {
                // Without package boundaries, retain only the first reported identity.
                CentralProcessor.Builder builder = new CentralProcessor.Builder();
                detectName(builder, cpuInfo);
                return List.of(builder.build());
            }
            packages.computeIfAbsent(packageId, ignored -> new TreeMap<>()).put(entry.getKey(), entry.getValue());
        }
        @Unmodifiable Set<Integer> performance = cpuList(read(pmuRoot.resolve("cpu_core/cpus")));
        Set<Integer> efficiency = new HashSet<>(cpuList(read(pmuRoot.resolve("cpu_atom/cpus"))));
        efficiency.addAll(cpuList(read(pmuRoot.resolve("cpu_lowpower/cpus"))));
        List<CentralProcessor> result = new ArrayList<>();
        for (var packageInfo : packages.values()) {
            CentralProcessor.Builder identity = new CentralProcessor.Builder();
            detectName(identity, packageInfo);
            identity.setCores(detectCores(packageInfo, cpuInfo.keySet(), cpuRoot, performance, efficiency, architecture));
            result.add(identity.build());
        }
        return List.copyOf(result);
    }

    /// Detects online processors, or returns an empty list if procfs is unavailable.
    static @Unmodifiable List<CentralProcessor> detect() {
        @Nullable TreeMap<Integer, Map<String, String>> cpuInfo = loadCPUInfo();
        if (cpuInfo == null)
            return List.of();
        return detect(cpuInfo, Paths.get("/sys/devices/system/cpu"), Paths.get("/sys/bus/event_source/devices"), Architecture.SYSTEM_ARCH);
    }

    /// Prevents instantiation.
    private LinuxCPUDetector() {
    }
}
