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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
final class WindowsGPUDetector {
    public static final String DISPLAY_DEVICES_REGISTRY_PATH =
            "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}\\";

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
            String getCimInstance = OperatingSystem.SYSTEM_VERSION.startsWith("6.1")
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

    private static List<GraphicsCard> detectByRegistry(WinReg reg) {
        final WinReg.HKEY hkey = WinReg.HKEY.HKEY_CURRENT_USER_LOCAL_SETTINGS;

        var result = new ArrayList<GraphicsCard>();
        for (String subkey : reg.querySubKeys(hkey, DISPLAY_DEVICES_REGISTRY_PATH)) {
            try {
                Integer.parseInt(subkey);

                Object name = reg.queryValue(hkey, subkey, "HardwareInformation.AdapterString");
                Object vendor = reg.queryValue(hkey, subkey, "ProviderName");
                Object driverVersion = reg.queryValue(hkey, subkey, "DriverVersion");
                Object dacType = reg.queryValue(hkey, subkey, "HardwareInformation.DacType");

                GraphicsCard.Builder builder = GraphicsCard.builder();
                if (name instanceof String)
                    builder.setName(GraphicsCard.cleanName((String) name));
                if (vendor instanceof String)
                    builder.setVendor(HardwareVendor.of((String) vendor));
                if (driverVersion instanceof String)
                    builder.setDriverVersion(((String) driverVersion));
                if (dacType instanceof String)
                    builder.setType(fromDacType((String) dacType));
                result.add(builder.build());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    static List<GraphicsCard> detect() {
        WinReg reg = WinReg.INSTANCE;
        if (reg != null) {
            List<GraphicsCard> res = detectByRegistry(reg);
            if (!res.isEmpty())
                return res;
        }

        return detectByCim();
    }

    private WindowsGPUDetector() {
    }
}
