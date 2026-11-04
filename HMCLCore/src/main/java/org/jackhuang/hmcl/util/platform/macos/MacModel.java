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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A known Mac product with size and unambiguous release-date details for diagnostics and localization.
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

    /// Builds the English diagnostic name from the product name and ordered details.
    public String name() {
        return details.isEmpty() ? productName : productName + " ("
                + details.stream().map(Detail::formatEnglish).collect(Collectors.joining(", ")) + ")";
    }

    /// One description component whose type selects a presentation template.
    ///
    /// @param type the size or release-date component type, such as `size`, `year`, or `early`
    /// @param arguments the ordered template arguments, copied into an immutable list
    @NotNullByDefault
    public record Detail(String type, @Unmodifiable List<String> arguments) {
        /// Validates the component type and takes an immutable snapshot of its arguments.
        public Detail {
            Objects.requireNonNull(type);
            arguments = List.copyOf(arguments);
        }

        /// Formats this component in English without depending on UI resources or the current locale.
        /// Unknown types or invalid arguments are rejected when the bundled CSV is loaded.
        public String formatEnglish() {
            String template = switch (type) {
                case "year" -> "%s";
                case "size" -> "%s-inch";
                case "early" -> "Early %s";
                case "mid" -> "Mid %s";
                case "late" -> "Late %s";
                case "november" -> "Nov %s";
                default -> throw new IllegalArgumentException("Unknown Mac model detail type: " + type);
            };
            int argumentCount = (template.length() - template.replace("%s", "").length()) / 2;
            if (arguments.size() != argumentCount)
                throw new IllegalArgumentException("Invalid argument count for Mac model detail: " + type);
            return template.formatted(arguments.toArray());
        }
    }

    /// Loads the bundled model table only when a model identifier is queried.
    @NotNullByDefault
    private static final class Holder {
        /// Immutable identifier-to-model mappings, empty if the bundled resource cannot be read.
        private static final @Unmodifiable Map<String, MacModel> MODELS = loadModels();
    }

    /// Returns the known model for an exact identifier, or `null` for missing or unknown identifiers.
    public static @Nullable MacModel find(@Nullable String identifier) {
        return identifier == null ? null : Holder.MODELS.get(identifier);
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
                        detail.formatEnglish();
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
