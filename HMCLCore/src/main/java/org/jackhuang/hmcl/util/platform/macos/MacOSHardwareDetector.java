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
import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
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
public final class MacOSHardwareDetector extends HardwareDetector {

    @Override
    public @Nullable CentralProcessor detectCentralProcessor() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS)
            return null;

        try {
            Map<String, String> values = SystemUtils.run(Arrays.asList("/usr/sbin/sysctl", "machdep.cpu"),
                    inputStream -> KeyValuePairUtils.loadProperties(
                            new BufferedReader(new InputStreamReader(inputStream, OperatingSystem.NATIVE_CHARSET))));

            String brandString = values.get("machdep.cpu.brand_string");
            String coreCount = values.get("machdep.cpu.core_count");
            String threadCount = values.get("machdep.cpu.thread_count");
            String coresPerPackage = values.get("machdep.cpu.cores_per_package");

            CentralProcessor.Builder builder = new CentralProcessor.Builder();

            if (brandString != null) {
                builder.setName(brandString);

                String lower = brandString.toLowerCase(Locale.ROOT);
                if (lower.startsWith("apple"))
                    builder.setVendor(HardwareVendor.APPLE);
                else if (lower.startsWith("intel"))
                    builder.setVendor(HardwareVendor.INTEL);
            } else
                builder.setName("Unknown");

            if (coreCount != null || threadCount != null) {
                int cores = coreCount != null ? Integer.parseInt(coreCount) : 0;
                int threads = threadCount != null ? Integer.parseInt(threadCount) : 0;
                int coresPerPackageCount = coresPerPackage != null ? Integer.parseInt(coresPerPackage) : 0;

                if (cores > 0 && threads == 0)
                    threads = cores;
                else if (threads > 0 && cores == 0)
                    cores = threads;

                int packages = 1;
                if (cores > 0 && coresPerPackageCount > 0)
                    packages = Integer.max(cores / coresPerPackageCount, 1);

                builder.setCores(new CentralProcessor.Cores(cores, threads, packages));
            } else
                builder.setCores(new CentralProcessor.Cores(Runtime.getRuntime().availableProcessors()));

            return builder.build();
        } catch (Throwable e) {
            LOG.warning("Failed to get CPU info", e);
            return null;
        }
    }

    @Override
    public List<GraphicsCard> detectGraphicsCards() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS)
            return null;

        String json = null;
        try {
            json = SystemUtils.run("/usr/sbin/system_profiler", "SPDisplaysDataType", "-json");

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
                        builder.setVendor(HardwareVendor.of(StringUtils.removePrefix(vendor.getAsString(), "sppci_vendor_")));

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
            LOG.warning("Failed to get graphics card info" + (json != null ? ": " + json : ""), e);
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
