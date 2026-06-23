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
import javafx.scene.paint.Color;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorRole;
import org.glavo.monetfx.ColorStyle;
import org.jackhuang.hmcl.setting.BackgroundOpacityType;
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
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for applying and exporting launcher theme packs.
@NotNullByDefault
public final class ThemePackManagerTest {

    /// Tests applying an image-background theme pack to launcher settings.
    @Test
    public void testApplyImageThemePack() throws Exception {
        Path installedFile = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.ui" + ThemePackExporter.FILE_EXTENSION);
        Path installedCacheDirectory = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve(".cache")
                .resolve("example.ui");
        deleteRecursively(installedFile);
        deleteRecursively(installedCacheDirectory);

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
            assertEquals(installedFile.toAbsolutePath().normalize(), installedThemePack.file());
            assertTrue(Files.isRegularFile(installedFile));
            assertFalse(Files.isDirectory(installedFile));
            Path cachedWallpaper = ThemePackManager.resolveInstalledAsset(
                    installedFile,
                    "assets/wallpapers/wallpaper.png");
            assertTrue(Files.isRegularFile(cachedWallpaper));
            assertEquals("thumbnail", Files.readString(
                    ThemePackManager.resolveInstalledAsset(installedFile, "assets/thumbnails/thumbnail.txt"),
                    StandardCharsets.UTF_8));

            Theme theme = installedThemePack.manifest().findTheme(null);
            assertNotNull(theme);

            ThemePackManager.apply(
                    installedThemePack.file(),
                    installedThemePack.manifest(),
                    theme,
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));

            LauncherSettings settings = SettingsManager.settings();
            assertEquals(ThemeColor.DEFAULT, settings.customThemeColorProperty().get());
            assertNull(settings.themeColorTypeProperty().get());
            assertNull(settings.themeColorStyleProperty().get());
            assertNull(settings.themeBrightnessProperty().get());
            assertEquals(new ThemeReference("example.ui", null), settings.themeProperty().get());
            JsonObject settingsJson = LauncherSettings.SETTINGS_GSON.toJsonTree(settings).getAsJsonObject();
            JsonObject themeJson = settingsJson.getAsJsonObject("theme");
            assertEquals("example.ui", themeJson.get("packId").getAsString());
            assertFalse(themeJson.has("themeId"));
            assertNull(settings.titleTransparentProperty().get());
            assertEquals(BackgroundType.DEFAULT, settings.backgroundTypeProperty().get());
            assertEquals(BackgroundOpacityType.DEFAULT, settings.backgroundOpacityTypeProperty().get());
            assertNull(settings.backgroundFallbackTypeProperty().get());
            assertEquals(Color.WHITE, settings.backgroundFallbackPaintProperty().get());
            assertNull(settings.backgroundLoadPolicyProperty().get());
            assertNull(settings.customBackgroundImagePathProperty().get());
            assertNull(settings.networkBackgroundImageUrlProperty().get());
            assertNull(settings.customBackgroundPaintProperty().get());
            assertFalse(settingsJson.has("themeBrightness"));
            assertFalse(settingsJson.has("themeColorType"));
            assertFalse(settingsJson.has("themeColorStyle"));
            assertFalse(settingsJson.has("titleTransparent"));
            assertFalse(settingsJson.has("backgroundOpacityType"));
            assertFalse(settingsJson.has("backgroundOpacity"));
            assertFalse(settingsJson.has("backgroundFallbackType"));
            assertFalse(settingsJson.has("backgroundLoadPolicy"));

            ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "linux", "en");
            assertEquals(ColorStyle.EXPRESSIVE, ThemePackManager.resolveCurrentThemeColorStyle(
                    context,
                    ResolvedTheme.DEFAULT.colorStyle()));
            assertEquals(Brightness.DARK, ThemePackManager.resolveCurrentThemeBrightness(context));
            assertTrue(ThemePackManager.resolveCurrentTitleBarTransparent(context, false));
            assertEquals(
                    BackgroundLoadPolicy.WAIT_FOR_BACKGROUND,
                    ThemePackManager.resolveCurrentBackgroundLoadPolicy(context));
            ThemePackManager.ResolvedBackground resolvedFallback = ThemePackManager.resolveCurrentBackgroundFallback(context);
            assertEquals(BackgroundType.PAINT, resolvedFallback.type());
            assertEquals(Color.web("#112233"), resolvedFallback.paint());
            assertEquals(0.75, resolvedFallback.opacity());

            ThemePackManager.ResolvedBackground resolvedBackground = ThemePackManager.resolveCurrentBackground(
                    context);
            assertEquals(BackgroundType.CUSTOM, resolvedBackground.type());
            assertEquals(cachedWallpaper, resolvedBackground.imagePath());
            assertEquals(0.75, resolvedBackground.opacity());

            settings.backgroundOpacityTypeProperty().set(BackgroundOpacityType.CUSTOM);
            settings.backgroundOpacityProperty().set(0.35);
            ThemePackManager.apply(
                    installedThemePack.file(),
                    installedThemePack.manifest(),
                    theme,
                    context);
            assertEquals(BackgroundOpacityType.DEFAULT, settings.backgroundOpacityTypeProperty().get());
            assertEquals(0.35, settings.backgroundOpacityProperty().get());

            settings.customThemeColorProperty().set(Objects.requireNonNull(ThemeColor.of("#663399")));
            assertEquals(ThemeColor.of("#248C44"), Themes.resolveCurrentThemeColor());

            Path exportedThemePackFile = tempDir.resolve("exported" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(exportedThemePackFile, "com.example.exported", "Exported", "Test Author");
            Theme exportedTheme = ThemePackManager.load(exportedThemePackFile).manifest().findTheme(null);
            assertNotNull(exportedTheme);
            assertEquals(ThemeColorSource.wallpaper(), exportedTheme.appearance().color());
        } finally {
            deleteRecursively(installedFile);
            deleteRecursively(installedCacheDirectory);
        }
    }

    /// Tests installing a package replaces an existing version with the same package ID.
    @Test
    public void testInstallReplacesSamePackageId() throws Exception {
        Path installedFile = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.replace" + ThemePackExporter.FILE_EXTENSION);
        Path installedCacheDirectory = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve(".cache")
                .resolve("example.replace");
        deleteRecursively(installedFile);
        deleteRecursively(installedCacheDirectory);

        try (SettingsScope ignored = new SettingsScope()) {
            Path tempDir = createTestDirectory("replace-package");
            Path firstThemePackFile = tempDir.resolve("first" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(
                    createMinimalManifest("example.replace", "1.0.0", "Replace Example"),
                    List.of(),
                    firstThemePackFile);

            Path secondThemePackFile = tempDir.resolve("second" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(
                    createMinimalManifest("example.replace", "2.0.0", "Replace Example"),
                    List.of(),
                    secondThemePackFile);

            ThemePackManager.InstalledThemePack firstInstalledThemePack = ThemePackManager.install(firstThemePackFile);
            assertEquals(installedFile.toAbsolutePath().normalize(), firstInstalledThemePack.file());
            assertEquals("1.0.0", firstInstalledThemePack.manifest().version());

            Files.writeString(installedFile, "stale", StandardCharsets.UTF_8);

            ThemePackManager.InstalledThemePack secondInstalledThemePack = ThemePackManager.install(secondThemePackFile);
            assertEquals(installedFile.toAbsolutePath().normalize(), secondInstalledThemePack.file());
            assertEquals("2.0.0", secondInstalledThemePack.manifest().version());
            assertEquals("2.0.0", ThemePackManager.loadInstalled(installedFile).manifest().version());
            assertFalse(Files.isDirectory(installedFile));
        } finally {
            deleteRecursively(installedFile);
            deleteRecursively(installedCacheDirectory);
        }
    }

    /// Tests that one broken installed package does not prevent listing other packages.
    @Test
    public void testListInstalledSkipsBrokenThemePacks() throws Exception {
        Path validFile = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.list-valid" + ThemePackExporter.FILE_EXTENSION);
        Path brokenFile = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.list-broken" + ThemePackExporter.FILE_EXTENSION);
        Path validCacheDirectory = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve(".cache")
                .resolve("example.list-valid");
        deleteRecursively(validFile);
        deleteRecursively(brokenFile);
        deleteRecursively(validCacheDirectory);

        try {
            Path tempDir = createTestDirectory("list-installed");
            Path themePackFile = tempDir.resolve("valid" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(
                    createMinimalManifest("example.list-valid", "1.0.0", "List Valid"),
                    List.of(),
                    themePackFile);
            ThemePackManager.install(themePackFile);

            Files.createDirectories(ThemePackManager.THEME_PACKS_DIRECTORY);
            Files.writeString(brokenFile, "{", StandardCharsets.UTF_8);

            List<ThemePackManager.InstalledThemePack> installedThemePacks = ThemePackManager.listInstalled();
            assertFalse(installedThemePacks.isEmpty());
            ThemePackManager.InstalledThemePack builtInThemePack = installedThemePacks.get(0);
            assertTrue(builtInThemePack.builtin());
            assertEquals(
                    ThemePackManager.BUILTIN_DEFAULT_THEME_REFERENCE.packId(),
                    builtInThemePack.manifest().id());
            assertThrows(IOException.class, () -> ThemePackManager.uninstall(builtInThemePack));
            assertTrue(installedThemePacks.stream()
                    .anyMatch(themePack -> "example.list-valid".equals(themePack.manifest().id())));
        } finally {
            deleteRecursively(validFile);
            deleteRecursively(brokenFile);
            deleteRecursively(validCacheDirectory);
        }
    }

    /// Tests listing and resolving assets from an unpacked theme-pack directory.
    @Test
    public void testListInstalledLoadsUnpackedThemePackDirectory() throws Exception {
        Path installedDirectory = ThemePackManager.THEME_PACKS_DIRECTORY.resolve("folder-dev");
        deleteRecursively(installedDirectory);

        try {
            Path wallpaper = installedDirectory.resolve("assets/wallpapers/wallpaper.png");
            Files.createDirectories(Objects.requireNonNull(wallpaper.getParent()));
            writeSolidImage(wallpaper, 0xFF336699);
            Files.writeString(installedDirectory.resolve(ThemePackExporter.MANIFEST_ENTRY), """
                    {
                      "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                      "id": "example.folder-dev",
                      "version": "1.0.0",
                      "name": "Folder Dev",
                      "authors": [
                        {
                          "name": "Example"
                        }
                      ],
                      "theme": {
                        "background": {
                          "type": "image",
                          "path": "assets/wallpapers/wallpaper.png"
                        }
                      }
                    }
                    """, StandardCharsets.UTF_8);

            List<ThemePackManager.InstalledThemePack> installedThemePacks = ThemePackManager.listInstalled();
            assertTrue(installedThemePacks.stream()
                    .anyMatch(themePack -> "example.folder-dev".equals(themePack.manifest().id())));

            ThemePackManager.InstalledThemePack installedThemePack = ThemePackManager.findInstalled(
                    new ThemeReference("example.folder-dev", null));
            assertNotNull(installedThemePack);
            assertEquals(installedDirectory.toAbsolutePath().normalize(), installedThemePack.file());
            assertEquals(
                    wallpaper.toAbsolutePath().normalize(),
                    ThemePackManager.resolveInstalledAsset(
                            installedThemePack.file(),
                            "assets/wallpapers/wallpaper.png"));
        } finally {
            deleteRecursively(installedDirectory);
        }
    }

    /// Tests the built-in default and classic theme packs declare the expected themes.
    @Test
    public void testBuiltInThemePacksDeclareExpectedThemes() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            ThemePackManager.InstalledThemePack defaultThemePack = ThemePackManager.builtinThemePack();
            assertEquals("hmcl.default", defaultThemePack.manifest().id());
            assertEquals(1, defaultThemePack.manifest().themes().size());

            Theme defaultTheme = defaultThemePack.manifest().findTheme(null);
            assertNotNull(defaultTheme);
            assertNull(defaultTheme.appearance().brightness());
            ThemeBackgroundSettings defaultBackground = defaultTheme.appearance().background();
            assertNotNull(defaultBackground);
            ThemeBackground.Builtin defaultBuiltinBackground =
                    assertInstanceOf(ThemeBackground.Builtin.class, defaultBackground.source());
            assertEquals(BackgroundType.BUILTIN_WALLPAPER_2021_08_26_ID, defaultBuiltinBackground.id());

            LauncherSettings settings = SettingsManager.settings();
            ThemePackManager.apply(defaultThemePack, defaultTheme);
            assertEquals(ThemePackManager.BUILTIN_DEFAULT_THEME_REFERENCE, settings.themeProperty().get());

            settings.themeBrightnessProperty().set("dark");
            assertEquals("dark", settings.themeBrightnessProperty().get());

            ThemePackManager.InstalledThemePack classicThemePack = ThemePackManager.findInstalled(
                    new ThemeReference("hmcl.classic", "2016-02-25"));
            assertNotNull(classicThemePack);
            assertTrue(classicThemePack.builtin());
            assertEquals(3, classicThemePack.manifest().themes().size());
            assertNotNull(classicThemePack.manifest().findTheme("2021-08-26"));
            assertNotNull(classicThemePack.manifest().findTheme("2015-06-22"));

            Theme classicTheme = classicThemePack.manifest().findTheme("2016-02-25");
            assertNotNull(classicTheme);
            ThemeBackgroundSettings classicBackground = classicTheme.appearance().background();
            assertNotNull(classicBackground);
            ThemeBackground.Builtin classicBuiltinBackground =
                    assertInstanceOf(ThemeBackground.Builtin.class, classicBackground.source());
            assertEquals(BackgroundType.BUILTIN_WALLPAPER_2016_02_25_ID, classicBuiltinBackground.id());

            ThemeReference classicReference = new ThemeReference(classicThemePack.manifest().id(), "2016-02-25");
            ThemePackManager.ResolvedBackground resolvedBackground = ThemePackManager.resolveThemeBackground(
                    classicReference,
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));
            assertNotNull(resolvedBackground);
            assertEquals(BackgroundType.BUILTIN, resolvedBackground.type());
            assertEquals(BackgroundType.BUILTIN_WALLPAPER_2016_02_25_ID, resolvedBackground.builtinBackgroundId());

            ThemePackManager.apply(classicThemePack, classicTheme);
            assertEquals(classicReference, settings.themeProperty().get());
            assertNull(settings.themeBrightnessProperty().get());
            assertNull(settings.themeColorStyleProperty().get());
            assertEquals(BackgroundType.DEFAULT, settings.backgroundTypeProperty().get());
            ThemePackManager.ResolvedBackground selectedClassicBackground = ThemePackManager.resolveCurrentBackground(
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));
            assertEquals(BackgroundType.BUILTIN, selectedClassicBackground.type());
            assertEquals(BackgroundType.BUILTIN_WALLPAPER_2016_02_25_ID, selectedClassicBackground.builtinBackgroundId());
        }
    }

    /// Tests refreshing the selected theme when the brightness condition context changes.
    @Test
    public void testRefreshCurrentThemeForBrightnessCondition() throws Exception {
        Path installedFile = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.refresh" + ThemePackExporter.FILE_EXTENSION);
        Path installedCacheDirectory = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve(".cache")
                .resolve("example.refresh");
        deleteRecursively(installedFile);
        deleteRecursively(installedCacheDirectory);

        try (SettingsScope ignored = new SettingsScope()) {
            Path tempDir = createTestDirectory("refresh-brightness");
            Path themePackFile = tempDir.resolve("refresh" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(createBrightnessConditionManifest(), List.of(), themePackFile);

            ThemePackManager.InstalledThemePack installedThemePack = ThemePackManager.install(themePackFile);
            Theme theme = installedThemePack.manifest().findTheme(null);
            assertNotNull(theme);

            ThemePackManager.apply(
                    installedThemePack.file(),
                    installedThemePack.manifest(),
                    theme,
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));

            LauncherSettings settings = SettingsManager.settings();
            ThemeReference reference = new ThemeReference("example.refresh", null);
            assertEquals(reference, settings.themeProperty().get());
            assertNull(settings.themeColorStyleProperty().get());
            assertNull(settings.titleTransparentProperty().get());
            assertEquals(BackgroundOpacityType.DEFAULT, settings.backgroundOpacityTypeProperty().get());
            assertEquals(
                    ColorStyle.NEUTRAL,
                    ThemePackManager.resolveCurrentThemeColorStyle(
                            new ThemeResolveContext(Brightness.LIGHT, "linux", "en"),
                            ResolvedTheme.DEFAULT.colorStyle()));
            assertFalse(ThemePackManager.resolveCurrentTitleBarTransparent(
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"),
                    false));
            assertEquals(
                    0.5,
                    ThemePackManager.resolveCurrentBackground(
                            new ThemeResolveContext(Brightness.LIGHT, "linux", "en")).opacity());

            settings.themeBrightnessProperty().set("dark");

            assertEquals(reference, settings.themeProperty().get());
            assertNull(settings.themeColorStyleProperty().get());
            assertEquals(
                    ColorStyle.EXPRESSIVE,
                    ThemePackManager.resolveCurrentThemeColorStyle(
                            new ThemeResolveContext(Brightness.DARK, "linux", "en"),
                            ResolvedTheme.DEFAULT.colorStyle()));
            assertTrue(ThemePackManager.resolveCurrentTitleBarTransparent(
                    new ThemeResolveContext(Brightness.DARK, "linux", "en"),
                    false));
            assertEquals(BackgroundType.DEFAULT, settings.backgroundTypeProperty().get());
            assertEquals(BackgroundOpacityType.DEFAULT, settings.backgroundOpacityTypeProperty().get());

            ThemePackManager.ResolvedBackground background = ThemePackManager.resolveCurrentBackground(
                    new ThemeResolveContext(Brightness.DARK, "linux", "en"));
            assertEquals(BackgroundType.PAINT, background.type());
            assertEquals(Color.BLACK, background.paint());
        } finally {
            deleteRecursively(installedFile);
            deleteRecursively(installedCacheDirectory);
        }
    }

    /// Tests refreshing the selected theme preserves launcher appearance overrides.
    @Test
    public void testRefreshCurrentThemePreservesAppearanceOverrides() throws Exception {
        Path installedFile = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.refresh" + ThemePackExporter.FILE_EXTENSION);
        Path installedCacheDirectory = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve(".cache")
                .resolve("example.refresh");
        deleteRecursively(installedFile);
        deleteRecursively(installedCacheDirectory);

        try (SettingsScope ignored = new SettingsScope()) {
            Path tempDir = createTestDirectory("refresh-overrides");
            Path themePackFile = tempDir.resolve("refresh" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(createBrightnessConditionManifest(), List.of(), themePackFile);

            ThemePackManager.InstalledThemePack installedThemePack = ThemePackManager.install(themePackFile);
            Theme theme = installedThemePack.manifest().findTheme(null);
            assertNotNull(theme);

            ThemePackManager.apply(
                    installedThemePack.file(),
                    installedThemePack.manifest(),
                    theme,
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));

            LauncherSettings settings = SettingsManager.settings();
            ThemeReference reference = new ThemeReference("example.refresh", null);
            settings.themeColorStyleProperty().set(ColorStyle.MONOCHROME);
            settings.themeBrightnessProperty().set("dark");

            assertEquals(reference, settings.themeProperty().get());
            assertEquals(ColorStyle.MONOCHROME, settings.themeColorStyleProperty().get());
            assertTrue(ThemePackManager.resolveCurrentTitleBarTransparent(
                    new ThemeResolveContext(Brightness.DARK, "linux", "en"),
                    false));
            assertEquals(
                    0.25,
                    ThemePackManager.resolveCurrentBackground(
                            new ThemeResolveContext(Brightness.DARK, "linux", "en")).opacity());
        } finally {
            deleteRecursively(installedFile);
            deleteRecursively(installedCacheDirectory);
        }
    }

    /// Tests that an explicit default background in an override resets the base background source.
    @Test
    public void testDefaultBackgroundOverrideResetsBaseBackground() throws Exception {
        Path installedFile = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve("example.default-reset" + ThemePackExporter.FILE_EXTENSION);
        Path installedCacheDirectory = ThemePackManager.THEME_PACKS_DIRECTORY
                .resolve(".cache")
                .resolve("example.default-reset");
        deleteRecursively(installedFile);
        deleteRecursively(installedCacheDirectory);

        try (SettingsScope ignored = new SettingsScope()) {
            ThemePackManifest manifest = ThemePackManifest.fromJson("""
                    {
                      "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                      "id": "example.default-reset",
                      "version": "1.0.0",
                      "name": "Default Reset",
                      "theme": {
                        "background": {
                          "type": "paint",
                          "paint": "#112233"
                        },
                        "overrides": [
                          {
                            "condition": {
                              "brightness": "dark"
                            },
                            "background": {
                              "type": "default"
                            }
                          }
                        ]
                      }
                    }
                    """);
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            Path tempDir = createTestDirectory("default-background-reset");
            Path themePackFile = tempDir.resolve("default-reset" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(manifest, List.of(), themePackFile);
            ThemePackManager.install(themePackFile);

            ThemeReference reference = new ThemeReference("example.default-reset", null);
            ThemePackManager.ResolvedBackground lightBackground = ThemePackManager.resolveThemeBackground(
                    reference,
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));
            assertNotNull(lightBackground);
            assertEquals(BackgroundType.PAINT, lightBackground.type());
            assertEquals(Color.web("#112233"), lightBackground.paint());

            ThemePackManager.ResolvedBackground darkBackground = ThemePackManager.resolveThemeBackground(
                    reference,
                    new ThemeResolveContext(Brightness.DARK, "linux", "en"));
            assertNotNull(darkBackground);
            assertEquals(BackgroundType.DEFAULT, darkBackground.type());
        } finally {
            deleteRecursively(installedFile);
            deleteRecursively(installedCacheDirectory);
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
            settings.backgroundTypeProperty().set(BackgroundType.DEFAULT);
            settings.backgroundOpacityTypeProperty().set(BackgroundOpacityType.CUSTOM);
            settings.backgroundOpacityProperty().set(0.5);
            settings.backgroundFallbackTypeProperty().set(BackgroundType.PAINT);
            settings.backgroundFallbackPaintProperty().set(Color.web("#112233"));
            settings.backgroundLoadPolicyProperty().set(BackgroundLoadPolicy.WAIT_FOR_BACKGROUND);

            Path tempDir = createTestDirectory("export-current");
            Path output = tempDir.resolve("current" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(output, "com.example.hmcl-theme-pack.test", "Current Pack", "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            ThemeAppearance appearance = theme.appearance();
            ThemeBackgroundSettings background = appearance.background();
            assertNotNull(background);

            assertEquals("com.example.hmcl-theme-pack.test", manifest.id());
            assertEquals("Current Pack", manifest.displayName());
            assertEquals("Test Author", manifest.authors().get(0).displayName());
            assertNull(theme.id());
            assertNull(theme.name());
            assertEquals(ThemeColorSource.wallpaper(), appearance.color());
            assertEquals(Brightness.DARK, appearance.brightness());
            assertEquals(ColorStyle.MONOCHROME, appearance.colorStyle());
            assertNotNull(appearance.titleBar());
            assertEquals(true, appearance.titleBar().transparent());
            assertInstanceOf(ThemeBackground.Builtin.class, background.source());
            assertEquals(0.5, background.opacity());
            assertNull(background.fallback());
            assertNull(background.loadPolicy());
        }
    }

    /// Tests exporting the theme-color background fallback option for a network background.
    @Test
    public void testExportCurrentThemeColorBackgroundFallback() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.backgroundTypeProperty().set(BackgroundType.NETWORK);
            settings.networkBackgroundImageUrlProperty().set("https://example.com/wallpaper.png");
            settings.backgroundFallbackTypeProperty().set(BackgroundType.THEME_COLOR);

            Path tempDir = createTestDirectory("export-theme-color-fallback");
            Path output = tempDir.resolve("theme-color-fallback" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(output, "com.example.theme-color-fallback", "Theme Color Fallback", "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            ThemeBackgroundSettings background = theme.appearance().background();
            assertNotNull(background);
            assertInstanceOf(ThemeBackground.ThemeColor.class, background.fallback());
            assertEquals(BackgroundLoadPolicy.WAIT_FOR_BACKGROUND, background.loadPolicy());
        }
    }

    /// Tests applying the theme-color background fallback option.
    @Test
    public void testApplyThemeColorBackgroundFallback() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            ThemePackManifest manifest = ThemePackManifest.fromJson("""
                    {
                      "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                      "id": "example.theme-color-fallback",
                      "version": "1.0.0",
                      "name": "Theme Color Fallback",
                      "theme": {
                        "background": {
                          "type": "builtin",
                          "id": "2021-08-26",
                          "fallback": {
                            "type": "theme_color"
                          }
                        }
                      }
                    }
                    """);
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            Path tempDir = createTestDirectory("apply-theme-color-fallback");
            Path themePackFile = tempDir.resolve("theme-color-fallback" + ThemePackExporter.FILE_EXTENSION);
            ThemePackExporter.export(manifest, List.of(), themePackFile);
            ThemePackManager.InstalledThemePack installedThemePack = ThemePackManager.install(themePackFile);

            ThemePackManager.apply(installedThemePack, theme);

            assertNull(SettingsManager.settings().backgroundFallbackTypeProperty().get());
            assertEquals(
                    BackgroundType.THEME_COLOR,
                    ThemePackManager.resolveCurrentBackgroundFallback(
                            new ThemeResolveContext(Brightness.LIGHT, "linux", "en")).type());
        }
    }

    /// Tests exporting a network background preserves the URL image cache policy.
    @Test
    public void testExportCurrentNetworkBackgroundCachePolicy() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.backgroundTypeProperty().set(BackgroundType.NETWORK);
            settings.networkBackgroundImageUrlProperty().set("https://example.com/wallpaper.png");
            settings.networkBackgroundImageCachePolicyProperty().set(NetworkBackgroundImageCachePolicy.DISABLED);

            Path tempDir = createTestDirectory("export-network-background-cache");
            Path output = tempDir.resolve("network-background-cache" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(
                    output,
                    "com.example.network-background-cache",
                    "Network Background Cache",
                    "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);
            ThemeBackgroundSettings background = theme.appearance().background();
            assertNotNull(background);

            ThemeBackground.Network network = assertInstanceOf(ThemeBackground.Network.class, background.source());
            assertEquals("https://example.com/wallpaper.png", network.url());
            assertEquals(NetworkBackgroundImageCachePolicy.DISABLED, network.cache());
        }
    }

    /// Tests exporting a network background with the default URL image cache policy omits the cache field.
    @Test
    public void testExportCurrentNetworkBackgroundDefaultCachePolicyOmitsField() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.backgroundTypeProperty().set(BackgroundType.NETWORK);
            settings.networkBackgroundImageUrlProperty().set("https://example.com/wallpaper.png");

            Path tempDir = createTestDirectory("export-network-background-default-cache");
            Path output = tempDir.resolve("network-background-default-cache" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(
                    output,
                    "com.example.network-background-default-cache",
                    "Network Background Default Cache",
                    "Test Author");

            try (ZipFile zipFile = new ZipFile(output.toFile(), StandardCharsets.UTF_8)) {
                ZipEntry manifestEntry = zipFile.getEntry(ThemePackExporter.MANIFEST_ENTRY);
                assertNotNull(manifestEntry);
                try (InputStream input = zipFile.getInputStream(manifestEntry)) {
                    String manifestJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    assertFalse(manifestJson.contains("\"cache\""));
                }
            }

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);
            ThemeBackgroundSettings background = theme.appearance().background();
            assertNotNull(background);

            ThemeBackground.Network network = assertInstanceOf(ThemeBackground.Network.class, background.source());
            assertEquals("https://example.com/wallpaper.png", network.url());
            assertNull(network.cache());
        }
    }

    /// Tests exporting the default theme color uses the selected theme seed.
    @Test
    public void testExportDefaultThemeColorUsesSelectedThemeSeed() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.customThemeColorProperty().set(Objects.requireNonNull(ThemeColor.of("#663399")));
            settings.themeColorTypeProperty().set(null);

            Path tempDir = createTestDirectory("export-default-theme-color");
            Path output = tempDir.resolve("default-theme-color" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(output, "com.example.default-theme-color", "Default Theme Color", "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);
            assertEquals(
                    ThemeColorSource.custom(Objects.requireNonNull(ThemeColor.of("#5C6BC0"))),
                    theme.appearance().color());
        }
    }

    /// Tests exporting the theme-color background option with wallpaper theme colors uses the default seed.
    @Test
    public void testExportCurrentThemeColorBackgroundUsesDefaultThemeColor() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.customThemeColorProperty().set(ThemeColor.of("#663399"));
            settings.themeColorTypeProperty().set(ThemeColorType.BACKGROUND);
            settings.backgroundTypeProperty().set(BackgroundType.THEME_COLOR);
            settings.backgroundOpacityTypeProperty().set(BackgroundOpacityType.CUSTOM);
            settings.backgroundOpacityProperty().set(1.0);

            Path tempDir = createTestDirectory("export-theme-color-background");
            Path output = tempDir.resolve("theme-color-background" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(output, "com.example.theme-color-background", "Theme Color Background", "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            ThemeAppearance appearance = theme.appearance();
            assertEquals(ThemeColorSource.custom(ThemeColor.DEFAULT), appearance.color());
            ThemeBackgroundSettings background = appearance.background();
            assertNotNull(background);
            assertInstanceOf(ThemeBackground.Paint.class, background.source());
            assertNull(background.fallback());
            assertNull(background.loadPolicy());
        }
    }

    /// Tests exporting a local background file omits loading controls.
    @Test
    public void testExportCurrentLocalBackgroundOmitsLoadingControls() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            Path tempDir = createTestDirectory("export-local-background");
            Path wallpaper = tempDir.resolve("wallpaper.png");
            writeSolidImage(wallpaper, 0xFF336699);

            LauncherSettings settings = SettingsManager.settings();
            settings.backgroundTypeProperty().set(BackgroundType.CUSTOM);
            settings.customBackgroundImagePathProperty().set(wallpaper.toString());
            settings.backgroundFallbackTypeProperty().set(BackgroundType.PAINT);
            settings.backgroundLoadPolicyProperty().set(BackgroundLoadPolicy.WAIT_FOR_BACKGROUND);

            Path output = tempDir.resolve("local-background" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(output, "com.example.local-background", "Local Background", "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            ThemeBackgroundSettings background = theme.appearance().background();
            assertNotNull(background);
            assertInstanceOf(ThemeBackground.Image.class, background.source());
            assertNull(background.fallback());
            assertNull(background.loadPolicy());
        }
    }

    /// Tests exporting the selected built-in launcher wallpaper ID.
    @Test
    public void testExportCurrentThemePackWritesSelectedBuiltinBackground() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.backgroundTypeProperty().set(BackgroundType.BUILTIN);
            settings.builtinBackgroundIdProperty().set(BackgroundType.BUILTIN_WALLPAPER_2016_02_25_ID);

            Path tempDir = createTestDirectory("export-builtin-wallpaper");
            Path output = tempDir.resolve("builtin-wallpaper" + ThemePackExporter.FILE_EXTENSION);
            ThemePackManager.exportCurrent(output, "com.example.builtin-wallpaper", "Builtin Wallpaper", "Test Author");

            ThemePackManifest manifest = ThemePackManager.load(output).manifest();
            Theme theme = manifest.findTheme(null);
            assertNotNull(theme);

            ThemeBackgroundSettings background = theme.appearance().background();
            assertNotNull(background);
            ThemeBackground.Builtin builtin = assertInstanceOf(ThemeBackground.Builtin.class, background.source());
            assertEquals(BackgroundType.BUILTIN_WALLPAPER_2016_02_25_ID, builtin.id());
            assertNull(background.fallback());
            assertNull(background.loadPolicy());
        }
    }

    /// Tests resolving the theme-color background option to the current surface container color.
    @Test
    public void testResolveThemeColorBackground() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.customThemeColorProperty().set(ThemeColor.of("#663399"));
            settings.themeColorTypeProperty().set(ThemeColorType.CUSTOM);
            settings.backgroundTypeProperty().set(BackgroundType.THEME_COLOR);
            settings.backgroundOpacityTypeProperty().set(BackgroundOpacityType.CUSTOM);
            settings.backgroundOpacityProperty().set(0.6);

            ThemePackManager.ResolvedBackground background = ThemePackManager.resolveCurrentBackground(
                    new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));

            assertEquals(BackgroundType.THEME_COLOR, background.type());
            assertEquals(Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER), background.paint());
            assertEquals(0.6, background.opacity());
        }
    }

    /// Tests that wallpaper theme colors fall back to the default seed when the background follows the theme color.
    @Test
    public void testThemeColorBackgroundPreventsWallpaperColorCycle() {
        assertEquals(
                ThemeColor.DEFAULT,
                Themes.resolveThemeColor(
                        Objects.requireNonNull(ThemeColor.of("#663399")),
                        ThemeColorType.BACKGROUND,
                        BackgroundType.THEME_COLOR));
    }

    /// Tests that the default theme color type uses the built-in default theme when the stored reference is missing.
    @Test
    public void testDefaultThemeColorWithoutStoredReferenceUsesDefaultTheme() throws Exception {
        try (SettingsScope ignored = new SettingsScope()) {
            LauncherSettings settings = SettingsManager.settings();
            settings.customThemeColorProperty().set(Objects.requireNonNull(ThemeColor.of("#663399")));
            settings.themeColorTypeProperty().set(null);
            settings.themeProperty().set(null);

            assertEquals(Objects.requireNonNull(ThemeColor.of("#5C6BC0")), Themes.resolveCurrentThemeColor());
        }
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
                  "authors": [
                    {
                      "name": "Example"
                    }
                  ],
                  "theme": {
                    "name": "Forest",
                    "thumbnail": "assets/thumbnails/thumbnail.txt",
                    "color": {
                      "source": "wallpaper"
                    },
                    "brightness": "dark",
                    "colorStyle": "expressive",
                    "titleBar": {
                      "transparent": true
                    },
                    "background": {
                      "type": "image",
                      "path": "assets/wallpapers/wallpaper.png",
                      "opacity": 0.75,
                      "fallback": {
                        "type": "paint",
                        "paint": "#112233"
                      },
                      "loadPolicy": "wait_for_background"
                    }
                  }
                }
                """);
    }

    /// Creates a manifest with brightness-dependent appearance overrides.
    ///
    /// @return the parsed manifest
    private static ThemePackManifest createBrightnessConditionManifest() {
        return ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.refresh",
                  "version": "1.0.0",
                  "name": "Refresh Example",
                  "authors": [
                    {
                      "name": "Example"
                    }
                  ],
                  "theme": {
                    "colorStyle": "neutral",
                    "titleBar": {
                      "transparent": false
                    },
                    "background": {
                      "type": "paint",
                      "paint": "#ffffff",
                      "opacity": 0.5
                    },
                    "overrides": [
                      {
                        "condition": {
                          "brightness": "dark"
                        },
                        "colorStyle": "expressive",
                        "titleBar": {
                          "transparent": true
                        },
                        "background": {
                          "paint": "#000000",
                          "opacity": 0.25
                        }
                      }
                    ]
                  }
                }
                """);
    }

    /// Creates a minimal single-theme manifest.
    ///
    /// @param id the package ID
    /// @param version the package version
    /// @param name the package display name
    /// @return the parsed manifest
    private static ThemePackManifest createMinimalManifest(String id, String version, String name) {
        return ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "%s",
                  "version": "%s",
                  "name": "%s",
                  "authors": [
                    {
                      "name": "Example"
                    }
                  ],
                  "theme": {}
                }
                """.formatted(id, version, name));
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
