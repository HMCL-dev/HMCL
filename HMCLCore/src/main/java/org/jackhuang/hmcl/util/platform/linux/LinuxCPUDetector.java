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

    /// Resolves lowercase Apple M-series SoC codes to display names.
    /// Returns `null` for unrecognized codes.
    ///
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
            case "t6050" -> "Apple M5 Pro";
            case "t8152" -> "Apple M6";
            default -> null;
        };
    }

    /// Resolves Qualcomm names from device-tree compatibles and sysfs, returning `null` for unknown models.
    private static @Nullable String detectQualcommName(String model, String[] compatibles) {
        // Adapted from Fastfetch's ffCPUQualcommSnapdragonToName:
        // https://github.com/fastfetch-cli/fastfetch/blob/2f68ce7f7467ddbaaf31519c1d41455781d48492/src/detection/cpu/cpu_linux.c#L711-L776
        // The devkit shares soc_id 555 with retail X Elite chips; its compatible entry distinguishes it.
        if (Arrays.stream(compatibles).anyMatch(compatible -> compatible.contains("x1e001de")))
            return "Qualcomm Snapdragon X Elite X1E-00-1DE";

        // soc_id is a decimal hardware identifier. One ID can cover several retail SKUs.
        @Nullable Integer socId = parseNonNegativeInt(readSysfsAttribute(Path.of("/sys/devices/soc0/soc_id")));
        @Nullable String snapdragonName = switch (socId != null ? socId : -1) {
            case 662 -> "X2 Elite Extreme X2E-94/96-100";
            case 698 -> "X2 Elite X2E-88/90-100";
            case 699 -> "X2 Elite X2E-80/84-100";
            case 693 -> "X2 Elite X2E-78-100";
            case 728 -> "X2 Plus X2P-64-100";
            case 719 -> "X2 Plus X2P-42-100";
            case 555 -> "X Elite X1E-78/80/84-100";
            case 615 -> "X Plus X1P-64/66-100";
            case 616 -> "X X1-26-101";
            case 635 -> "X X1-26-100 / X Plus X1P-42/46-100";
            default -> null;
        };
        if (snapdragonName != null)
            return "Qualcomm Snapdragon " + snapdragonName;

        // Adapted from Fastfetch's detectSocName Qualcomm fallback and ffCPUQualcommCodeToName:
        // https://github.com/fastfetch-cli/fastfetch/blob/2f68ce7f7467ddbaaf31519c1d41455781d48492/src/detection/cpu/cpu_linux.c#L1014-L1030
        // https://github.com/fastfetch-cli/fastfetch/blob/2f68ce7f7467ddbaaf31519c1d41455781d48492/src/detection/cpu/cpu.c#L76-L90
        if (model.startsWith("sc")) {
            // SC compatibles may have suffixes, e.g. sc8280xp; only the numeric prefix selects the family.
            int codeEnd = 2;
            while (codeEnd < model.length() && model.charAt(codeEnd) >= '0' && model.charAt(codeEnd) <= '9')
                codeEnd++;
            @Nullable Integer socCode = parseNonNegativeInt(model.substring(2, codeEnd));
            return switch (socCode != null ? socCode : -1) {
                case 7180 -> "Qualcomm Snapdragon 7c";
                case 7280 -> "Qualcomm Snapdragon 7c+ Gen 3";
                case 8180 -> "Qualcomm Snapdragon 8cx Gen 2 5G";
                case 8280 -> "Qualcomm Snapdragon 8cx Gen 3";
                default -> "Qualcomm Snapdragon " + model.toUpperCase(Locale.ROOT);
            };
        }
        // Other unknown models retain HMCL's vendor/model fallback in the caller.
        return null;
    }

    /// Reads the processor name and vendor, preferring the device tree on ARM and procfs elsewhere.
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

        // ARM's procfs model name may describe only the architecture, not the actual SoC.
        if (modelName == null || Architecture.SYSTEM_ARCH.isArm()) {
            for (Path compatiblePath : List.of(
                    Path.of("/sys/firmware/devicetree/base/compatible"),
                    Path.of("/proc/device-tree/compatible"))) {
                try {
                    if (!Files.isRegularFile(compatiblePath))
                        continue;

                    // device-vendor,device-model\0soc-vendor,soc-model\0
                    String[] data = Files.readString(compatiblePath).split("\0");

                    for (int i = data.length - 1; i >= 0; i--) {
                        String device = data[i];
                        int idx = device.indexOf(',');
                        if (idx <= 0 || idx >= device.length() - 1)
                            continue;

                        String vendor = device.substring(0, idx);
                        String model = device.substring(idx + 1);

                        // Skip generic compatibles, following Fastfetch's detectSocName filtering:
                        // https://github.com/fastfetch-cli/fastfetch/blob/2f68ce7f7467ddbaaf31519c1d41455781d48492/src/detection/cpu/cpu_linux.c#L981-L984
                        if (model.startsWith("generic-") || model.endsWith("-platform") || model.endsWith("-soc"))
                            continue;

                        builder.setVendor(HardwareVendor.getKnown(vendor));
                        if (builder.getVendor() == null)
                            builder.setVendor(HardwareVendor.of(StringUtils.capitalizeFirst(vendor)));

                        if (builder.getVendor() == HardwareVendor.APPLE) {
                            builder.setName(appleCodeToName(model));

                            if (builder.getName() == null)
                                builder.setName("Apple Silicon " + model);
                        } else if (builder.getVendor() == HardwareVendor.QUALCOMM) {
                            builder.setName(detectQualcommName(model, data));
                        } else if (builder.getVendor() == HardwareVendor.BROADCOM)
                            // Raspberry Pi
                            builder.setName("Broadcom " + model.toUpperCase(Locale.ROOT));

                        if (builder.getName() == null)
                            builder.setName(builder.getVendor() + " " + model.toUpperCase(Locale.ROOT));

                        return;
                    }
                } catch (Throwable e) {
                    LOG.warning("Failed to detect CPU name from " + compatiblePath, e);
                }
            }
        }

        if (modelName != null) {
            builder.setName(CentralProcessor.cleanName(modelName));
            builder.setVendor(HardwareVendor.of(firstCore.get("vendor_id")));

            if (builder.getVendor() == null && modelName.startsWith("Loongson"))
                builder.setVendor(HardwareVendor.LOONGSON);
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

    /// Counts a nonempty processor group, using package-local identities only when package boundaries are known.
    /// Without package boundaries or consistent sibling sets, estimates physical cores from logical processors.
    ///
    /// Sysfs CPU directories and procfs entries identify logical processors; SMT siblings must be counted once.
    /// See the [Linux topology ABI](https://www.kernel.org/doc/html/latest/admin-guide/cputopology.html).
    private static CentralProcessor.@Nullable Cores detectCores(TreeMap<Integer, Map<String, String>> packageInfo,
                                                              Set<Integer> onlineProcessors, Path cpuRoot,
                                                              Set<Integer> performance, Set<Integer> efficiency,
                                                              boolean packageBoundariesKnown) {
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
        // Use one identity scheme per group so a missing sibling file cannot double-count a core.
        // Each online sibling must report the same set; offline siblings need no readable entry of their own.
        boolean useSiblings = siblingSets.entrySet().stream().allMatch(entry -> entry.getValue().contains(entry.getKey())
                && entry.getValue().stream().filter(onlineProcessors::contains)
                .allMatch(id -> entry.getValue().equals(siblingSets.get(id))));
        if (!useSiblings && !packageBoundariesKnown) {
            // core_id and die_id can repeat across packages; "cpu cores" is also package-local.
            // With no reliable physical identities, count each logical processor as a performance core for display.
            return new CentralProcessor.Cores(packageInfo.size(), packageInfo.size(), 0);
        }
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

    /// Detects online processors, returning one aggregate processor when package boundaries are unknown.
    /// Returns an empty list if procfs is unavailable.
    static @Unmodifiable List<CentralProcessor> detect() {
        @Nullable TreeMap<Integer, Map<String, String>> cpuInfo = loadCPUInfo();
        if (cpuInfo == null || cpuInfo.isEmpty())
            return List.of();
        Path cpuRoot = Path.of("/sys/devices/system/cpu");
        Path pmuRoot = Path.of("/sys/bus/event_source/devices");
        Map<Integer, TreeMap<Integer, Map<String, String>>> packages = new TreeMap<>();
        boolean packageBoundariesKnown = true;
        for (var entry : cpuInfo.entrySet()) {
            @Nullable Integer packageId = parseNonNegativeInt(readSysfsAttribute(cpuRoot.resolve("cpu" + entry.getKey() + "/topology/physical_package_id")));
            if (packageId == null)
                packageId = parseNonNegativeInt(entry.getValue().get("physical id"));
            if (packageId == null) {
                // One missing boundary makes the package grouping incomplete; retain all CPUs in one display entry.
                packageBoundariesKnown = false;
                packages.clear();
                packages.put(0, cpuInfo);
                break;
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
            identity.setCores(detectCores(packageInfo, cpuInfo.keySet(), cpuRoot, performance, efficiency, packageBoundariesKnown));
            result.add(identity.build());
        }
        return List.copyOf(result);
    }

    /// Prevents instantiation.
    private LinuxCPUDetector() {
    }
}
