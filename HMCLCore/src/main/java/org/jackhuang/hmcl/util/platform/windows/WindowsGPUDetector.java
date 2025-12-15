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
import java.util.*;
import java.util.regex.Pattern;

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
        } else if (object instanceof byte[]) {
            return new String((byte[]) object, StandardCharsets.UTF_16LE)
                    .replace("\0", "");
        } else {
            return object.toString();
        }
    }

    private static List<GraphicsCard> detectByRegistry(WinReg reg) {
        final WinReg.HKEY hkey = WinReg.HKEY.HKEY_LOCAL_MACHINE;
        final String displayDevices = "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}\\";
        final Pattern graphicsCardPattern = Pattern.compile("\\\\[0-9]+\\\\?$");

        var result = new ArrayList<GraphicsCard>();
        for (String subkey : reg.querySubKeys(hkey, displayDevices)) {
            if (!graphicsCardPattern.matcher(subkey).find())
                continue;

            String name = regValueToString(reg.queryValue(hkey, subkey, "HardwareInformation.AdapterString"));
            String vendor = regValueToString(reg.queryValue(hkey, subkey, "ProviderName"));
            String driverVersion = regValueToString(reg.queryValue(hkey, subkey, "DriverVersion"));
            String dacType = regValueToString(reg.queryValue(hkey, subkey, "HardwareInformation.DacType"));

            GraphicsCard.Builder builder = GraphicsCard.builder();
            if (name != null)
                builder.setName(GraphicsCard.cleanName(name));
            if (vendor != null)
                builder.setVendor(HardwareVendor.of(vendor));
            if (driverVersion != null)
                builder.setDriverVersion(driverVersion);
            if (dacType != null)
                builder.setType(fromDacType(dacType));
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
