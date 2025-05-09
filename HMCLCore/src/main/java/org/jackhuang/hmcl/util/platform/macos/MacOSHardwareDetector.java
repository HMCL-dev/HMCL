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
package org.jackhuang.hmcl.util.platform.macos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareDetector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class MacOSHardwareDetector extends HardwareDetector {

    @Override
    public List<GraphicsCard> detectGraphicsCards() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.OSX)
            return null;

        Process process = null;
        String json = null;
        try {
            File devNull = new File("/dev/null");

            Process finalProcess = process = new ProcessBuilder("/usr/sbin/system_profiler",
                    "SPDisplaysDataType",
                    "-json")
                    .redirectInput(devNull)
                    .redirectError(devNull)
                    .start();

            CompletableFuture<String> future = CompletableFuture.supplyAsync(Lang.wrap(() ->
                            IOUtils.readFullyAsString(finalProcess.getInputStream(), OperatingSystem.NATIVE_CHARSET)),
                    Schedulers.io());

            if (!process.waitFor(15, TimeUnit.SECONDS))
                throw new TimeoutException();

            if (process.exitValue() != 0)
                throw new IOException("Bad exit code: " + process.exitValue());

            json = future.get();

            JsonObject object = JsonUtils.GSON.fromJson(json, JsonObject.class);
            JsonArray spDisplaysDataType = object.getAsJsonArray("SPDisplaysDataType");
            if (spDisplaysDataType != null) {
                ArrayList<GraphicsCard> cards = new ArrayList<>();

                for (JsonElement element : spDisplaysDataType) {
                    JsonObject item = element.getAsJsonObject();

                    JsonElement deviceType = item.get("sppci_device_type");
                    if (deviceType == null || !"spdisplays_gpu".equals(deviceType.getAsString()))
                        continue;

                    JsonElement model = item.getAsJsonPrimitive("sppci_model");
                    JsonElement vendor = item.getAsJsonPrimitive("spdisplays_vendor");
                    JsonElement bus = item.getAsJsonPrimitive("sppci_bus");

                    if (model == null)
                        continue;

                    GraphicsCard.Builder builder = GraphicsCard.builder()
                            .setName(model.getAsString());

                    if (vendor != null)
                        builder.setVendor(GraphicsCard.Vendor.of(StringUtils.removePrefix(vendor.getAsString(), "sppci_vendor_")));

                    GraphicsCard.Type type = GraphicsCard.Type.Integrated;
                    if (bus != null) {
                        String lower = bus.getAsString().toLowerCase(Locale.ROOT);
                        if (!lower.contains("builtin") && !lower.contains("built_in") & !lower.contains("built-in"))
                            type = GraphicsCard.Type.Discrete;
                    }
                    builder.setType(type);

                    cards.add(builder.build());
                }

                return Collections.unmodifiableList(cards);
            }
        } catch (Throwable e) {
            if (process != null && process.isAlive())
                process.destroy();

            LOG.warning("Failed to get graphics card info" + (json != null ? ": " + json : ""), e);
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
