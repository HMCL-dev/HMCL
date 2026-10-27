// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jetbrains.annotations.Nullable;

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

    private static GraphicsCard.Type fromDacType(String adapterDACType) {
        if (StringUtils.isBlank(adapterDACType)
                || "Internal".equalsIgnoreCase(adapterDACType)
                || "InternalDAC".equalsIgnoreCase(adapterDACType)) {
            return GraphicsCard.Type.Integrated;
        } else {
            return GraphicsCard.Type.Discrete;
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
                String name = videoController.get("Name");
                String adapterCompatibility = videoController.get("AdapterCompatibility");
                String driverVersion = videoController.get("DriverVersion");
                String adapterDACType = videoController.get("AdapterDACType");

                if (StringUtils.isNotBlank(name)) {
                    cards.add(GraphicsCard.builder().setName(GraphicsCard.cleanName(name))
                            .setVendor(HardwareVendor.of(adapterCompatibility))
                            .setDriverVersion(driverVersion)
                            .setType(fromDacType(adapterDACType))
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
            @Nullable String vendor = regValueToString(reg.queryValue(hkey, subkey, "ProviderName"));
            @Nullable String driverVersion = regValueToString(reg.queryValue(hkey, subkey, "DriverVersion"));
            @Nullable String dacType = regValueToString(reg.queryValue(hkey, subkey, "HardwareInformation.DacType"));
            @Nullable Object vulkanDriverName = reg.queryValue(hkey, subkey, "VulkanDriverName");
            @Nullable String matchingDeviceId = regValueToString(reg.queryValue(hkey, subkey, "MatchingDeviceId"));

            GraphicsCard.Builder builder = GraphicsCard.builder();
            builder.setVirtualDisplayAdapter("Root\\GameViewerIddDriver".equalsIgnoreCase(matchingDeviceId));
            if (StringUtils.isNotBlank(name))
                builder.setName(GraphicsCard.cleanName(name));
            if (vendor != null)
                builder.setVendor(HardwareVendor.of(vendor));
            if (driverVersion != null)
                builder.setDriverVersion(driverVersion);
            if (dacType != null)
                builder.setType(fromDacType(dacType));
            if (vulkanDriverName != null)
                builder.setVulkanDriverFiles(parseVulkanDriverFiles(vulkanDriverName));
            result.add(builder.build());
        }
        return result;
    }

    static @Nullable List<GraphicsCard> detect() {
        try {
            WinReg reg = WinReg.INSTANCE;
            if (reg != null) {
                List<GraphicsCard> res = detectByRegistry(reg);
                if (!res.isEmpty())
                    return res;
            }
            return detectByCim();
        } catch (Throwable e) {
            LOG.warning("Failed to get graphics cards", e);
            return null;
        }
    }

    private WindowsGPUDetector() {
    }
}
