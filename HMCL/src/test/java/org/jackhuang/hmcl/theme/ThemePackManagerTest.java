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

import com.google.gson.JsonObject;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorStyle;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for applying and exporting launcher theme packs.
@NotNullByDefault
public final class ThemePackManagerTest {

    /// Tests applying an image-background theme pack to launcher settings.
    @Test
    public void testApplyImageThemePack() throws Exception {
        Path installedDirectory = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.ui")
                .resolve("1.0.0");
        deleteRecursively(installedDirectory.getParent());

        try (SettingsScope ignored = new SettingsScope()) {
            Path tempDir = createTestDirectory("apply-image");
            Path wallpaper = tempDir.resolve("wallpaper.png");
            Path thumbnail = tempDir.resolve("thumbnail.txt");
            writeSolidImage(wallpaper, 0xFF248C44);
            Files.writeString(thumbnail, "thumbnail", StandardCharsets.UTF_8);

            ThemePackManifest manifest = createImageManifest();
            Path themePackFile = tempDir.resolve("example" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(
                    manifest,
                    List.of(
                            new ThemePackAsset(wallpaper, "assets/wallpapers/wallpaper.png"),
                            new ThemePackAsset(thumbnail, "assets/thumbnails/thumbnail.txt")),
                    themePackFile);

            ThemePackManager.InstalledThemePack installedThemePack = ThemePackManager.install(themePackFile);
            assertEquals(installedDirectory, installedThemePack.directory());
            assertTrue(Files.isRegularFile(installedDirectory.resolve(ThemePackExporter.MANIFEST_ENTRY)));
            assertTrue(Files.isRegularFile(installedDirectory.resolve("assets/wallpapers/wallpaper.png")));
            assertEquals("thumbnail", Files.readString(
                    installedDirectory.resolve("assets/thumbnails/thumbnail.txt"),
                    StandardCharsets.UTF_8));

            Theme theme = installedThemePack.manifest().findTheme(null);
            assertNotNull(theme);

            ThemePackManager.apply(
                    installedThemePack.directory(),
                    installedThemePack.manifest(),
                    theme,
                    new ThemeResolveContext(Brightness.LIGHT, "light", "linux", "x86_64", "en"));

            LauncherSettings settings = SettingsManager.settings();
            assertEquals(ThemeColor.of("#248C44"), settings.customThemeColorProperty().get());
            assertEquals(ThemeColorType.BACKGROUND, settings.themeColorTypeProperty().get());
            assertEquals(ColorStyle.EXPRESSIVE, settings.themeColorStyleProperty().get());
            assertEquals("dark", settings.themeBrightnessProperty().get());
            assertEquals(new ThemeSelection("example.ui", "1.0.0", null), settings.themeProperty().get());
            JsonObject themeJson = LauncherSettings.SETTINGS_GSON.toJsonTree(settings)
                    .getAsJsonObject()
                    .getAsJsonObject("theme");
            assertEquals("example.ui", themeJson.get("packId").getAsString());
            assertEquals("1.0.0", themeJson.get("version").getAsString());
            assertFalse(themeJson.has("themeId"));
            assertTrue(settings.titleTransparentProperty().get());
            assertEquals(BackgroundType.CUSTOM, settings.backgroundTypeProperty().get());
            assertEquals(0.75, settings.backgroundOpacityProperty().get());
            assertNull(settings.backgroundImageUrlProperty().get());
            assertNull(settings.backgroundPaintProperty().get());

            ThemePackResourceURL backgroundResource = ThemePackResourceURL.parse(settings.backgroundImageProperty().get());
            assertNotNull(backgroundResource);
            assertEquals("example.ui", backgroundResource.packId());
            assertEquals("1.0.0", backgroundResource.version());
            assertEquals("assets/wallpapers/wallpaper.png", backgroundResource.entryName());
            assertEquals(installedDirectory.resolve("assets/wallpapers/wallpaper.png"), backgroundResource.resolve());
        } finally {
            deleteRecursively(installedDirectory.getParent());
        }
    }

    /// Tests exporting the current launcher appearance as a theme-pack file.
    @Test
    public void testExportCurrentThemePack() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            ThemeColor themeColor = Objects.requireNonNull(ThemeColor.of("#663399"));
            settings.customThemeColorProperty().set(themeColor);
            settings.themeColorTypeProperty().set(ThemeColorType.BACKGROUND);
            settings.themeColorStyleProperty().set(ColorStyle.MONOCHROME);
            settings.themeBrightnessProperty().set("dark");
            settings.titleTransparentProperty().set(true);
            settings.backgroundTypeProperty().set(BackgroundType.CLASSIC);
            settings.backgroundOpacityProperty().set(0.5);

            Path tempDir = createTestDirectory("export-current");
            Path output = tempDir.resolve("current" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(output, "com.example.hmcl-theme-pack.test", "Current Pack", "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            ThemeAppearance appearance = theme.appearance();
            ThemeBackground background = appearance.background();
            assertNotNull(background);

            assertEquals("com.example.hmcl-theme-pack.test", manifest.id());
            assertEquals("Current Pack", manifest.name());
            assertEquals(List.of("Test Author"), manifest.authors());
            assertNull(theme.id());
            assertNull(theme.name());
            assertEquals(ThemeColorSource.wallpaper(themeColor), appearance.color());
            assertEquals(ColorStyle.MONOCHROME, appearance.colorStyle());
            assertEquals(ThemeBrightness.DARK, appearance.brightness());
            assertEquals(true, appearance.titleTransparent());
            assertEquals(ThemeBackground.Type.CLASSIC, background.effectiveType());
            assertEquals(0.5, background.opacity());
        }
    }

    /// Tests theme-pack resource URL serialization and parsing.
    @Test
    public void testThemePackResourceURLRoundTrip() {
        ThemePackResourceURL resource = new ThemePackResourceURL(
                "example.pack",
                "1.0.0",
                "assets/wall papers/a+b.png");

        String serialized = resource.toString();

        assertEquals("hmcl://theme-pack/example.pack/1.0.0/assets/wall%20papers/a+b.png", serialized);
        assertEquals(resource, ThemePackResourceURL.parse(serialized));
        assertNull(ThemePackResourceURL.parse("/tmp/background.png"));
        assertNull(ThemePackResourceURL.parse("C:\\background.png"));
    }

    /// Creates a test directory under the build directory.
    ///
    /// @param name the directory name prefix
    /// @return the created directory
    private static Path createTestDirectory(String name) throws IOException {
        Path directory = Path.of("build", "tmp", "theme-pack-manager-test", name + "-" + System.nanoTime())
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(directory);
        return directory;
    }

    /// Writes a small solid-color PNG image.
    ///
    /// @param output the image file to write
    /// @param argb the ARGB color used for every pixel
    private static void writeSolidImage(Path output, int argb) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
        ImageIO.write(image, "png", output.toFile());
    }

    /// Deletes a path tree if it exists.
    ///
    /// @param path the path to delete
    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    /// Creates a manifest with an image background.
    ///
    /// @return the parsed manifest
    private static ThemePackManifest createImageManifest() {
        return ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.ui",
                  "version": "1.0.0",
                  "name": "Example UI",
                  "authors": ["Example"],
                  "theme": {
                    "name": "Forest",
                    "thumbnail": "assets/thumbnails/thumbnail.txt",
                    "color": {
                      "source": "wallpaper",
                      "fallback": "#336699"
                    },
                    "brightness": "dark",
                    "colorStyle": "expressive",
                    "titleTransparent": true,
                    "background": {
                      "type": "image",
                      "path": "assets/wallpapers/wallpaper.png",
                      "opacity": 0.75
                    }
                  }
                }
                """);
    }

    /// Temporarily replaces the launcher settings singleton used by theme-pack code.
    private static final class SettingsScope implements AutoCloseable {
        /// The reflected launcher settings field.
        private final Field launcherSettingsField;

        /// The settings instance that was active before the test.
        private final @Nullable LauncherSettings previousLauncherSettings;

        /// Creates a scope with fresh launcher settings.
        private SettingsScope() throws ReflectiveOperationException {
            launcherSettingsField = SettingsManager.class.getDeclaredField("launcherSettings");
            launcherSettingsField.setAccessible(true);
            previousLauncherSettings = (LauncherSettings) launcherSettingsField.get(null);
            launcherSettingsField.set(null, new LauncherSettings());
        }

        /// Restores the previous launcher settings.
        @Override
        public void close() throws ReflectiveOperationException {
            launcherSettingsField.set(null, previousLauncherSettings);
        }
    }
}
