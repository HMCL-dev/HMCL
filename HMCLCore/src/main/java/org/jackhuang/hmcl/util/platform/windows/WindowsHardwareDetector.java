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

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WindowsHardwareDetector extends HardwareDetector {

    private static List<Map<String, String>> parsePowerShellFormatList(Iterable<String> lines) {
        ArrayList<Map<String, String>> result = new ArrayList<>();
        Map<String, String> current = new LinkedHashMap<>();

        for (String line : lines) {
            int idx = line.indexOf(':');

            if (idx < 0) {
                if (!current.isEmpty()) {
                    result.add(current);
                    current = new LinkedHashMap<>();
                }
                continue;
            }

            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();

            current.put(key, value);
        }

        if (!current.isEmpty())
            result.add(current);

        return result;
    }

    @Override
    public @NotNull List<GraphicsCard> detectGraphicsCards() {
        if (!OperatingSystem.isWindows7OrLater())
            return null;

        Process process = null;
        String list = null;
        try {
            File nul = new File("NUL");

            String getCimInstance = OperatingSystem.SYSTEM_VERSION.startsWith("6.1")
                    ? "Get-WmiObject"
                    : "Get-CimInstance";

            Process finalProcess = process = new ProcessBuilder("powershell.exe",
                    "-Command",
                    String.join(" | ",
                            getCimInstance + " -Class Win32_VideoController",
                            "Select-Object Name,AdapterCompatibility,DriverVersion,AdapterDACType",
                            "Format-List"
                    ))
                    .redirectInput(nul)
                    .redirectError(nul)
                    .start();

            CompletableFuture<String> future = CompletableFuture.supplyAsync(Lang.wrap(() ->
                            IOUtils.readFullyAsString(finalProcess.getInputStream(), OperatingSystem.NATIVE_CHARSET)),
                    Schedulers.io());

            if (!process.waitFor(15, TimeUnit.SECONDS))
                throw new TimeoutException();

            if (process.exitValue() != 0)
                throw new IOException("Bad exit code: " + process.exitValue());

            list = future.get();

            List<Map<String, String>> videoControllers = parsePowerShellFormatList(Arrays.asList(list.split("\\R")));
            ArrayList<GraphicsCard> cards = new ArrayList<>(videoControllers.size());
            for (Map<String, String> videoController : videoControllers) {
                String name = videoController.get("Name");
                String adapterCompatibility = videoController.get("AdapterCompatibility");
                String driverVersion = videoController.get("DriverVersion");
                String adapterDACType = videoController.get("AdapterDACType");

                if (StringUtils.isNotBlank(name)) {
                    cards.add(GraphicsCard.builder().setName(name)
                            .setVendor(GraphicsCard.Vendor.of(adapterCompatibility))
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
            if (process != null && process.isAlive())
                process.destroy();
            LOG.warning("Failed to get graphics card info" + (list != null ? ": " + list : ""), e);
            return Collections.emptyList();
        }
    }
}
