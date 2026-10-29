// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
final class WindowsGPUDetector {

    /// Matching device IDs of known display-only adapters.
    private static final @Unmodifiable List<String> DISPLAY_ONLY_ADAPTER_MATCHING_DEVICE_IDS = List.of(
            // Microsoft Remote Display Adapter
            "RdpIdd_IndirectDisplay",

            // NetEase UU Remote Display Adapter
            "Root\\GameViewerIddDriver",

            // MuMu Virtual Display Adapter
            "Root\\MuMuIddDriver",

            // Honor Virtual Display Device
            "IndirectDSP\\VirtualDisplayDriver",

            // Huawei Virtual Display Device and virtual-display-rs
            "ROOT\\VIRTUALDISPLAYDRIVER",

            // Parsec Virtual Display Adapter
            "Root\\Parsec\\VDA",

            // MikeTheTech / VirtualDrivers Virtual Display Driver
            "Root\\MttVDD",
            "MttVDD",

            // Apollo / SudoMaker Virtual Display Adapter
            "Root\\SudoMaker\\SudoVDA",
            "SudoVDA",

            // RustDesk Indirect Display Driver
            "Root\\RustDeskIddDriver",
            "RustDeskIddDriver",

            // Microsoft Indirect Display Driver sample and derivatives
            "Root\\IddSampleDriver",
            "IddSampleDriver",

            // Amyuni USB Mobile Monitor Virtual Display
            "usbmmidd"
    );

    /// Identifies display-only and virtual adapters before applying AMD name rules and the DAC heuristic.
    /// Recognized AMD names are heuristic evidence, not a hardware guarantee. Unmatched names and
    /// other vendors retain the DAC result, or `UNKNOWN` when the DAC value is absent.
    ///
    /// @param name the adapter name or driver description normalized by [GraphicsCard#cleanName(String)], or `null`
    /// @param vendor the resolved hardware vendor, or `null`
    /// @param dacType the decoded DAC description, or `null` if absent
    /// @param matchingDeviceId the matched device ID, or `null` if absent
    /// @return the detected or estimated type, or `UNKNOWN` if no rule applies
    static GraphicsCard.@NotNull Type inferType(@Nullable String name,
                                                 @Nullable HardwareVendor vendor,
                                                 @Nullable String dacType,
                                                 @Nullable String matchingDeviceId) {
        if (matchingDeviceId != null) {
            for (String id : DISPLAY_ONLY_ADAPTER_MATCHING_DEVICE_IDS) {
                if (matchingDeviceId.equalsIgnoreCase(id)) {
                    return GraphicsCard.Type.DISPLAY_ONLY;
                }
            }
        }

        if (HardwareVendor.VMWARE.equals(vendor) || HardwareVendor.PARALLEL.equals(vendor))
            return GraphicsCard.Type.VIRTUAL;

        if (HardwareVendor.AMD.equals(vendor) && name != null) {
            String model = name.replaceFirst("(?i)^(AMD |ATI )?Radeon ", "");
            if (!model.equals(name)) {
                // RX Vega 11 is integrated, whereas RX Vega 56 and 64 are discrete.
                if (model.equalsIgnoreCase("Graphics")
                        || model.matches("(?i)[0-9]{3}M( Graphics)?")
                        || model.matches("(?i)(RX )?Vega ([2-9]|10|11)( Graphics)?"))
                    return GraphicsCard.Type.INTEGRATED;

                if (model.matches("(?i)RX [0-9]{3,4}([A-Z]*| .*)")
                        || model.matches("(?i)(RX )?Vega (56|64)( .*)?")
                        || model.matches("(?i)VII( .*)?"))
                    return GraphicsCard.Type.DISCRETE;
            }
        }

        if (dacType != null) {
            if (StringUtils.isBlank(dacType)
                    || "Internal".equalsIgnoreCase(dacType)
                    || "InternalDAC".equalsIgnoreCase(dacType)) {
                return GraphicsCard.Type.INTEGRATED;
            } else {
                return GraphicsCard.Type.DISCRETE;
            }
        } else {
            return GraphicsCard.Type.UNKNOWN;
        }
    }

    private static @Nullable List<Path> parseVulkanDriverFiles(Object vulkanDriverFiles) {
        if (vulkanDriverFiles instanceof String file) {
            try {
                return List.of(Path.of(file));
            } catch (InvalidPathException e) {
                LOG.warning("Failed to parse Vulkan ICD file path: " + file, e);
                return null;
            }
        } else if (vulkanDriverFiles instanceof String[] files) {
            try {
                return Stream.of(files).map(Path::of).toList();
            } catch (InvalidPathException e) {
                LOG.warning("Failed to parse Vulkan ICD file paths: " + Arrays.toString(files), e);
                return null;
            }
        } else
            return null;
    }

    /// Reads CIM video controllers and applies the shared name and DAC type heuristics.
    /// Returns an empty list if the query or its processing fails.
    private static List<GraphicsCard> detectByCim() {
        try {
            String getCimInstance = OperatingSystem.SYSTEM_VERSION.getVersion().startsWith("6.1")
                    ? "Get-WmiObject"
                    : "Get-CimInstance";

            List<Map<String, String>> videoControllers = SystemUtils.run(Arrays.asList(
                            "powershell.exe",
                            "-NoProfile",
                            "-Command",
                            String.join(" | ",
                                    getCimInstance + " -Class Win32_VideoController",
                                    "Select-Object Name,AdapterCompatibility,DriverVersion,AdapterDACType",
                                    "Format-List"
                            )),
                    inputStream -> KeyValuePairUtils.loadList(new BufferedReader(new InputStreamReader(inputStream, OperatingSystem.NATIVE_CHARSET))));

            ArrayList<GraphicsCard> cards = new ArrayList<>(videoControllers.size());
            for (Map<String, String> videoController : videoControllers) {
                @Nullable String name = GraphicsCard.cleanName(videoController.get("Name"));
                @Nullable HardwareVendor vendor = HardwareVendor.of(videoController.get("AdapterCompatibility"));
                @Nullable String driverVersion = videoController.get("DriverVersion");
                @Nullable String adapterDACType = videoController.get("AdapterDACType");

                if (StringUtils.isNotBlank(name)) {
                    cards.add(GraphicsCard.builder().setName(name)
                            .setVendor(vendor)
                            .setDriverVersion(driverVersion)
                            .setType(inferType(name, vendor, adapterDACType, null))
                            .build()
                    );
                }
            }

            return cards;
        } catch (Throwable e) {
            LOG.warning("Failed to get graphics card info", e);
            return List.of();
        }
    }

    private static @Nullable String regValueToString(Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof String[]) {
            return String.join(" ", (String[]) object);
        } else if (object instanceof byte[] data) {
            // Ensure an even byte length for UTF_16LE
            int length = data.length & ~1;
            return new String(data, 0, length, StandardCharsets.UTF_16LE)
                    .replace("\0", "");
        } else {
            return object.toString();
        }
    }

    /// Reads graphics card information from the display adapter registry keys.
    /// Uses the driver description when the adapter name is missing or blank;
    /// if both are missing or blank, leaves the name to the builder's fallback.
    private static List<GraphicsCard> detectByRegistry(WinReg reg) {
        final WinReg.HKEY hkey = WinReg.HKEY.HKEY_LOCAL_MACHINE;
        final String displayDevices = "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}\\";
        final Pattern graphicsCardPattern = Pattern.compile("\\\\[0-9]+\\\\?$");

        var result = new ArrayList<GraphicsCard>();
        for (String subkey : reg.querySubKeys(hkey, displayDevices)) {
            if (!graphicsCardPattern.matcher(subkey).find())
                continue;

            @Nullable String name = regValueToString(reg.queryValue(hkey, subkey, "HardwareInformation.AdapterString"));
            if (StringUtils.isBlank(name))
                name = regValueToString(reg.queryValue(hkey, subkey, "DriverDesc"));
            name = GraphicsCard.cleanName(name);
            @Nullable HardwareVendor vendor = HardwareVendor.of(regValueToString(reg.queryValue(hkey, subkey, "ProviderName")));
            @Nullable String driverVersion = regValueToString(reg.queryValue(hkey, subkey, "DriverVersion"));
            @Nullable String dacType = regValueToString(reg.queryValue(hkey, subkey, "HardwareInformation.DacType"));
            @Nullable Object vulkanDriverName = reg.queryValue(hkey, subkey, "VulkanDriverName");
            @Nullable String matchingDeviceId = regValueToString(reg.queryValue(hkey, subkey, "MatchingDeviceId"));

            GraphicsCard.Builder builder = GraphicsCard.builder();
            if (StringUtils.isNotBlank(name))
                builder.setName(name);
            if (vendor != null)
                builder.setVendor(vendor);
            if (driverVersion != null)
                builder.setDriverVersion(driverVersion);
            builder.setType(inferType(name, vendor, dacType, matchingDeviceId));
            if (vulkanDriverName != null)
                builder.setVulkanDriverFiles(parseVulkanDriverFiles(vulkanDriverName));
            result.add(builder.build());
        }
        return result;
    }

    /// Returns an immutable adapter list, empty if detection fails.
    static @NotNull @Unmodifiable List<GraphicsCard> detect() {
        try {
            @Nullable WinReg reg = WinReg.INSTANCE;
            if (reg != null) {
                List<GraphicsCard> res = detectByRegistry(reg);
                if (!res.isEmpty())
                    return List.copyOf(res);
            }
            return List.copyOf(detectByCim());
        } catch (Throwable e) {
            LOG.warning("Failed to get graphics cards", e);
            return List.of();
        }
    }

    private WindowsGPUDetector() {
    }
}
