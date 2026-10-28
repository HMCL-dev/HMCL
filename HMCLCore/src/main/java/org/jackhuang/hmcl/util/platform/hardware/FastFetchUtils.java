// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Reads processor and graphics adapter metadata from Fastfetch JSON output.
///
/// @author Glavo
@NotNullByDefault
final class FastFetchUtils {
    /// Prevents instantiation of this utility class.
    private FastFetchUtils() {
    }

    /// Runs Fastfetch for one module, returning `null` if execution or response parsing fails.
    private static <T> @Nullable T get(String type, TypeToken<T> resultType) {
        @Nullable Path fastfetch = SystemUtils.which(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "fastfetch.exe" : "fastfetch");
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

            @Nullable List<@Nullable Result<T>> list = JsonUtils.GSON.fromJson(json, JsonUtils.listTypeOf(Result.typeOf(resultType)));

            @Nullable Result<T> result;
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

    /// Detects a processor, returning `null` if Fastfetch provides no usable CPU response.
    static @Nullable CentralProcessor detectCentralProcessor() {
        @Nullable CPUInfo cpuInfo = get("CPU", TypeToken.get(CPUInfo.class));

        if (cpuInfo == null)
            return null;

        CentralProcessor.Builder builder = new CentralProcessor.Builder()
                .setName(cpuInfo.cpu)
                .setVendor(HardwareVendor.of(cpuInfo.vendor));

        if (cpuInfo.cores != null) {
            try {
                @Nullable String physical = cpuInfo.cores.get("physical");
                @Nullable String logical = cpuInfo.cores.get("logical");

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

    /// Returns a mutable list of detected adapters, or `null` if no usable GPU response is available.
    static @Nullable List<GraphicsCard> detectGraphicsCards() {
        @Nullable List<@Nullable GPUInfo> gpuInfos = get("GPU", JsonUtils.listTypeOf(GPUInfo.class));
        if (gpuInfos == null)
            return null;

        ArrayList<GraphicsCard> result = new ArrayList<>(gpuInfos.size());
        for (@Nullable GPUInfo gpuInfo : gpuInfos) {
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

    /// A Fastfetch module response whose fields may be absent from JSON.
    @SuppressWarnings("unchecked")
    private static final class Result<T> {
        /// Returns the Gson type token for a response with the supplied payload type.
        static <T> TypeToken<Result<T>> typeOf(TypeToken<T> type) {
            return (TypeToken<Result<T>>) TypeToken.getParameterized(Result.class, type.getType());
        }

        /// The module name, or `null` if absent.
        @Nullable String type;
        /// The module payload, or `null` if absent.
        @Nullable T result;
    }

    /// Processor metadata decoded from a Fastfetch CPU response.
    private static final class CPUInfo {
        /// The processor name, or `null` if absent.
        @Nullable String cpu;
        /// The vendor name, or `null` if absent.
        @Nullable String vendor;
        /// The package count; zero when absent.
        int packages;
        /// Core counts keyed by kind, or `null` if absent.
        @Nullable Map<String, @Nullable String> cores;
    }

    /// Adapter metadata decoded from a Fastfetch GPU response.
    private static final class GPUInfo {
        /// The adapter name, or `null` if absent.
        @Nullable String name;
        /// The vendor name, or `null` if absent.
        @Nullable String vendor;
        /// The adapter classification, or `null` if absent.
        @Nullable String type;
        /// The driver name, or `null` if absent.
        @Nullable String driver;
    }
}
