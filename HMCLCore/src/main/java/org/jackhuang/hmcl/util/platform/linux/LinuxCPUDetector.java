/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.platform.linux;

import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 * @see <a href="https://github.com/fastfetch-cli/fastfetch/blob/e13eb05073297aa6f1dc244ab3414e98170a43e1/src/detection/cpu/cpu_linux.c">cpu_linux.c</a>
 */
final class LinuxCPUDetector {

    private static TreeMap<Integer, Map<String, String>> loadCPUInfo() {
        try {
            List<Map<String, String>> list = KeyValuePairUtils.loadList(Paths.get("/proc/cpuinfo"));
            TreeMap<Integer, Map<String, String>> result = new TreeMap<>();
            for (Map<String, String> map : list) {
                String id = map.get("processor");
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

    // https://asahilinux.org/docs/hw/soc/soc-codenames/
    private static String appleCodeToName(int code) {
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

    private static void detectName(CentralProcessor.Builder builder, TreeMap<Integer, Map<String, String>> cpuInfo) {
        // assert !cpuInfo.isEmpty();
        Map<String, String> firstCore = cpuInfo.firstEntry().getValue();

        String modelName = firstCore.get("model name");
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

    private static void detectCores(CentralProcessor.Builder builder, TreeMap<Integer, Map<String, String>> cpuInfo) {
        // assert !cpuInfo.isEmpty();

        int logical = cpuInfo.size();

        try {
            Map<String, String> firstCore = cpuInfo.firstEntry().getValue();
            if (firstCore.containsKey("cpu cores") && firstCore.containsKey("physical id")) {
                TreeMap<Integer, Integer> cpuCores = new TreeMap<>();
                TreeSet<Integer> physicalIds = new TreeSet<>();

                for (Map<String, String> core : cpuInfo.values()) {
                    int cores = Integer.parseInt(core.get("cpu cores"));
                    int physicalId = Integer.parseInt(core.get("physical id"));

                    cpuCores.put(physicalId, cores);
                    physicalIds.add(physicalId);
                }

                int physical = 0;
                for (Integer value : cpuCores.values()) {
                    physical += value;
                }

                builder.setCores(new CentralProcessor.Cores(physical, logical, physicalIds.size()));
                return;
            }
        } catch (Throwable e) {
            LOG.warning("Failed to detect cores in /proc/cpuinfo", e);
        }

        Path cpuDevicesDir = Paths.get("/sys/devices/system/cpu");
        if (Files.isRegularFile(cpuDevicesDir.resolve("cpu0/topology/physical_package_id"))
                && Files.isRegularFile(cpuDevicesDir.resolve("cpu0/topology/core_cpus_list"))) {
            Pattern dirNamePattern = Pattern.compile("cpu[0-9]+");
            TreeSet<Integer> physicalPackageIds = new TreeSet<>();
            TreeSet<Integer> physicalCores = new TreeSet<>();

            int physical = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cpuDevicesDir)) {
                for (Path cpuDir : stream) {
                    if (!dirNamePattern.matcher(cpuDir.getFileName().toString()).matches() || !Files.isDirectory(cpuDir))
                        continue;

                    physicalPackageIds.add(Integer.parseInt(Files.readString(cpuDir.resolve("topology/physical_package_id")).trim()));

                    boolean shouldCount = false;
                    for (String item : Files.readString(cpuDir.resolve("topology/core_cpus_list")).trim().split(",")) {
                        String range = item.trim();
                        int idx = range.indexOf('-');
                        if (idx < 0)
                            shouldCount |= physicalCores.add(Integer.parseInt(range));
                        else {
                            int first = Integer.parseInt(range.substring(0, idx));
                            int last = Integer.parseInt(range.substring(idx + 1));

                            for (int i = first; i <= last; i++) {
                                shouldCount |= physicalCores.add(i);
                            }
                        }
                    }
                    if (shouldCount)
                        physical++;
                }

                builder.setCores(new CentralProcessor.Cores(physical, logical, physicalPackageIds.size()));
                return;
            } catch (Throwable e) {
                LOG.warning("Failed to detect cores in /sys/devices/system/cpu", e);
            }
        }

        builder.setCores(new CentralProcessor.Cores(logical));
    }

    static @Nullable CentralProcessor detect() {
        TreeMap<Integer, Map<String, String>> cpuInfo = loadCPUInfo();
        if (cpuInfo == null || cpuInfo.isEmpty()) return null;

        CentralProcessor.Builder builder = new CentralProcessor.Builder();
        detectName(builder, cpuInfo);
        if (builder.getName() == null)
            return null;

        detectCores(builder, cpuInfo);
        return builder.build();
    }

    private LinuxCPUDetector() {
    }
}
