// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.macos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Detects macOS hardware information.
///
/// @author Glavo
@NotNullByDefault
public final class MacOSHardwareDetector extends HardwareDetector {

    /// Reads a structured Mac identity from `system_profiler`, falling back to `sysctl -n hw.model`.
    ///
    /// Preserves `machine_model` separately and uses it as the display name when `machine_name` is unavailable.
    /// Names use the shared whitespace and placeholder filtering. Returns `null` outside macOS
    /// or when no valid value is available. Interrupted queries restore the interrupt flag and stop detection.
    @Override
    public @Nullable MacDeviceInfo detectDeviceInfo() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS)
            return null;

        try {
            @Nullable MacDeviceInfo info = parseDeviceInfo(SystemUtils.run("/usr/sbin/system_profiler", "SPHardwareDataType", "-json"));
            if (info != null)
                return info;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOG.warning("Failed to detect device name using system_profiler", e);
        }

        try {
            @Nullable String modelIdentifier = cleanDeviceName(SystemUtils.run("/usr/sbin/sysctl", "-n", "hw.model"));
            return modelIdentifier == null ? null : new MacDeviceInfo(modelIdentifier, modelIdentifier);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOG.warning("Failed to detect device name using sysctl", e);
            return null;
        }
    }

    /// Extracts a model name and identifier from the first usable hardware entry in profiler JSON.
    /// Retains the identifier even when a readable name is available; uses it as the name if needed.
    /// Ignores missing, non-string, blank, and placeholder fields; returns `null` if none are usable.
    /// Malformed JSON is rejected by Gson so the caller can fall back to another source.
    static @Nullable MacDeviceInfo parseDeviceInfo(String json) {
        @Nullable JsonElement root = JsonUtils.GSON.fromJson(json, JsonElement.class);
        if (!(root instanceof JsonObject object)
                || !(object.get("SPHardwareDataType") instanceof JsonArray hardware))
            return null;

        for (JsonElement element : hardware) {
            if (!(element instanceof JsonObject item))
                continue;
            @Nullable String name = readDeviceNameField(item, "machine_name");
            @Nullable String modelIdentifier = readDeviceNameField(item, "machine_model");
            if (name != null)
                return new MacDeviceInfo(name, modelIdentifier);
            if (modelIdentifier != null)
                return new MacDeviceInfo(modelIdentifier, modelIdentifier);
        }
        return null;
    }

    /// Reads and cleans a profiler string field, returning `null` for absent or invalid values.
    private static @Nullable String readDeviceNameField(JsonObject object, String key) {
        return object.get(key) instanceof JsonPrimitive value && value.isString()
                ? cleanDeviceName(value.getAsString()) : null;
    }

    /// Detects macOS processors, using the default detector when native information is unavailable.
    @Override
    public @Unmodifiable List<CentralProcessor> detectCentralProcessors() {
        @Unmodifiable List<CentralProcessor> processors = OperatingSystem.CURRENT_OS == OperatingSystem.MACOS ? MacOSCPUDetector.detect() : List.of();
        return processors.isEmpty() ? super.detectCentralProcessors() : processors;
    }

    /// Returns an immutable graphics card list, empty if detection is unsupported or fails.
    @Override
    public @Unmodifiable List<GraphicsCard> detectGraphicsCards() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS)
            return List.of();

        @Nullable String json = null;
        try {
            json = SystemUtils.run("/usr/sbin/system_profiler", "SPDisplaysDataType", "-json");

            @Nullable JsonObject object = JsonUtils.GSON.fromJson(json, JsonObject.class);
            @Nullable JsonArray spDisplaysDataType = object == null ? null : object.getAsJsonArray("SPDisplaysDataType");
            if (spDisplaysDataType != null) {
                ArrayList<GraphicsCard> cards = new ArrayList<>();

                for (JsonElement element : spDisplaysDataType) {
                    JsonObject item = element.getAsJsonObject();

                    @Nullable JsonElement deviceType = item.get("sppci_device_type");
                    if (deviceType == null || !"spdisplays_gpu".equals(deviceType.getAsString()))
                        continue;

                    @Nullable JsonElement model = item.getAsJsonPrimitive("sppci_model");
                    @Nullable JsonElement vendor = item.getAsJsonPrimitive("spdisplays_vendor");
                    @Nullable JsonElement bus = item.getAsJsonPrimitive("sppci_bus");

                    if (model == null)
                        continue;

                    GraphicsCard.Builder builder = GraphicsCard.builder()
                            .setName(model.getAsString());

                    if (vendor != null)
                        builder.setVendor(HardwareVendor.of(StringUtils.removePrefix(vendor.getAsString(), "sppci_vendor_")));

                    GraphicsCard.Type type = GraphicsCard.Type.INTEGRATED;
                    if (HardwareVendor.VMWARE.equals(builder.getVendor()) || HardwareVendor.PARALLEL.equals(builder.getVendor())) {
                        type = GraphicsCard.Type.VIRTUAL;
                    } else if (bus != null) {
                        String lower = bus.getAsString().toLowerCase(Locale.ROOT);
                        if (!lower.contains("builtin") && !lower.contains("built_in") & !lower.contains("built-in"))
                            type = GraphicsCard.Type.DISCRETE;
                    }
                    builder.setType(type);

                    cards.add(builder.build());
                }

                return List.copyOf(cards);
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
                            new BufferedReader(new InputStreamReader(inputStream, SystemInfo.NATIVE_CHARSET))));
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
