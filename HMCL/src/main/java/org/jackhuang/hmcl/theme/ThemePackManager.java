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
import org.jackhuang.hmcl.util.io.FileUtils;
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
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

/// Imports, applies, and exports launcher theme packs.
@NotNullByDefault
public final class ThemePackManager {
    /// Directory where imported theme packs are stored for the launcher UI.
    public static final Path THEME_PACKS_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("theme-packs");

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

    /// A theme pack installed under the launcher's local theme-pack directory.
    ///
    /// @param directory the installed theme-pack directory
    /// @param manifest the parsed manifest
    public record InstalledThemePack(Path directory, ThemePackManifest manifest) {
        /// Creates an installed theme-pack descriptor.
        ///
        /// @param directory the installed theme-pack directory
        /// @param manifest the parsed manifest
        public InstalledThemePack {
            directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
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

    /// Loads a manifest from an installed theme-pack directory.
    ///
    /// @param directory the installed theme-pack directory
    /// @return the installed theme pack
    /// @throws IOException if the installed manifest cannot be read or parsed
    public static InstalledThemePack loadInstalled(Path directory) throws IOException {
        Objects.requireNonNull(directory);

        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path manifestFile = normalizedDirectory.resolve(ThemePackExporter.MANIFEST_ENTRY);
        try {
            return new InstalledThemePack(
                    normalizedDirectory,
                    ThemePackManifest.fromJson(Files.readString(manifestFile, StandardCharsets.UTF_8)));
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new IOException("Invalid installed theme-pack manifest", e);
        }
    }

    /// Installs a theme-pack file under the launcher's local theme-pack directory.
    ///
    /// Existing files for the same package ID and version are replaced.
    ///
    /// @param file the theme-pack file
    /// @return the installed theme pack
    /// @throws IOException if the theme pack cannot be validated or extracted
    public static InstalledThemePack install(Path file) throws IOException {
        LoadedThemePack loadedThemePack = load(file);
        Path targetDirectory = installedThemePackDirectory(loadedThemePack.manifest());
        Path temporaryDirectory = temporaryInstallDirectory(targetDirectory);

        Files.createDirectories(Objects.requireNonNull(targetDirectory.getParent()));
        deleteIfExists(temporaryDirectory);
        Files.createDirectories(temporaryDirectory);

        try {
            extractThemePack(loadedThemePack.file(), temporaryDirectory);
            deleteIfExists(targetDirectory);
            Files.move(temporaryDirectory, targetDirectory, StandardCopyOption.REPLACE_EXISTING);
            return new InstalledThemePack(targetDirectory, loadedThemePack.manifest());
        } catch (IOException | RuntimeException e) {
            try {
                deleteIfExists(temporaryDirectory);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /// Applies one theme from a loaded theme pack to current launcher settings.
    ///
    /// @param themePack the loaded theme pack
    /// @param theme the theme to apply
    /// @throws IOException if the pack cannot be installed, referenced assets cannot be read, or settings cannot be applied
    public static void apply(LoadedThemePack themePack, Theme theme) throws IOException {
        Objects.requireNonNull(themePack);
        Objects.requireNonNull(theme);

        InstalledThemePack installedThemePack = install(themePack.file());
        @Nullable Theme installedTheme = installedThemePack.manifest().findTheme(theme.id());
        if (installedTheme == null) {
            throw new IOException("Installed theme pack does not contain theme: " + theme.id());
        }
        apply(installedThemePack, installedTheme);
    }

    /// Applies one theme from an installed theme pack to current launcher settings.
    ///
    /// @param themePack the installed theme pack
    /// @param theme the theme to apply
    /// @throws IOException if referenced assets cannot be read or settings cannot be applied
    public static void apply(InstalledThemePack themePack, Theme theme) throws IOException {
        Objects.requireNonNull(themePack);
        Objects.requireNonNull(theme);

        apply(themePack.directory(), themePack.manifest(), theme, currentResolveContext());
    }

    /// Applies one theme from an installed theme-pack directory to current launcher settings.
    ///
    /// @param themePackDirectory the installed theme-pack directory
    /// @param manifest the parsed manifest
    /// @param theme the theme to apply
    /// @param context the condition resolution context
    /// @throws IOException if referenced assets cannot be read or settings cannot be applied
    public static void apply(
            Path themePackDirectory,
            ThemePackManifest manifest,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(themePackDirectory);
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(context);

        ThemeAppearance appearance = theme.resolve(context);
        LauncherSettings currentSettings = settings();

        if (appearance.brightness() != null) {
            currentSettings.themeBrightnessProperty().set(toLauncherBrightness(appearance.brightness()));
        }
        if (appearance.titleTransparent() != null) {
            currentSettings.titleTransparentProperty().set(appearance.titleTransparent());
        }
        if (appearance.background() != null) {
            applyBackground(manifest, themePackDirectory.toAbsolutePath().normalize(), appearance.background());
        }
        if (appearance.color() != null) {
            currentSettings.themeColorProperty().set(resolveThemeColor(themePackDirectory, appearance));
        }

        currentSettings.themeProperty().set(new ThemeSelection(manifest.id(), manifest.version(), theme.id()));
    }

    /// Exports the current launcher appearance to a theme-pack file.
    ///
    /// @param outputFile the target theme-pack file
    /// @param packId the exported package identifier
    /// @param packName the exported package display name
    /// @param themeName the exported theme display name
    /// @throws IOException if the current appearance cannot be exported
    public static void exportCurrent(Path outputFile, String packId, String packName, String themeName) throws IOException {
        Objects.requireNonNull(outputFile);

        ExportedThemePack themePack = createCurrent(packId, packName, themeName);
        ThemePackExporter.export(themePack.manifest(), themePack.assets(), outputFile);
    }

    /// Creates an exportable theme pack from the current launcher appearance.
    ///
    /// @param packId the exported package identifier
    /// @param packName the exported package display name
    /// @param themeName the exported theme display name
    /// @return the exportable theme-pack descriptor
    /// @throws IOException if the current appearance cannot be represented as a theme pack
    public static ExportedThemePack createCurrent(String packId, String packName, String themeName) throws IOException {
        List<ThemePackAsset> assets = new ArrayList<>();
        ThemeAppearance appearance = new ThemeAppearance(
                ThemeColorSource.fixed(Objects.requireNonNullElse(settings().themeColorProperty().get(), ThemeColor.DEFAULT)),
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
                requireNonBlank(packId, "packId"),
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
            ThemePackManifest manifest,
            Path themePackDirectory,
            ThemeBackground background) throws IOException {
        Objects.requireNonNull(manifest);

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
                resolveInstalledAsset(themePackDirectory, path);
                currentSettings.backgroundTypeProperty().set(BackgroundType.CUSTOM);
                currentSettings.backgroundImageProperty().set(ThemePackResourceURL.of(manifest, path).toString());
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

    /// Resolves a concrete launcher color from a theme appearance.
    private static ThemeColor resolveThemeColor(Path themePackDirectory, ThemeAppearance appearance) throws IOException {
        ThemeColorSource color = Objects.requireNonNull(appearance.color());
        if (color.type() == ThemeColorSource.Type.FIXED) {
            return color.resolveFallback();
        }

        ThemeColor fallback = color.resolveFallback();
        ThemeBackground background = appearance.background();
        if (background == null) {
            return fallback;
        }

        return switch (background.effectiveType()) {
            case IMAGE -> {
                String path = requireNonBlank(background.path(), "background.path");
                yield WallpaperColorExtractor.extract(resolveInstalledAsset(themePackDirectory, path), fallback);
            }
            case PAINT -> {
                Paint paint = parsePaint(requireNonBlank(background.paint(), "background.paint"));
                yield paint instanceof Color paintColor ? ThemeColor.of(paintColor) : fallback;
            }
            case DEFAULT, CLASSIC, NETWORK -> fallback;
        };
    }

    /// Clears background source fields that are irrelevant for built-in backgrounds.
    private static void clearBackgroundSources() {
        settings().backgroundImageProperty().set(null);
        settings().backgroundImageUrlProperty().set(null);
        settings().backgroundPaintProperty().set(null);
    }

    /// Resolves one asset referenced by an installed theme.
    static Path resolveInstalledAsset(Path themePackDirectory, String entryName) throws IOException {
        String normalizedEntryName = ThemePackAsset.normalizeEntryName(entryName);
        Path installedDirectory = themePackDirectory.toAbsolutePath().normalize();
        Path target = installedDirectory.resolve(normalizedEntryName).normalize();
        if (!target.startsWith(installedDirectory)) {
            throw new IOException("Theme-pack asset escapes the installed directory: " + entryName);
        }
        if (!Files.isRegularFile(target)) {
            throw new IOException("Installed theme-pack asset is missing: " + normalizedEntryName);
        }
        return target;
    }

    /// Returns the directory used for one installed theme pack.
    private static Path installedThemePackDirectory(ThemePackManifest manifest) {
        return installedThemePackDirectory(manifest.id(), manifest.version());
    }

    /// Returns the directory used for one installed theme pack.
    static Path installedThemePackDirectory(String packId, String version) {
        return THEME_PACKS_DIRECTORY
                .resolve(sanitizePathSegment(packId))
                .resolve(sanitizePathSegment(version))
                .toAbsolutePath()
                .normalize();
    }

    /// Returns the temporary installation directory for one target directory.
    private static Path temporaryInstallDirectory(Path targetDirectory) {
        return targetDirectory.resolveSibling("." + targetDirectory.getFileName() + ".tmp");
    }

    /// Extracts a validated theme pack into a clean target directory.
    private static void extractThemePack(Path themePackFile, Path targetDirectory) throws IOException {
        Path normalizedTargetDirectory = targetDirectory.toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(themePackFile.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = normalizeThemePackEntryName(entry.getName());
                checkSupportedThemePackEntry(entryName);

                Path target = normalizedTargetDirectory.resolve(entryName).normalize();
                if (!target.startsWith(normalizedTargetDirectory)) {
                    throw new IOException("Theme-pack entry escapes the installation directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(Objects.requireNonNull(target.getParent()));
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /// Returns a normalized and safe theme-pack zip entry name.
    private static String normalizeThemePackEntryName(String entryName) throws IOException {
        Objects.requireNonNull(entryName);

        String normalized = entryName.trim().replace('\\', '/');
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IOException("Theme-pack entry is empty");
        }
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("Theme-pack entry must be relative: " + entryName);
        }

        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Theme-pack entry contains an unsafe segment: " + entryName);
            }
            for (int i = 0; i < segment.length(); i++) {
                char ch = segment.charAt(i);
                if (Character.isISOControl(ch) || ch == '\0') {
                    throw new IOException("Theme-pack entry contains a control character: " + entryName);
                }
            }
        }
        return normalized;
    }

    /// Checks that a theme-pack zip entry belongs to the current file layout.
    private static void checkSupportedThemePackEntry(String entryName) throws IOException {
        if (!ThemePackExporter.MANIFEST_ENTRY.equals(entryName)
                && !"assets".equals(entryName)
                && !entryName.startsWith("assets/")) {
            throw new IOException("Unsupported theme-pack entry: " + entryName);
        }
    }

    /// Deletes an existing file, symbolic link, or directory tree.
    private static void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path) || Files.isSymbolicLink(path)) {
            FileUtils.forceDelete(path);
        }
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
        Path source = resolveBackgroundImageSource(backgroundImage);
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

    /// Resolves a launcher background image value to a local file.
    private static Path resolveBackgroundImageSource(String backgroundImage) throws IOException {
        @Nullable ThemePackResourceURL resourceURL = ThemePackResourceURL.parse(backgroundImage);
        if (resourceURL != null) {
            return resourceURL.resolve();
        }
        return Path.of(backgroundImage).toAbsolutePath().normalize();
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

    /// Sanitizes one path segment used for installed theme packs.
    private static String sanitizePathSegment(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "_";
        }
        return sanitized;
    }
}
