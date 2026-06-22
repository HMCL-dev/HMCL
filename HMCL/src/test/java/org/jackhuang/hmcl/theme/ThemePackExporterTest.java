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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for exporting theme-pack files.
@NotNullByDefault
public final class ThemePackExporterTest {

    /// Tests exporting a manifest and one asset into a readable theme-pack file.
    @Test
    public void testExportThemePackFile() throws IOException {
        Path tempDir = createTestDirectory("export");
        Path wallpaper = tempDir.resolve("wallpaper.txt");
        Files.writeString(wallpaper, "wallpaper", StandardCharsets.UTF_8);

        assertEquals(".hmcl-theme", ThemePackExporter.FILE_EXTENSION);

        Path output = tempDir.resolve("theme-pack" + ThemePackExporter.FILE_EXTENSION);
        ThemePackExporter.export(
                createManifest(),
                List.of(new ThemePackAsset(wallpaper, "assets/wallpapers/wallpaper.txt")),
                output);

        try (ZipFile zipFile = new ZipFile(output.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry manifestEntry = zipFile.getEntry(ThemePackExporter.MANIFEST_ENTRY);
            ZipEntry wallpaperEntry = zipFile.getEntry("assets/wallpapers/wallpaper.txt");
            assertNotNull(manifestEntry);
            assertNotNull(wallpaperEntry);

            String exportedManifest = readEntry(zipFile, manifestEntry);
            assertTrue(exportedManifest.contains(ThemePackManifest.CURRENT_SCHEMA.url()));
            assertTrue(exportedManifest.contains("\"theme\""));
            assertFalse(exportedManifest.contains("\"themes\""));
            ThemePackManifest parsed = ThemePackManifest.fromJson(exportedManifest);
            Theme theme = parsed.findTheme(null);
            assertNotNull(theme);
            ThemeBackgroundSettings background = theme.appearance().background();
            assertNotNull(background);
            assertEquals("user.current-theme", parsed.id());
            ThemeBackground.Image image = assertInstanceOf(ThemeBackground.Image.class, background.source());
            assertEquals("assets/wallpapers/wallpaper.txt", image.path());

            String exportedWallpaper = readEntry(zipFile, wallpaperEntry);
            assertEquals("wallpaper", exportedWallpaper);
        }
    }

    /// Tests rejecting unsafe asset entry names before writing a zip file.
    @Test
    public void testRejectUnsafeAssetEntryName() throws IOException {
        Path tempDir = createTestDirectory("unsafe-entry");
        Path wallpaper = tempDir.resolve("wallpaper.txt");

        assertThrows(IllegalArgumentException.class, () -> new ThemePackAsset(wallpaper, "../wallpaper.txt"));
        assertThrows(IllegalArgumentException.class, () -> new ThemePackAsset(wallpaper, "manifest.json"));
        assertThrows(IllegalArgumentException.class, () -> new ThemePackAsset(wallpaper, "assets/../wallpaper.txt"));
    }

    /// Tests rejecting duplicated asset entries.
    @Test
    public void testRejectDuplicateAssetEntries() throws IOException {
        Path tempDir = createTestDirectory("duplicate-entry");
        Path wallpaper = tempDir.resolve("wallpaper.txt");
        Files.writeString(wallpaper, "wallpaper", StandardCharsets.UTF_8);

        ThemePackAsset first = new ThemePackAsset(wallpaper, "assets/wallpapers/wallpaper.txt");
        ThemePackAsset second = new ThemePackAsset(wallpaper, "assets/wallpapers/wallpaper.txt");

        assertThrows(IllegalArgumentException.class,
                () -> ThemePackExporter.export(
                        createManifest(),
                        List.of(first, second),
                        tempDir.resolve("theme-pack" + ThemePackExporter.FILE_EXTENSION)));
    }

    /// Tests rejecting missing asset source files.
    @Test
    public void testRejectMissingAssetSource() throws IOException {
        Path tempDir = createTestDirectory("missing-source");
        ThemePackAsset asset = new ThemePackAsset(tempDir.resolve("missing.txt"), "assets/wallpapers/missing.txt");

        assertThrows(IOException.class,
                () -> ThemePackExporter.export(
                        createManifest(),
                        List.of(asset),
                        tempDir.resolve("theme-pack" + ThemePackExporter.FILE_EXTENSION)));
    }

    /// Creates a test directory under the build directory.
    ///
    /// @param name the directory name prefix
    /// @return the created directory
    private static Path createTestDirectory(String name) throws IOException {
        Path directory = Path.of("build", "tmp", "theme-pack-exporter-test", name + "-" + System.nanoTime())
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(directory);
        return directory;
    }

    /// Reads one zip entry as a UTF-8 string.
    ///
    /// @param zipFile the zip file
    /// @param entry the entry to read
    /// @return the entry content
    private static String readEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream input = zipFile.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /// Creates a minimal exportable manifest.
    private static ThemePackManifest createManifest() {
        return ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "user.current-theme",
                  "version": "1.0.0",
                  "name": "Current Theme",
                  "authors": [
                    {
                      "name": "User"
                    }
                  ],
                  "theme": {
                    "color": "#5C6BC0",
                    "colorStyle": "fidelity",
                    "contrast": "standard",
                    "background": {
                      "type": "image",
                      "path": "assets/wallpapers/wallpaper.txt"
                    }
                  }
                }
                """);
    }
}
