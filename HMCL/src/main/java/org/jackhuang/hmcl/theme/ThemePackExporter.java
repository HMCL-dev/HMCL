/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
