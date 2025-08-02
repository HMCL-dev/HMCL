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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
final class FastFetchUtils {
    private FastFetchUtils() {
    }

    private static <T> T get(String type, TypeToken<T> resultType) {
        Path fastfetch = SystemUtils.which(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "fastfetch.exe" : "fastfetch");
        if (fastfetch == null)
            return null;

        String output;
        try {
            output = SystemUtils.run(Arrays.asList(fastfetch.toString(), "--structure", type, "--format", "json"),
                    inputStream -> IOUtils.readFullyAsString(inputStream, OperatingSystem.NATIVE_CHARSET));
        } catch (Throwable e) {
            LOG.warning("Failed to get result from fastfetch", e);
            return null;
        }

        try {
            // Sometimes there is some garbage before the output JSON, we should filter it out
            int idx = output.indexOf('[');
            String json = idx >= 0 ? output.substring(idx) : output;

            List<Result<T>> list = JsonUtils.GSON.fromJson(json, JsonUtils.listTypeOf(Result.typeOf(resultType)));

            Result<T> result;
            if (list == null
                    || list.size() != 1
                    || (result = list.get(0)) == null
                    || !type.equalsIgnoreCase(result.type)
                    || result.result == null) {
                throw new IOException("Illegal output format");
            }

            return result.result;
        } catch (Throwable e) {
            LOG.warning("Failed to parse fastfetch output: " + output, e);
            return null;
        }
    }

    static @Nullable CentralProcessor detectCentralProcessor() {
        CPUInfo cpuInfo = get("CPU", TypeToken.get(CPUInfo.class));

        if (cpuInfo == null)
            return null;

        CentralProcessor.Builder builder = new CentralProcessor.Builder()
                .setName(cpuInfo.cpu)
                .setVendor(HardwareVendor.of(cpuInfo.vendor));

        if (cpuInfo.cores != null) {
            try {
                String physical = cpuInfo.cores.get("physical");
                String logical = cpuInfo.cores.get("logical");

                int cores = physical != null ? Integer.parseInt(physical) : 0;
                int threads = logical != null ? Integer.parseInt(logical) : 0;
                int packages = Integer.max(cpuInfo.packages, 1);

                if (cores > 0 && threads == 0)
                    threads = cores;
                else if (threads > 0 && cores == 0)
                    cores = threads;

                builder.setCores(new CentralProcessor.Cores(cores, threads, packages));
            } catch (Throwable ignored) {
            }
        }

        return builder.build();
    }

    static @Nullable List<GraphicsCard> detectGraphicsCards() {
        List<GPUInfo> gpuInfos = get("GPU", JsonUtils.listTypeOf(GPUInfo.class));
        if (gpuInfos == null)
            return null;

        ArrayList<GraphicsCard> result = new ArrayList<>(gpuInfos.size());
        for (GPUInfo gpuInfo : gpuInfos) {
            if (gpuInfo == null)
                continue;

            GraphicsCard.Builder builder = new GraphicsCard.Builder()
                    .setName(gpuInfo.name)
                    .setVendor(HardwareVendor.of(gpuInfo.vendor))
                    .setDriver(gpuInfo.driver);

            if ("Discrete".equalsIgnoreCase(gpuInfo.type))
                builder.setType(GraphicsCard.Type.Discrete);
            else if ("Integrated".equalsIgnoreCase(gpuInfo.type))
                builder.setType(GraphicsCard.Type.Integrated);

            result.add(builder.build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static final class Result<T> {
        static <T> TypeToken<Result<T>> typeOf(TypeToken<T> type) {
            return (TypeToken<Result<T>>) TypeToken.getParameterized(Result.class, type.getType());
        }

        String type;
        T result;
    }

    private static final class CPUInfo {
        String cpu;
        String vendor;
        int packages;
        Map<String, String> cores;
    }

    private static final class GPUInfo {
        String name;
        String vendor;
        String type;
        String driver;
    }
}
