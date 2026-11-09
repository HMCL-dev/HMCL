// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.macos;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A known Mac product with size, unambiguous chip, and release-date details for localization.
///
/// @param productName the product name, such as `Mac mini`, preserved across locales
/// @param details the ordered description components, copied into an immutable list
@NotNullByDefault
public record MacModel(String productName, @Unmodifiable List<Detail> details) {

    /// Validates the product name and takes an immutable snapshot of the details.
    public MacModel {
        Objects.requireNonNull(productName);
        details = List.copyOf(details);
    }

    /// One description component whose type selects its presentation.
    ///
    /// @param type the component type: `size`, `chip`, `year`, `early`, `mid`, `late`, or `month`
    /// @param arguments the ordered template arguments, copied into an immutable list
    @NotNullByDefault
    public record Detail(String type, @Unmodifiable List<String> arguments) {
        /// Copies the arguments and rejects unknown types, incorrect argument counts, and invalid month dates.
        public Detail {
            Objects.requireNonNull(type);
            arguments = List.copyOf(arguments);
            int argumentCount = switch (type) {
                case "year", "size", "chip", "early", "mid", "late" -> 1;
                case "month" -> 2;
                default -> throw new IllegalArgumentException("Unknown Mac model detail type: " + type);
            };
            if (arguments.size() != argumentCount)
                throw new IllegalArgumentException("Invalid argument count for Mac model detail: " + type);
            if (type.equals("month"))
                YearMonth.of(Integer.parseInt(arguments.get(0)), Integer.parseInt(arguments.get(1)));
        }
    }

    /// Returns the known model for an exact identifier, or `null` for missing or unknown identifiers.
    /// Reads the bundled model table on each lookup without retaining a cache.
    public static @Nullable MacModel find(@Nullable String identifier) {
        return identifier == null ? null : loadModels().get(identifier);
    }

    /// Reads the versioned, unquoted CSV resource, skipping comments and blank lines.
    /// The first two columns form the identifier, followed by the product name and `type:argument...` details.
    /// Rejects duplicate identifiers and invalid details; failure preserves system-provided fallback names.
    private static @Unmodifiable Map<String, MacModel> loadModels() {
        try (@Nullable InputStream input = MacModel.class.getResourceAsStream("/assets/platform/mac-models.csv")) {
            if (input == null)
                throw new IOException("Missing Mac model resource");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                Map<String, MacModel> result = new HashMap<>();
                @Nullable String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.isBlank())
                        continue;
                    String @Unmodifiable [] columns = line.split(",", -1);
                    if (columns.length < 3 || columns[0].isBlank() || columns[1].isBlank() || columns[2].isBlank())
                        throw new IOException("Invalid Mac model row: " + line);
                    String identifier = columns[0] + "," + columns[1];
                    List<Detail> details = new ArrayList<>();
                    for (int i = 3; i < columns.length; i++) {
                        String @Unmodifiable [] parts = columns[i].split(":", -1);
                        Detail detail = new Detail(parts[0], List.copyOf(Arrays.asList(parts).subList(1, parts.length)));
                        details.add(detail);
                    }
                    if (result.putIfAbsent(identifier, new MacModel(columns[2], details)) != null)
                        throw new IOException("Duplicate Mac model identifier: " + identifier);
                }
                return Map.copyOf(result);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to load Mac model mappings", e);
            return Map.of();
        }
    }
}
