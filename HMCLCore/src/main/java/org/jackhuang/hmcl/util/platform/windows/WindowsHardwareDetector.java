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
import org.jackhuang.hmcl.util.platform.NativeUtils;
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

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WindowsHardwareDetector extends HardwareDetector {

    @Override
    public @Nullable CentralProcessor detectCentralProcessor() {
        if (!OperatingSystem.isWindows7OrLater())
            return null;
        return WindowsCPUDetector.detect();
    }

    @Override
    public List<GraphicsCard> detectGraphicsCards() {
        if (!OperatingSystem.isWindows7OrLater())
            return null;

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
                            .setType(StringUtils.isBlank(adapterDACType)
                                    || "Internal".equalsIgnoreCase(adapterDACType)
                                    || "InternalDAC".equalsIgnoreCase(adapterDACType)
                                    ? GraphicsCard.Type.Integrated
                                    : GraphicsCard.Type.Discrete)
                            .build()
                    );
                }
            }

            return cards;
        } catch (Throwable e) {
            LOG.warning("Failed to get graphics card info", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long getTotalMemorySize() {
        if (NativeUtils.USE_JNA) {
            Kernel32 kernel32 = Kernel32.INSTANCE;
            if (kernel32 != null) {
                WinTypes.MEMORYSTATUSEX status = new WinTypes.MEMORYSTATUSEX();
                if (kernel32.GlobalMemoryStatusEx(status))
                    return status.ullTotalPhys;
                else
                    LOG.warning("Failed to get memory status: " + kernel32.GetLastError());
            }
        }

        return super.getTotalMemorySize();
    }

    @Override
    public long getFreeMemorySize() {
        if (NativeUtils.USE_JNA) {
            Kernel32 kernel32 = Kernel32.INSTANCE;
            if (kernel32 != null) {
                WinTypes.MEMORYSTATUSEX status = new WinTypes.MEMORYSTATUSEX();
                if (kernel32.GlobalMemoryStatusEx(status))
                    return status.ullAvailPhys;
                else
                    LOG.warning("Failed to get memory status: " + kernel32.GetLastError());
            }
        }

        return super.getFreeMemorySize();
    }
}
