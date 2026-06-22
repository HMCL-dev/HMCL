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
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.ThemeColorType;
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
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Imports, applies, and exports launcher theme packs.
@NotNullByDefault
public final class ThemePackManager {
    /// Directory where imported theme packs are stored for the launcher UI.
    public static final Path THEME_PACKS_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("theme-packs");

    /// Default version used when exporting the current launcher appearance.
    private static final String CURRENT_THEME_PACK_VERSION = "1.0.0";

    /// Whether a theme is currently being applied to launcher settings.
    private static boolean applyingTheme = false;

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

    /// A concrete launcher background resolved from either custom settings or a selected theme.
    ///
    /// @param type the launcher background source type
    /// @param imagePath the resolved local image file or directory, or `null` when not using a local image
    /// @param networkImageUrl the remote image URL, or `null` when not using a network image
    /// @param paint the resolved background paint, or `null` when not using a paint background
    /// @param opacity the background opacity clamped to `[0, 1]`
    public record ResolvedBackground(
            BackgroundType type,
            @Nullable Path imagePath,
            @Nullable String networkImageUrl,
            @Nullable Paint paint,
            double opacity) {
        /// Creates a resolved launcher background.
        ///
        /// @param type the launcher background source type
        /// @param imagePath the resolved local image file or directory, or `null` when not using a local image
        /// @param networkImageUrl the remote image URL, or `null` when not using a network image
        /// @param paint the resolved background paint, or `null` when not using a paint background
        /// @param opacity the background opacity clamped to `[0, 1]`
        public ResolvedBackground {
            Objects.requireNonNull(type);
            if (!Double.isFinite(opacity)) {
                opacity = 1.0;
            }
            opacity = Math.max(0.0, Math.min(1.0, opacity));
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

    /// Loads the installed theme pack referenced by a theme selection.
    ///
    /// @param selection the selected installed theme
    /// @return the installed theme pack, or `null` if the selected package directory does not exist
    /// @throws IOException if the installed manifest cannot be read or parsed
    public static @Nullable InstalledThemePack findInstalled(ThemeSelection selection) throws IOException {
        Objects.requireNonNull(selection);

        Path directory = installedThemePackDirectory(selection.packId());
        if (!Files.isDirectory(directory)) {
            return null;
        }
        return loadInstalled(directory);
    }

    /// Lists all installed theme packs.
    ///
    /// @return installed theme packs sorted by display name and package ID
    /// @throws IOException if installed theme-pack metadata cannot be listed or parsed
    public static @Unmodifiable List<InstalledThemePack> listInstalled() throws IOException {
        if (!Files.isDirectory(THEME_PACKS_DIRECTORY)) {
            return List.of();
        }

        ArrayList<InstalledThemePack> result = new ArrayList<>();
        try (Stream<Path> themePackDirectories = Files.list(THEME_PACKS_DIRECTORY)) {
            for (Path themePackDirectory : themePackDirectories
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .toList()) {
                result.add(loadInstalled(themePackDirectory));
            }
        }

        result.sort(Comparator
                .comparing((InstalledThemePack themePack) -> themePack.manifest().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(themePack -> themePack.manifest().id(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    /// Installs a theme-pack file under the launcher's local theme-pack directory.
    ///
    /// Existing files for the same package ID are replaced.
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

    /// Removes an installed theme pack from the launcher's local theme-pack directory.
    ///
    /// If the removed package is currently selected, the stored theme selection is cleared.
    ///
    /// @param themePack the installed theme pack to remove
    /// @throws IOException if the installed package cannot be removed
    public static void uninstall(InstalledThemePack themePack) throws IOException {
        Objects.requireNonNull(themePack);

        Path rootDirectory = THEME_PACKS_DIRECTORY.toAbsolutePath().normalize();
        Path targetDirectory = themePack.directory().toAbsolutePath().normalize();
        if (!targetDirectory.startsWith(rootDirectory) || targetDirectory.equals(rootDirectory)) {
            throw new IOException("Theme-pack directory is outside the managed directory: " + targetDirectory);
        }

        deleteIfExists(targetDirectory);

        @Nullable ThemeSelection selection = settings().themeProperty().get();
        ThemePackManifest manifest = themePack.manifest();
        if (selection != null
                && selection.packId().equals(manifest.id())) {
            settings().themeProperty().set(null);
        }
        @Nullable ThemeSelection backgroundSelection = settings().backgroundThemeProperty().get();
        if (backgroundSelection != null
                && backgroundSelection.packId().equals(manifest.id())) {
            settings().backgroundThemeProperty().set(null);
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
        applyTheme(themePackDirectory, manifest, theme, context);
    }

    /// Reapplies the selected theme against the current condition context.
    ///
    /// A selected theme is resolved again with the latest condition inputs. If the resolved appearance contains
    /// a non-null brightness directive, that directive is written back to launcher settings.
    public static void refreshCurrentThemeForContext() {
        if (applyingTheme) {
            return;
        }

        @Nullable ThemeSelection selection = settings().themeProperty().get();
        if (selection == null) {
            return;
        }

        try {
            @Nullable InstalledThemePack themePack = findInstalled(selection);
            if (themePack == null) {
                return;
            }

            @Nullable Theme theme = themePack.manifest().findTheme(selection.themeId());
            if (theme == null) {
                return;
            }

            applyTheme(themePack.directory(), themePack.manifest(), theme, currentResolveContext());
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to refresh selected theme", e);
        }
    }

    /// Returns whether a theme is currently being applied to launcher settings.
    ///
    /// @return `true` while theme fields are being written into launcher settings
    public static boolean isApplyingTheme() {
        return applyingTheme;
    }

    /// Applies one theme from an installed theme-pack directory to current launcher settings.
    private static void applyTheme(
            Path themePackDirectory,
            ThemePackManifest manifest,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(themePackDirectory);
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(context);

        boolean previousApplyingTheme = applyingTheme;
        applyingTheme = true;
        try {
            ThemeSelection selection = new ThemeSelection(manifest.id(), theme.id());
            ThemeAppearance appearance = theme.resolve(context);
            LauncherSettings currentSettings = settings();

            if (appearance.brightness() != null) {
                currentSettings.themeBrightnessProperty().set(toLauncherBrightness(appearance.brightness()));
            }
            if (appearance.titleBar() != null && appearance.titleBar().transparent() != null) {
                currentSettings.titleTransparentProperty().set(appearance.titleBar().transparent());
            }
            if (appearance.background() != null) {
                applyBackground(selection, themePackDirectory.toAbsolutePath().normalize(), appearance.background());
            }
            if (appearance.colorStyle() != null) {
                currentSettings.themeColorStyleProperty().set(appearance.colorStyle());
            }
            if (appearance.color() != null) {
                currentSettings.themeColorTypeProperty().set(toThemeColorType(appearance.color()));
                currentSettings.customThemeColorProperty().set(resolveThemeColor(themePackDirectory, appearance));
            }

            currentSettings.themeProperty().set(selection);
        } finally {
            applyingTheme = previousApplyingTheme;
        }
    }

    /// Exports the current launcher appearance to a theme-pack file.
    ///
    /// @param outputFile the target theme-pack file
    /// @param packId the exported package identifier
    /// @param packName the exported package display name
    /// @param authorName the exported package author name
    /// @throws IOException if the current appearance cannot be exported
    public static void exportCurrent(Path outputFile, String packId, String packName, String authorName) throws IOException {
        Objects.requireNonNull(outputFile);

        ExportedThemePack themePack = createCurrent(packId, packName, authorName);
        ThemePackExporter.export(themePack.manifest(), themePack.assets(), outputFile);
    }

    /// Creates an exportable theme pack from the current launcher appearance.
    ///
    /// @param packId the exported package identifier
    /// @param packName the exported package display name
    /// @param authorName the exported package author name
    /// @return the exportable theme-pack descriptor
    /// @throws IOException if the current appearance cannot be represented as a theme pack
    public static ExportedThemePack createCurrent(String packId, String packName, String authorName) throws IOException {
        packName = requireNonBlank(packName, "packName");

        List<ThemePackAsset> assets = new ArrayList<>();
        ThemeAppearance appearance = new ThemeAppearance(
                currentThemeColorSource(),
                currentControlledBrightness(),
                currentColorStyle(),
                Contrast.DEFAULT,
                createCurrentBackground(assets),
                new ThemeTitleBar(settings().titleTransparentProperty().get()));
        Theme theme = new Theme(
                null,
                null,
                null,
                null,
                appearance,
                List.of());
        ThemePackManifest manifest = new ThemePackManifest(
                requireNonBlank(packId, "packId"),
                CURRENT_THEME_PACK_VERSION,
                packName,
                List.of(requireNonBlank(authorName, "authorName")),
                null,
                List.of(theme));

        return new ExportedThemePack(manifest, assets);
    }

    /// Returns the current launcher theme color source as a theme-pack directive.
    private static ThemeColorSource currentThemeColorSource() {
        ThemeColor fallback = Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT);
        ThemeColorType themeColorType = Objects.requireNonNullElse(settings().themeColorTypeProperty().get(), ThemeColorType.CUSTOM);
        return themeColorType == ThemeColorType.BACKGROUND
                ? ThemeColorSource.wallpaper(fallback)
                : ThemeColorSource.custom(fallback);
    }

    /// Returns the current launcher color style as a theme-pack directive.
    private static ColorStyle currentColorStyle() {
        return Objects.requireNonNullElse(settings().themeColorStyleProperty().get(), ResolvedTheme.DEFAULT.colorStyle());
    }

    /// Returns the current launcher brightness as an explicit theme-pack directive, or `null` for auto mode.
    private static @Nullable Brightness currentControlledBrightness() {
        String brightness = settings().themeBrightnessProperty().get();
        if (StringUtils.isBlank(brightness)) {
            return null;
        }
        return switch (brightness.trim().toLowerCase(Locale.ROOT)) {
            case "light" -> Brightness.LIGHT;
            case "dark" -> Brightness.DARK;
            default -> null;
        };
    }

    /// Converts a theme-pack color directive into the launcher setting source type.
    private static ThemeColorType toThemeColorType(ThemeColorSource color) {
        return color instanceof ThemeColorSource.Wallpaper
                ? ThemeColorType.BACKGROUND
                : ThemeColorType.CUSTOM;
    }

    /// Returns the current condition resolution context.
    ///
    /// @return the current resolution context
    public static ThemeResolveContext currentResolveContext() {
        return ThemeResolveContext.current(Themes.getCurrentBrightness());
    }

    /// Resolves the background currently selected by launcher settings.
    ///
    /// When [LauncherSettings#backgroundTypeProperty()] is [BackgroundType#THEME], this resolves the selected
    /// background theme first. Other background types are resolved from the launcher custom background fields.
    ///
    /// @param context the condition resolution context
    /// @return the currently effective launcher background
    /// @throws IOException if the referenced theme pack exists but its background cannot be resolved
    public static ResolvedBackground resolveCurrentBackground(ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(context);

        BackgroundType type = Objects.requireNonNullElse(settings().backgroundTypeProperty().get(), BackgroundType.DEFAULT);
        if (type == BackgroundType.THEME) {
            @Nullable ThemeSelection selection = settings().backgroundThemeProperty().get();
            if (selection != null) {
                @Nullable ResolvedBackground resolved = resolveThemeBackground(selection, context);
                if (resolved != null) {
                    return resolved;
                }
            }
            return new ResolvedBackground(BackgroundType.DEFAULT, null, null, null, currentBackgroundOpacity());
        }

        return resolveCustomBackground();
    }

    /// Resolves the launcher custom background fields without considering theme-pack background references.
    ///
    /// @return the custom launcher background settings
    public static ResolvedBackground resolveCustomBackground() {
        BackgroundType type = Objects.requireNonNullElse(settings().backgroundTypeProperty().get(), BackgroundType.DEFAULT);
        double opacity = currentBackgroundOpacity();
        return switch (type) {
            case THEME, DEFAULT -> new ResolvedBackground(BackgroundType.DEFAULT, null, null, null, opacity);
            case CLASSIC -> new ResolvedBackground(BackgroundType.CLASSIC, null, null, null, opacity);
            case CUSTOM -> {
                @Nullable String customBackgroundImagePath = settings().customBackgroundImagePathProperty().get();
                @Nullable Path imagePath = StringUtils.isBlank(customBackgroundImagePath)
                        ? null
                        : Path.of(customBackgroundImagePath).toAbsolutePath().normalize();
                yield new ResolvedBackground(BackgroundType.CUSTOM, imagePath, null, null, opacity);
            }
            case NETWORK -> {
                @Nullable String networkBackgroundImageUrl = settings().networkBackgroundImageUrlProperty().get();
                yield new ResolvedBackground(
                        BackgroundType.NETWORK,
                        null,
                        StringUtils.isBlank(networkBackgroundImageUrl) ? null : networkBackgroundImageUrl.trim(),
                        null,
                        opacity);
            }
            case PAINT -> new ResolvedBackground(
                    BackgroundType.PAINT,
                    null,
                    null,
                    settings().customBackgroundPaintProperty().get(),
                    opacity);
        };
    }

    /// Resolves the background contributed by a selected installed theme.
    ///
    /// @param selection the selected theme reference
    /// @param context the condition resolution context
    /// @return the resolved background, or `null` when the theme or background is not available
    /// @throws IOException if the installed theme pack exists but its background cannot be resolved
    public static @Nullable ResolvedBackground resolveThemeBackground(
            ThemeSelection selection,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(selection);
        Objects.requireNonNull(context);

        @Nullable InstalledThemePack themePack = findInstalled(selection);
        if (themePack == null) {
            return null;
        }

        @Nullable Theme theme = themePack.manifest().findTheme(selection.themeId());
        if (theme == null) {
            return null;
        }

        ThemeAppearance appearance = theme.resolve(context);
        @Nullable ThemeBackground background = appearance.background();
        if (background == null) {
            return null;
        }
        return resolveBackground(themePack.directory(), background, currentBackgroundOpacity());
    }

    /// Applies background fields from a resolved theme appearance.
    private static void applyBackground(
            ThemeSelection selection,
            Path themePackDirectory,
            ThemeBackground background) throws IOException {
        Objects.requireNonNull(selection);

        resolveBackground(themePackDirectory, background, currentBackgroundOpacity());
        LauncherSettings currentSettings = settings();
        @Nullable Double opacity = background.opacity();
        if (opacity != null) {
            currentSettings.backgroundOpacityProperty().set(opacity);
        }
        currentSettings.backgroundThemeProperty().set(selection);
        currentSettings.backgroundTypeProperty().set(BackgroundType.THEME);
    }

    /// Resolves a concrete launcher background from a theme-pack background object.
    private static ResolvedBackground resolveBackground(
            Path themePackDirectory,
            ThemeBackground background,
            double opacity) throws IOException {
        if (background instanceof ThemeBackground.Image image) {
            return new ResolvedBackground(
                    BackgroundType.CUSTOM,
                    resolveInstalledAsset(themePackDirectory, requireNonBlank(image.path(), "background.path")),
                    null,
                    null,
                    opacity);
        }
        if (background instanceof ThemeBackground.Network network) {
            return new ResolvedBackground(
                    BackgroundType.NETWORK,
                    null,
                    requireNonBlank(network.url(), "background.url"),
                    null,
                    opacity);
        }
        if (background instanceof ThemeBackground.Paint paint) {
            return new ResolvedBackground(
                    BackgroundType.PAINT,
                    null,
                    null,
                    parsePaint(requireNonBlank(paint.paint(), "background.paint")),
                    opacity);
        }
        if (background instanceof ThemeBackground.Patch patch) {
            return resolvePartialBackground(themePackDirectory, patch, opacity);
        }
        return new ResolvedBackground(BackgroundType.DEFAULT, null, null, null, opacity);
    }

    /// Resolves a concrete launcher color from a theme appearance.
    private static ThemeColor resolveThemeColor(Path themePackDirectory, ThemeAppearance appearance) throws IOException {
        ThemeColorSource color = Objects.requireNonNull(appearance.color());
        if (color instanceof ThemeColorSource.Custom) {
            return color.resolveFallback();
        }

        ThemeColor fallback = color.resolveFallback();
        ThemeBackground background = appearance.background();
        if (background == null) {
            return fallback;
        }

        if (background instanceof ThemeBackground.Image image) {
            String path = requireNonBlank(image.path(), "background.path");
            return WallpaperColorExtractor.extract(resolveInstalledAsset(themePackDirectory, path), fallback);
        }
        if (background instanceof ThemeBackground.Paint paintBackground) {
            Paint paint = parsePaint(requireNonBlank(paintBackground.paint(), "background.paint"));
            return paint instanceof Color paintColor ? ThemeColor.of(paintColor) : fallback;
        }
        if (background instanceof ThemeBackground.Patch patch) {
            if (patch.path() != null) {
                return WallpaperColorExtractor.extract(resolveInstalledAsset(themePackDirectory, patch.path()), fallback);
            }
            if (patch.paint() != null) {
                Paint paint = parsePaint(patch.paint());
                return paint instanceof Color paintColor ? ThemeColor.of(paintColor) : fallback;
            }
        }
        return fallback;
    }

    /// Resolves a partial background object without an explicit source type.
    private static ResolvedBackground resolvePartialBackground(
            Path themePackDirectory,
            ThemeBackground.Patch patch,
            double opacity) throws IOException {
        if (patch.path() != null) {
            return new ResolvedBackground(
                    BackgroundType.CUSTOM,
                    resolveInstalledAsset(themePackDirectory, patch.path()),
                    null,
                    null,
                    opacity);
        }
        if (patch.url() != null) {
            return new ResolvedBackground(
                    BackgroundType.NETWORK,
                    null,
                    patch.url(),
                    null,
                    opacity);
        }
        if (patch.paint() != null) {
            return new ResolvedBackground(
                    BackgroundType.PAINT,
                    null,
                    null,
                    parsePaint(patch.paint()),
                    opacity);
        }
        return new ResolvedBackground(BackgroundType.DEFAULT, null, null, null, opacity);
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
        return installedThemePackDirectory(manifest.id());
    }

    /// Returns the directory used for one installed theme pack.
    static Path installedThemePackDirectory(String packId) {
        return THEME_PACKS_DIRECTORY
                .resolve(sanitizePathSegment(packId))
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
        ResolvedBackground background = resolveCurrentBackground(currentResolveContext());
        Double opacity = background.opacity();
        return switch (background.type()) {
            case THEME, DEFAULT -> new ThemeBackground.Builtin(opacity);
            case CLASSIC -> throw new IOException("Theme packs cannot reference the classic built-in background");
            case CUSTOM -> createCurrentImageBackground(assets, background.imagePath(), opacity);
            case NETWORK -> new ThemeBackground.Network(
                    requireNonBlank(background.networkImageUrl(), "networkBackgroundImageUrl"),
                    opacity);
            case PAINT -> new ThemeBackground.Paint(
                    Objects.requireNonNullElse(background.paint(), Color.WHITE).toString(),
                    opacity);
        };
    }

    /// Creates the image background model for the current launcher settings.
    private static ThemeBackground createCurrentImageBackground(
            List<ThemePackAsset> assets,
            @Nullable Path imagePath,
            Double opacity) throws IOException {
        if (imagePath == null) {
            throw new IOException("Theme background image path is not configured");
        }
        Path source = imagePath.toAbsolutePath().normalize();
        if (Files.isDirectory(source)) {
            throw new IOException("Cannot export a background directory as a theme-pack asset: " + source);
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("Theme background image does not exist: " + source);
        }

        String entryName = "assets/background/" + sanitizePathSegment(source.getFileName().toString());
        assets.add(new ThemePackAsset(source, entryName));
        return new ThemeBackground.Image(entryName, opacity);
    }

    /// Returns the current launcher background opacity.
    private static Double currentBackgroundOpacity() {
        double opacity = settings().backgroundOpacityProperty().get();
        if (!Double.isFinite(opacity)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, opacity));
    }

    /// Parses a serialized JavaFX paint value.
    private static Paint parsePaint(String value) throws IOException {
        try {
            return Paint.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid theme background paint: " + value, e);
        }
    }

    /// Converts a theme-pack brightness directive to a launcher setting value.
    private static String toLauncherBrightness(Brightness brightness) {
        return switch (brightness) {
            case LIGHT -> "light";
            case DARK -> "dark";
        };
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
