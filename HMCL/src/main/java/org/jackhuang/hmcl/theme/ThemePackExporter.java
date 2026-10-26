// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Writes theme-pack manifests and assets into zip-compatible files.
@NotNullByDefault
public final class ThemePackExporter {

    /// Recommended file extension for theme-pack files.
    public static final String FILE_EXTENSION = ".hmcl-theme";

    /// Manifest entry name used by every exported theme pack.
    public static final String MANIFEST_ENTRY = "manifest.json";

    /// Prevents instantiation.
    private ThemePackExporter() {
    }

    /// Exports a theme pack to a zip-compatible file.
    ///
    /// @param manifest   the manifest to write
    /// @param assets     asset files to copy into the zip
    /// @param outputFile the target theme-pack file
    /// @throws IOException              if a source file cannot be read or the zip cannot be written
    /// @throws IllegalArgumentException if an asset entry is duplicated or unsafe
    public static void export(
            ThemePackManifest manifest,
            @Unmodifiable List<ThemePackAsset> assets,
            Path outputFile) throws IOException {
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(assets);
        Objects.requireNonNull(outputFile);

        validateAssets(assets);

        Path output = outputFile.toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryFile = FileUtils.tmpSaveFile(output);
        try {
            Files.deleteIfExists(temporaryFile);
            try (Zipper zipper = new Zipper(temporaryFile)) {
                zipper.putTextFile(JsonUtils.GSON.toJson(manifest), MANIFEST_ENTRY);
                for (ThemePackAsset asset : assets) {
                    try (InputStream input = asset.source().openStream()) {
                        zipper.putStream(input, asset.entryName());
                    }
                }
            }
            Files.move(temporaryFile, output, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /// Validates all asset entries and source files before the zip is written.
    private static void validateAssets(List<ThemePackAsset> assets) throws IOException {
        Set<String> entries = new HashSet<>();
        entries.add(MANIFEST_ENTRY);

        for (ThemePackAsset asset : assets) {
            Objects.requireNonNull(asset);
            @Nullable Path sourceFile = asset.source().file();
            if (sourceFile != null && !Files.isRegularFile(sourceFile)) {
                throw new IOException("Theme-pack asset source is not a regular file: " + sourceFile);
            }
            try (InputStream ignored = asset.source().openStream()) {
                // Validate readability before writing the target zip.
            }
            if (!entries.add(asset.entryName())) {
                throw new IllegalArgumentException("Duplicate theme-pack zip entry: " + asset.entryName());
            }
        }
    }
}
