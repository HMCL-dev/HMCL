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
package org.jackhuang.hmcl.util.platform.hardware;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class GraphicsCard {

    private static final class Win32_VideoController {
        String Name;
        String AdapterCompatibility;
        String DriverVersion;
    }

    private static @Nullable List<GraphicsCard> searchByCIM() {
        if (!OperatingSystem.isWindows7OrLater())
            return null;

        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe",
                    "-Command",
                    "Get-CimInstance -Class Win32_VideoController | Select-Object Name,AdapterCompatibility,DriverVersion | ConvertTo-Json")
                    .redirectError(new File("NUL"))
                    .start();

            String json = IOUtils.readFullyAsString(process.getInputStream(), OperatingSystem.NATIVE_CHARSET);
            if (process.waitFor() != 0)
                throw new IOException("Bad exit code: " + process.exitValue());

            List<Win32_VideoController> videoControllers = JsonUtils.GSON.fromJson(json, JsonUtils.listTypeOf(Win32_VideoController.class));
            if (videoControllers == null)
                return null;

            ArrayList<GraphicsCard> cards = new ArrayList<>(videoControllers.size());
            for (Win32_VideoController videoController : videoControllers) {
                if (videoController != null && videoController.Name != null) {
                    cards.add(new GraphicsCard(videoController.Name,
                            Vendor.of(videoController.AdapterCompatibility),
                            videoController.DriverVersion));
                }
            }

            return cards;
        } catch (Throwable e) {
            if (process != null && process.isAlive())
                process.destroy();

            LOG.warning("Failed to get graphics card info", e);
            return null;
        }
    }

    public static @Nullable List<GraphicsCard> listGraphicsCards() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return searchByCIM();
        }

        return null;
    }

    private final String name;
    private final @Nullable Vendor vendor;
    private final @Nullable String version;

    private GraphicsCard(String name, @Nullable Vendor vendor, @Nullable String version) {
        this.name = name;
        this.vendor = vendor;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public @Nullable Vendor getVendor() {
        return vendor;
    }

    public @Nullable String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(name);

        if (vendor != null || version != null) {
            builder.append(" (");

            if (vendor != null)
                builder.append(vendor);

            if (version != null) {
                if (vendor != null)
                    builder.append(", ");

                builder.append(version);
            }

            builder.append(')');
        }

        return builder.toString();
    }

    public static final class Vendor {
        public static final Vendor INTEL = new Vendor("Intel");
        public static final Vendor NVIDIA = new Vendor("NVIDIA");
        public static final Vendor AMD = new Vendor("AMD");

        @Contract("null -> null; !null -> !null")
        public static Vendor of(String name) {
            if (name == null)
                return null;

            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("intel")) return INTEL;
            if (lower.startsWith("nvidia")) return NVIDIA;
            if (lower.startsWith("amd")) return AMD;

            return new Vendor(name);
        }

        private final String name;

        public Vendor(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Vendor)) return false;
            return Objects.equals(name, ((Vendor) o).name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
