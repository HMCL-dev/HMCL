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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WindowsHardwareDetector extends HardwareDetector {

    private static final class Win32_VideoController {
        String Name;
        String AdapterCompatibility;
        String DriverVersion;
    }

    @Override
    public @NotNull List<GraphicsCard> detectGraphicsCards() {
        if (!OperatingSystem.isWindows7OrLater())
            return Collections.emptyList();

        Process process = null;
        String json = null;
        try {
            process = new ProcessBuilder("powershell.exe",
                    "-Command",
                    "Get-CimInstance -Class Win32_VideoController | Select-Object Name,AdapterCompatibility,DriverVersion | ConvertTo-Json")
                    .redirectError(new File("NUL"))
                    .start();

            json = IOUtils.readFullyAsString(process.getInputStream(), OperatingSystem.NATIVE_CHARSET);
            if (process.waitFor() != 0)
                throw new IOException("Bad exit code: " + process.exitValue());

            JsonReader reader = new JsonReader(new StringReader(json));

            List<Win32_VideoController> videoControllers;
            JsonToken firstToken = reader.peek();
            if (firstToken == JsonToken.BEGIN_ARRAY)
                videoControllers = JsonUtils.GSON.fromJson(reader, JsonUtils.listTypeOf(Win32_VideoController.class));
            else if (firstToken == JsonToken.BEGIN_OBJECT)
                videoControllers = Collections.singletonList(JsonUtils.GSON.fromJson(reader, Win32_VideoController.class));
            else
                return Collections.emptyList();

            ArrayList<GraphicsCard> cards = new ArrayList<>(videoControllers.size());
            for (Win32_VideoController videoController : videoControllers) {
                if (videoController != null && videoController.Name != null) {
                    cards.add(GraphicsCard.builder().setName(videoController.Name)
                            .setVendor(GraphicsCard.Vendor.of(videoController.AdapterCompatibility))
                            .setDriverVersion(videoController.DriverVersion)
                            .build()
                    );
                }
            }

            return cards;
        } catch (Throwable e) {
            if (process != null && process.isAlive())
                process.destroy();

            LOG.warning("Failed to get graphics card info" + (json != null ? ": " + json : ""), e);
            return Collections.emptyList();
        }
    }
}
