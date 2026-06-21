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

import com.google.gson.JsonParseException;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

/// Imports, applies, and exports launcher theme packs.
@NotNullByDefault
public final class ThemePackManager {
    /// Directory where imported theme-pack assets are extracted for the launcher UI.
    public static final Path INSTALLED_ASSETS_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("theme-packs");

    /// Default identifier used when exporting the current launcher appearance.
    private static final String CURRENT_THEME_PACK_ID = "user.current-theme";

    /// Default version used when exporting the current launcher appearance.
    private static final String CURRENT_THEME_PACK_VERSION = "1.0.0";

    /// Identifier used by the single theme exported from the current launcher appearance.
    private static final String CURRENT_THEME_ID = "current";

    /// Default author used when exporting the current launcher appearance.
    private static final String CURRENT_THEME_AUTHOR = "User";

    /// Prevents instantiation.
    private ThemePackManager() {
    }

    /// A theme pack loaded from a zip-compatible theme-pack file.
    ///
    /// @param file the source theme-pack file
    /// @param manifest the parsed manifest
    public record LoadedThemePack(Path file, ThemePackManifest manifest) {
        /// Creates a loaded theme-pack descriptor.
        ///
        /// @param file the source theme-pack file
        /// @param manifest the parsed manifest
        public LoadedThemePack {
            file = Objects.requireNonNull(file).toAbsolutePath().normalize();
            Objects.requireNonNull(manifest);
        }
    }

    /// A theme pack assembled from launcher settings and ready to export.
    ///
    /// @param manifest the manifest to write
    /// @param assets the asset files to include
    public record ExportedThemePack(ThemePackManifest manifest, @Unmodifiable List<ThemePackAsset> assets) {
        /// Creates an exportable theme-pack descriptor.
        ///
        /// @param manifest the manifest to write
        /// @param assets the asset files to include
        public ExportedThemePack {
            Objects.requireNonNull(manifest);
            assets = List.copyOf(assets);
        }
    }

    /// Loads and parses a theme-pack file.
    ///
    /// @param file the theme-pack file
    /// @return the loaded theme pack
    /// @throws IOException if the file cannot be read or the manifest is invalid
    public static LoadedThemePack load(Path file) throws IOException {
        Objects.requireNonNull(file);

        Path normalizedFile = file.toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(normalizedFile.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry manifestEntry = zipFile.getEntry(ThemePackExporter.MANIFEST_ENTRY);
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                throw new IOException("Theme pack does not contain " + ThemePackExporter.MANIFEST_ENTRY);
            }

            String manifestJson;
            try (InputStream input = zipFile.getInputStream(manifestEntry)) {
                manifestJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new LoadedThemePack(normalizedFile, ThemePackManifest.fromJson(manifestJson));
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new IOException("Invalid theme-pack manifest", e);
        }
    }

    /// Applies one theme from a loaded theme pack to current launcher settings.
    ///
    /// @param themePack the loaded theme pack
    /// @param theme the theme to apply
    /// @throws IOException if referenced assets cannot be extracted or settings cannot be applied
    public static void apply(LoadedThemePack themePack, Theme theme) throws IOException {
        Objects.requireNonNull(themePack);
        Objects.requireNonNull(theme);

        apply(themePack.file(), themePack.manifest(), theme, currentResolveContext());
    }

    /// Applies one theme from a theme-pack file to current launcher settings.
    ///
    /// @param themePackFile the source theme-pack file
    /// @param manifest the parsed manifest
    /// @param theme the theme to apply
    /// @param context the condition resolution context
    /// @throws IOException if referenced assets cannot be extracted or settings cannot be applied
    public static void apply(
            Path themePackFile,
            ThemePackManifest manifest,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(themePackFile);
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(context);

        ThemeAppearance appearance = theme.resolve(context);
        LauncherSettings currentSettings = settings();

        if (appearance.color() != null) {
            currentSettings.themeColorProperty().set(appearance.color());
        }
        if (appearance.brightness() != null) {
            currentSettings.themeBrightnessProperty().set(toLauncherBrightness(appearance.brightness()));
        }
        if (appearance.titleTransparent() != null) {
            currentSettings.titleTransparentProperty().set(appearance.titleTransparent());
        }
        if (appearance.background() != null) {
            applyBackground(themePackFile.toAbsolutePath().normalize(), manifest, theme, appearance.background());
        }
    }

    /// Exports the current launcher appearance to a theme-pack file.
    ///
    /// @param outputFile the target theme-pack file
    /// @param packName the exported package display name
    /// @param themeName the exported theme display name
    /// @throws IOException if the current appearance cannot be exported
    public static void exportCurrent(Path outputFile, String packName, String themeName) throws IOException {
        Objects.requireNonNull(outputFile);

        ExportedThemePack themePack = createCurrent(packName, themeName);
        ThemePackExporter.export(themePack.manifest(), themePack.assets(), outputFile);
    }

    /// Creates an exportable theme pack from the current launcher appearance.
    ///
    /// @param packName the exported package display name
    /// @param themeName the exported theme display name
    /// @return the exportable theme-pack descriptor
    /// @throws IOException if the current appearance cannot be represented as a theme pack
    public static ExportedThemePack createCurrent(String packName, String themeName) throws IOException {
        List<ThemePackAsset> assets = new ArrayList<>();
        ThemeAppearance appearance = new ThemeAppearance(
                Objects.requireNonNullElse(settings().themeColorProperty().get(), ThemeColor.DEFAULT),
                currentThemeBrightness(),
                ColorStyle.FIDELITY,
                Contrast.DEFAULT,
                createCurrentBackground(assets),
                settings().titleTransparentProperty().get());
        Theme theme = new Theme(
                CURRENT_THEME_ID,
                requireNonBlank(themeName, "themeName"),
                null,
                null,
                appearance,
                List.of());
        ThemePackManifest manifest = new ThemePackManifest(
                CURRENT_THEME_PACK_ID,
                CURRENT_THEME_PACK_VERSION,
                requireNonBlank(packName, "packName"),
                List.of(CURRENT_THEME_AUTHOR),
                null,
                List.of(theme));

        return new ExportedThemePack(manifest, assets);
    }

    /// Returns the current condition resolution context.
    ///
    /// @return the current resolution context
    public static ThemeResolveContext currentResolveContext() {
        String brightnessMode = settings().themeBrightnessProperty().get();
        if (StringUtils.isBlank(brightnessMode)) {
            brightnessMode = "auto";
        }
        return ThemeResolveContext.current(Themes.getTheme().brightness(), brightnessMode);
    }

    /// Applies background fields from a resolved theme appearance.
    private static void applyBackground(
            Path themePackFile,
            ThemePackManifest manifest,
            Theme theme,
            ThemeBackground background) throws IOException {
        LauncherSettings currentSettings = settings();
        if (background.opacity() != null) {
            currentSettings.backgroundOpacityProperty().set(background.opacity());
        }

        switch (background.effectiveType()) {
            case DEFAULT -> {
                currentSettings.backgroundTypeProperty().set(BackgroundType.DEFAULT);
                clearBackgroundSources();
            }
            case CLASSIC -> {
                currentSettings.backgroundTypeProperty().set(BackgroundType.CLASSIC);
                clearBackgroundSources();
            }
            case IMAGE -> {
                String path = requireNonBlank(background.path(), "background.path");
                Path extractedAsset = extractAsset(themePackFile, manifest, theme, path);
                currentSettings.backgroundTypeProperty().set(BackgroundType.CUSTOM);
                currentSettings.backgroundImageProperty().set(extractedAsset.toString());
                currentSettings.backgroundImageUrlProperty().set(null);
                currentSettings.backgroundPaintProperty().set(null);
            }
            case NETWORK -> {
                currentSettings.backgroundTypeProperty().set(BackgroundType.NETWORK);
                currentSettings.backgroundImageUrlProperty().set(requireNonBlank(background.url(), "background.url"));
                currentSettings.backgroundImageProperty().set(null);
                currentSettings.backgroundPaintProperty().set(null);
            }
            case PAINT -> {
                currentSettings.backgroundTypeProperty().set(BackgroundType.PAINT);
                currentSettings.backgroundPaintProperty().set(parsePaint(requireNonBlank(background.paint(), "background.paint")));
                currentSettings.backgroundImageProperty().set(null);
                currentSettings.backgroundImageUrlProperty().set(null);
            }
        }
    }

    /// Clears background source fields that are irrelevant for built-in backgrounds.
    private static void clearBackgroundSources() {
        settings().backgroundImageProperty().set(null);
        settings().backgroundImageUrlProperty().set(null);
        settings().backgroundPaintProperty().set(null);
    }

    /// Extracts one asset referenced by an imported theme.
    private static Path extractAsset(
            Path themePackFile,
            ThemePackManifest manifest,
            Theme theme,
            String entryName) throws IOException {
        String normalizedEntryName = ThemePackAsset.normalizeEntryName(entryName);
        Path targetDirectory = installedAssetDirectory(manifest, theme);
        Path target = targetDirectory.resolve(normalizedEntryName).normalize();
        if (!target.startsWith(targetDirectory)) {
            throw new IOException("Theme-pack asset escapes the target directory: " + entryName);
        }

        try (ZipFile zipFile = new ZipFile(themePackFile.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(normalizedEntryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Theme-pack asset is missing: " + normalizedEntryName);
            }

            Files.createDirectories(Objects.requireNonNull(target.getParent()));
            try (InputStream input = zipFile.getInputStream(entry)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    /// Returns the directory used for assets extracted from one theme.
    private static Path installedAssetDirectory(ThemePackManifest manifest, Theme theme) {
        return INSTALLED_ASSETS_DIRECTORY
                .resolve(sanitizePathSegment(manifest.id()))
                .resolve(sanitizePathSegment(manifest.version()))
                .resolve(sanitizePathSegment(theme.id()))
                .toAbsolutePath()
                .normalize();
    }

    /// Creates the background model for the current launcher settings.
    private static ThemeBackground createCurrentBackground(List<ThemePackAsset> assets) throws IOException {
        BackgroundType type = settings().backgroundTypeProperty().get();
        if (type == null) {
            type = BackgroundType.DEFAULT;
        }

        Double opacity = currentBackgroundOpacity();
        return switch (type) {
            case DEFAULT -> new ThemeBackground(ThemeBackground.Type.DEFAULT, null, null, null, opacity);
            case CLASSIC -> new ThemeBackground(ThemeBackground.Type.CLASSIC, null, null, null, opacity);
            case CUSTOM -> createCurrentImageBackground(assets, opacity);
            case NETWORK -> new ThemeBackground(
                    ThemeBackground.Type.NETWORK,
                    null,
                    requireNonBlank(settings().backgroundImageUrlProperty().get(), "backgroundImageUrl"),
                    null,
                    opacity);
            case PAINT -> new ThemeBackground(
                    ThemeBackground.Type.PAINT,
                    null,
                    null,
                    Objects.requireNonNullElse(settings().backgroundPaintProperty().get(), Color.WHITE).toString(),
                    opacity);
        };
    }

    /// Creates the image background model for the current launcher settings.
    private static ThemeBackground createCurrentImageBackground(List<ThemePackAsset> assets, Double opacity) throws IOException {
        String backgroundImage = requireNonBlank(settings().backgroundImageProperty().get(), "backgroundImage");
        Path source = Path.of(backgroundImage).toAbsolutePath().normalize();
        if (Files.isDirectory(source)) {
            throw new IOException("Cannot export a background directory as a theme-pack asset: " + source);
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("Theme background image does not exist: " + source);
        }

        String entryName = "assets/background/" + sanitizePathSegment(source.getFileName().toString());
        assets.add(new ThemePackAsset(source, entryName));
        return new ThemeBackground(ThemeBackground.Type.IMAGE, entryName, null, null, opacity);
    }

    /// Returns the current launcher background opacity.
    private static Double currentBackgroundOpacity() {
        double opacity = settings().backgroundOpacityProperty().get();
        if (!Double.isFinite(opacity)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, opacity));
    }

    /// Returns the current launcher brightness as a theme-pack directive.
    private static ThemeBrightness currentThemeBrightness() {
        String brightness = settings().themeBrightnessProperty().get();
        if (StringUtils.isBlank(brightness)) {
            return ThemeBrightness.ADAPTIVE;
        }
        try {
            return ThemeBrightness.parse(brightness);
        } catch (IllegalArgumentException e) {
            return ThemeBrightness.ADAPTIVE;
        }
    }

    /// Converts a theme-pack brightness directive to a launcher setting value.
    private static String toLauncherBrightness(ThemeBrightness brightness) {
        return switch (brightness) {
            case LIGHT -> "light";
            case DARK -> "dark";
            case ADAPTIVE -> "auto";
        };
    }

    /// Parses a serialized JavaFX paint value.
    private static Paint parsePaint(String value) throws IOException {
        try {
            return Paint.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid theme background paint: " + value, e);
        }
    }

    /// Returns a non-blank string value.
    private static String requireNonBlank(@Nullable String value, String name) throws IOException {
        if (StringUtils.isBlank(value)) {
            throw new IOException("Theme pack value is missing: " + name);
        }
        return value.trim();
    }

    /// Sanitizes one path segment used for stored theme-pack assets.
    private static String sanitizePathSegment(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "_";
        }
        return sanitized;
    }
}
