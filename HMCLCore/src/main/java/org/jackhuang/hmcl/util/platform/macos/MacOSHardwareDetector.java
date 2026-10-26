// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.macos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class MacOSHardwareDetector extends HardwareDetector {

    @Override
    public @Nullable CentralProcessor detectCentralProcessor() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS)
            return null;

        try {
            Map<String, String> values = SystemUtils.run(Arrays.asList("/usr/sbin/sysctl", "machdep.cpu"),
                    inputStream -> KeyValuePairUtils.loadPairs(
                            new BufferedReader(new InputStreamReader(inputStream, OperatingSystem.NATIVE_CHARSET))));

            String brandString = values.get("machdep.cpu.brand_string");
            String coreCount = values.get("machdep.cpu.core_count");
            String threadCount = values.get("machdep.cpu.thread_count");
            String coresPerPackage = values.get("machdep.cpu.cores_per_package");

            CentralProcessor.Builder builder = new CentralProcessor.Builder();

            if (brandString != null) {
                builder.setName(brandString);

                String lower = brandString.toLowerCase(Locale.ROOT);
                if (lower.startsWith("apple"))
                    builder.setVendor(HardwareVendor.APPLE);
                else if (lower.startsWith("intel"))
                    builder.setVendor(HardwareVendor.INTEL);
            } else
                builder.setName("Unknown");

            if (coreCount != null || threadCount != null) {
                int cores = coreCount != null ? Integer.parseInt(coreCount) : 0;
                int threads = threadCount != null ? Integer.parseInt(threadCount) : 0;
                int coresPerPackageCount = coresPerPackage != null ? Integer.parseInt(coresPerPackage) : 0;

                if (cores > 0 && threads == 0)
                    threads = cores;
                else if (threads > 0 && cores == 0)
                    cores = threads;

                int packages = 1;
                if (cores > 0 && coresPerPackageCount > 0)
                    packages = Integer.max(cores / coresPerPackageCount, 1);

                builder.setCores(new CentralProcessor.Cores(cores, threads, packages));
            } else
                builder.setCores(new CentralProcessor.Cores(Runtime.getRuntime().availableProcessors()));

            return builder.build();
        } catch (Throwable e) {
            LOG.warning("Failed to get CPU info", e);
            return null;
        }
    }

    @Override
    public List<GraphicsCard> detectGraphicsCards() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS)
            return null;

        String json = null;
        try {
            json = SystemUtils.run("/usr/sbin/system_profiler", "SPDisplaysDataType", "-json");

            JsonObject object = JsonUtils.GSON.fromJson(json, JsonObject.class);
            JsonArray spDisplaysDataType = object.getAsJsonArray("SPDisplaysDataType");
            if (spDisplaysDataType != null) {
                ArrayList<GraphicsCard> cards = new ArrayList<>();

                for (JsonElement element : spDisplaysDataType) {
                    JsonObject item = element.getAsJsonObject();

                    JsonElement deviceType = item.get("sppci_device_type");
                    if (deviceType == null || !"spdisplays_gpu".equals(deviceType.getAsString()))
                        continue;

                    JsonElement model = item.getAsJsonPrimitive("sppci_model");
                    JsonElement vendor = item.getAsJsonPrimitive("spdisplays_vendor");
                    JsonElement bus = item.getAsJsonPrimitive("sppci_bus");

                    if (model == null)
                        continue;

                    GraphicsCard.Builder builder = GraphicsCard.builder()
                            .setName(model.getAsString());

                    if (vendor != null)
                        builder.setVendor(HardwareVendor.of(StringUtils.removePrefix(vendor.getAsString(), "sppci_vendor_")));

                    GraphicsCard.Type type = GraphicsCard.Type.Integrated;
                    if (bus != null) {
                        String lower = bus.getAsString().toLowerCase(Locale.ROOT);
                        if (!lower.contains("builtin") && !lower.contains("built_in") & !lower.contains("built-in"))
                            type = GraphicsCard.Type.Discrete;
                    }
                    builder.setType(type);

                    cards.add(builder.build());
                }

                return Collections.unmodifiableList(cards);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to get graphics card info" + (json != null ? ": " + json : ""), e);
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private static final Pattern PAGE_SIZE_PATTERN = Pattern.compile("\\(page size of (?<size>\\d+) bytes\\)");

    @Override
    public long getFreeMemorySize() {
        vmStat:
        try {
            Map<String, String> stats = SystemUtils.run(List.of("/usr/bin/vm_stat"),
                    inputStream -> KeyValuePairUtils.loadPairs(
                            new BufferedReader(new InputStreamReader(inputStream, OperatingSystem.NATIVE_CHARSET))));
            String statistics = stats.get("Mach Virtual Memory Statistics");

            long pageSize;
            long pagesFree;
            long pagesInactive;
            long pagesSpeculative;

            if (statistics != null) {
                Matcher matcher = PAGE_SIZE_PATTERN.matcher(statistics);
                if (matcher.find()) {
                    pageSize = Long.parseLong(matcher.group("size"));
                } else {
                    break vmStat;
                }
            } else {
                break vmStat;
            }

            String pagesFreeStr = stats.get("Pages free");
            if (pagesFreeStr != null && pagesFreeStr.endsWith(".")) {
                pagesFree = Long.parseUnsignedLong(pagesFreeStr, 0, pagesFreeStr.length() - 1, 10);
            } else {
                break vmStat;
            }

            String pagesInactiveStr = stats.get("Pages inactive");
            if (pagesInactiveStr != null && pagesInactiveStr.endsWith(".")) {
                pagesInactive = Long.parseUnsignedLong(pagesInactiveStr, 0, pagesInactiveStr.length() - 1, 10);
            } else {
                break vmStat;
            }

            String pagesSpeculativeStr = stats.get("Pages speculative");
            if (pagesSpeculativeStr != null && pagesSpeculativeStr.endsWith(".")) {
                pagesSpeculative = Long.parseUnsignedLong(pagesSpeculativeStr, 0, pagesSpeculativeStr.length() - 1, 10);
            } else {
                break vmStat;
            }

            long available = (pagesFree + pagesSpeculative + pagesInactive) * pageSize;
            if (available > 0) {
                return available;
            }
        } catch (Throwable e) {
            LOG.warning("Failed to parse vm_stat output", e);
        }

        return super.getFreeMemorySize();
    }
}
