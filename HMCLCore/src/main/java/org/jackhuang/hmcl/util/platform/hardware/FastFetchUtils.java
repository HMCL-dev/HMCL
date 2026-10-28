// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

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

    /// Returns the first reported processor identity; counts are used only for an explicitly single-package response.
    static @Unmodifiable List<CentralProcessor> detectCentralProcessors() {
        @Nullable CPUInfo cpuInfo = get("CPU", TypeToken.get(CPUInfo.class));
        if (cpuInfo == null)
            return List.of();
        return parseCentralProcessors(cpuInfo.cpu, cpuInfo.vendor, cpuInfo.packages, cpuInfo.cores);
    }

    /// Converts Fastfetch totals without assigning multi-package counts to an individual processor.
    static @Unmodifiable List<CentralProcessor> parseCentralProcessors(@Nullable String name, @Nullable String vendor,
                                                                      @Nullable Integer packages, @Nullable Map<String, String> counts) {
        CentralProcessor.Builder builder = new CentralProcessor.Builder()
                .setName(name).setVendor(HardwareVendor.of(vendor));
        if (packages != null && packages == 1 && counts != null) {
            try {
                @Nullable String physical = counts.get("physical");
                @Nullable String logical = counts.get("logical");
                int cores = physical != null ? Integer.parseInt(physical) : 0;
                int threads = logical != null ? Integer.parseInt(logical) : 0;
                if (cores > 0 && threads > 0)
                    builder.setCores(new CentralProcessor.Cores(threads, cores, 0));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.of(builder.build());
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

    /// A Fastfetch module response.
    ///
    /// @param type the module name, or `null` if absent
    /// @param result the module payload, or `null` if absent
    @JsonSerializable
    private record Result<T>(@Nullable String type, @Nullable T result) {

        /// Returns the Gson type token for a response with the supplied payload type.
        @SuppressWarnings("unchecked")
        static <T> TypeToken<Result<T>> typeOf(TypeToken<T> type) {
            return (TypeToken<Result<T>>) TypeToken.getParameterized(Result.class, type.getType());
        }
    }

    /// Processor metadata decoded from a Fastfetch CPU response.
    ///
    /// @param cpu the processor name, or `null` if absent
    /// @param vendor the vendor name, or `null` if absent
    /// @param packages the package count, or `null` if absent
    /// @param cores core counts keyed by kind, or `null` if absent
    @JsonSerializable
    private record CPUInfo(@Nullable String cpu, @Nullable String vendor, @Nullable Integer packages,
                           @Nullable Map<String, @Nullable String> cores) {
    }

    /// Adapter metadata decoded from a Fastfetch GPU response.
    ///
    /// @param name the adapter name, or `null` if absent
    /// @param vendor the vendor name, or `null` if absent
    /// @param type the adapter classification, or `null` if absent
    /// @param driver the driver name, or `null` if absent
    @JsonSerializable
    private record GPUInfo(@Nullable String name, @Nullable String vendor,
                           @Nullable String type, @Nullable String driver) {
    }
}
