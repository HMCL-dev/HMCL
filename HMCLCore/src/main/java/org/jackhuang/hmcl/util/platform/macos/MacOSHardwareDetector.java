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
import org.jackhuang.hmcl.util.platform.OperatingSystemInfo;
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
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Detects macOS system and hardware information.
///
/// @author Glavo
@NotNullByDefault
public final class MacOSHardwareDetector extends HardwareDetector {

    /// Reads the macOS name, version, and optional build number from `sw_vers`, adding known release names.
    /// Falls back to JVM information on failure or incomplete output, preserving interruption.
    @Override
    public OperatingSystemInfo detectOperatingSystemInfo() {
        if (SystemInfo.CURRENT_OS != OperatingSystem.MACOS)
            return super.detectOperatingSystemInfo();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("/usr/bin/sw_vers");
            // Older macOS releases can otherwise report a compatibility version such as 10.16.
            processBuilder.environment().put("SYSTEM_VERSION_COMPAT", "0");
            String output = SystemUtils.run(processBuilder, Duration.ofSeconds(5));
            @Nullable OperatingSystemInfo info = parseOperatingSystemInfo(output);
            if (info != null)
                return info;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warning("Failed to detect macOS version using sw_vers", e);
        }
        return super.detectOperatingSystemInfo();
    }

    /// Parses `sw_vers` output, returning null when the product name or version is missing or blank.
    /// Expands generic Apple product names for known releases and includes optional version suffixes and build numbers.
    static @Nullable OperatingSystemInfo parseOperatingSystemInfo(String output) {
        Map<String, String> properties = KeyValuePairUtils.loadPairs(output.lines().iterator());
        @Nullable String name = properties.get("ProductName");
        @Nullable String version = properties.get("ProductVersion");
        if (name == null || name.isBlank() || version == null || version.isBlank())
            return null;

        if (name.equals("macOS") || name.equals("Mac OS X") || name.equals("OS X")) {
            @Nullable String releaseName = getReleaseName(version);
            if (releaseName != null)
                name = releaseName;
        }

        String description = name + ' ' + version;
        @Nullable String extra = properties.get("ProductVersionExtra");
        if (extra != null && !extra.isBlank())
            description += ' ' + extra;
        @Nullable String build = properties.get("BuildVersion");
        if (build != null && !build.isBlank())
            description += " (" + build + ')';
        return new OperatingSystemInfo(name, description);
    }

    /// Returns the Apple release name for a known numeric product version, or null otherwise.
    /// See https://support.apple.com/109033 for the version-to-name mapping.
    private static @Nullable String getReleaseName(String version) {
        if (!version.matches("[0-9]+(?:\\.[0-9]+)*"))
            return null;

        String[] parts = version.split("\\.", 3);
        if (parts[0].equals("10") && parts.length > 1) {
            return switch (parts[1]) {
                case "0" -> "Mac OS X Cheetah";
                case "1" -> "Mac OS X Puma";
                case "2" -> "Mac OS X Jaguar";
                case "3" -> "Mac OS X Panther";
                case "4" -> "Mac OS X Tiger";
                case "5" -> "Mac OS X Leopard";
                case "6" -> "Mac OS X Snow Leopard";
                case "7" -> "OS X Lion";
                case "8" -> "OS X Mountain Lion";
                case "9" -> "OS X Mavericks";
                case "10" -> "OS X Yosemite";
                case "11" -> "OS X El Capitan";
                case "12" -> "macOS Sierra";
                case "13" -> "macOS High Sierra";
                case "14" -> "macOS Mojave";
                case "15" -> "macOS Catalina";
                default -> null;
            };
        }
        return switch (parts[0]) {
            case "11" -> "macOS Big Sur";
            case "12" -> "macOS Monterey";
            case "13" -> "macOS Ventura";
            case "14" -> "macOS Sonoma";
            case "15" -> "macOS Sequoia";
            case "26" -> "macOS Tahoe";
            case "27" -> "macOS Golden Gate";
            default -> null;
        };
    }

    /// Reads a structured Mac identity from `system_profiler`, falling back to `sysctl -n hw.model`.
    ///
    /// Preserves `machine_model` separately and uses it as the display name when `machine_name` is unavailable.
    /// Names use the shared whitespace and placeholder filtering. Returns `null` outside macOS
    /// or when no valid value is available. Interrupted queries restore the interrupt flag and stop detection.
    @Override
    public @Nullable MacDeviceInfo detectDeviceInfo() {
        if (SystemInfo.CURRENT_OS != OperatingSystem.MACOS)
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
        @Unmodifiable List<CentralProcessor> processors = SystemInfo.CURRENT_OS == OperatingSystem.MACOS ? MacOSCPUDetector.detect() : List.of();
        return processors.isEmpty() ? super.detectCentralProcessors() : processors;
    }

    /// Returns an immutable graphics card list, empty if detection is unsupported or fails.
    @Override
    public @Unmodifiable List<GraphicsCard> detectGraphicsCards() {
        if (SystemInfo.CURRENT_OS != OperatingSystem.MACOS)
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
