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
import kala.compress.archivers.zip.ZipArchiveReader;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorRole;
import org.glavo.monetfx.ColorStyle;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.BuiltinBackground;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.util.MathUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Imports, applies, and exports launcher theme packs.
@NotNullByDefault
public final class ThemePackManager {
    /// Directory where imported theme packs are stored for the launcher UI.
    public static final Path THEME_PACKS_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("themes");

    /// Directory where user-wide theme packs are discovered.
    private static final Path USER_THEME_PACKS_DIRECTORY = Metadata.HMCL_USER_HOME.resolve("themes");

    /// Directory containing files extracted from installed theme packs on demand.
    private static final String CACHE_DIRECTORY = ".cache";

    /// Default version used when exporting the current launcher appearance.
    private static final String CURRENT_THEME_PACK_VERSION = "1.0.0";

    /// IDs of launcher-bundled theme packs in display order.
    private static final @Unmodifiable List<String> BUILTIN_THEME_PACK_IDS = List.of(
            "hmcl.default",
            "hmcl.classic"
    );

    /// Built-in default theme reference.
    public static final ThemeReference BUILTIN_DEFAULT_THEME_REFERENCE = LauncherSettings.DEFAULT_THEME_REFERENCE;

    /// Built-in theme packs bundled with the launcher.
    private static final @Unmodifiable List<InstalledThemePack> BUILTIN_THEME_PACKS = loadBuiltinThemePacks();

    /// Prevents instantiation.
    private ThemePackManager() {
    }

    /// A theme pack loaded from a zip-compatible theme-pack file.
    ///
    /// @param file     the source theme-pack file
    /// @param manifest the parsed manifest
    public record LoadedThemePack(Path file, ThemePackManifest manifest) {
        /// Creates a loaded theme-pack descriptor.
        ///
        /// @param file     the source theme-pack file
        /// @param manifest the parsed manifest
        public LoadedThemePack {
            file = Objects.requireNonNull(file).toAbsolutePath().normalize();
            Objects.requireNonNull(manifest);
        }
    }

    /// A theme pack available to the launcher.
    ///
    /// @param file     the installed theme-pack file, or a synthetic path for built-in packages
    /// @param manifest the parsed manifest
    /// @param builtin  whether this package is bundled with the launcher resources
    public record InstalledThemePack(Path file, ThemePackManifest manifest, boolean builtin) {
        /// Creates an installed user theme-pack descriptor.
        ///
        /// @param file     the installed theme-pack file
        /// @param manifest the parsed manifest
        public InstalledThemePack(Path file, ThemePackManifest manifest) {
            this(file, manifest, false);
        }

        /// Creates an available theme-pack descriptor.
        ///
        /// @param file     the installed theme-pack file, or a synthetic path for built-in packages
        /// @param manifest the parsed manifest
        /// @param builtin  whether this package is bundled with the launcher resources
        public InstalledThemePack {
            file = Objects.requireNonNull(file).toAbsolutePath().normalize();
            Objects.requireNonNull(manifest);
        }
    }

    /// A theme pack assembled from launcher settings and ready to export.
    ///
    /// @param manifest the manifest to write
    /// @param assets   the asset files to include
    public record ExportedThemePack(ThemePackManifest manifest, @Unmodifiable List<ThemePackAsset> assets) {
        /// Creates an exportable theme-pack descriptor.
        ///
        /// @param manifest the manifest to write
        /// @param assets   the asset files to include
        public ExportedThemePack {
            Objects.requireNonNull(manifest);
            assets = List.copyOf(assets);
        }
    }

    /// A concrete launcher background resolved from either custom settings or a selected theme.
    ///
    /// @param type                    the launcher background source type
    /// @param builtinBackgroundId     the selected built-in wallpaper ID, or `null` when not using a built-in wallpaper
    /// @param imagePath               the resolved local image file or directory, or `null` when not using a local image
    /// @param networkImageUrl         the remote image URL, or `null` when not using a network image
    /// @param networkImageCachePolicy whether the remote image cache policy is explicitly overridden, or `null` for default behavior
    /// @param paint                   the resolved background paint, or `null` when not using a paint background
    /// @param opacity                 the background opacity clamped to `[0, 1]`
    public record ResolvedBackground(
            BackgroundType type,
            @Nullable String builtinBackgroundId,
            @Nullable Path imagePath,
            @Nullable String networkImageUrl,
            @Nullable NetworkBackgroundImageCachePolicy networkImageCachePolicy,
            @Nullable Paint paint,
            double opacity) {
        /// Creates a resolved launcher background without a built-in wallpaper ID.
        ///
        /// @param type                    the launcher background source type
        /// @param imagePath               the resolved local image file or directory, or `null` when not using a local image
        /// @param networkImageUrl         the remote image URL, or `null` when not using a network image
        /// @param networkImageCachePolicy whether the remote image cache policy is explicitly overridden, or `null` for default behavior
        /// @param paint                   the resolved background paint, or `null` when not using a paint background
        /// @param opacity                 the background opacity clamped to `[0, 1]`
        public ResolvedBackground(
                BackgroundType type,
                @Nullable Path imagePath,
                @Nullable String networkImageUrl,
                @Nullable NetworkBackgroundImageCachePolicy networkImageCachePolicy,
                @Nullable Paint paint,
                double opacity) {
            this(type, null, imagePath, networkImageUrl, networkImageCachePolicy, paint, opacity);
        }

        /// Creates a resolved launcher background.
        ///
        /// @param type                    the launcher background source type
        /// @param builtinBackgroundId     the selected built-in wallpaper ID, or `null` when not using a built-in wallpaper
        /// @param imagePath               the resolved local image file or directory, or `null` when not using a local image
        /// @param networkImageUrl         the remote image URL, or `null` when not using a network image
        /// @param networkImageCachePolicy whether the remote image cache policy is explicitly overridden, or `null` for default behavior
        /// @param paint                   the resolved background paint, or `null` when not using a paint background
        /// @param opacity                 the background opacity clamped to `[0, 1]`
        public ResolvedBackground {
            Objects.requireNonNull(type);
            if (builtinBackgroundId != null) {
                builtinBackgroundId = builtinBackgroundId.trim();
                if (builtinBackgroundId.isEmpty()) {
                    throw new IllegalArgumentException("Resolved background built-in wallpaper ID is blank");
                }
            }
            opacity = Double.isFinite(opacity)
                    ? MathUtils.clamp(opacity, 0.0, 1.0)
                    : 1.0;
        }

        /// Returns a copy with a different opacity.
        public ResolvedBackground withOpacity(double opacity) {
            return new ResolvedBackground(
                    type,
                    builtinBackgroundId,
                    imagePath,
                    networkImageUrl,
                    networkImageCachePolicy,
                    paint,
                    opacity);
        }

        /// Returns a copy with a different network image cache policy.
        public ResolvedBackground withNetworkImageCachePolicy(@Nullable NetworkBackgroundImageCachePolicy networkImageCachePolicy) {
            return new ResolvedBackground(
                    type,
                    builtinBackgroundId,
                    imagePath,
                    networkImageUrl,
                    networkImageCachePolicy,
                    paint,
                    opacity);
        }
    }

    /// Loads and parses a theme-pack file or unpacked theme-pack directory.
    ///
    /// @param file the theme-pack file or directory
    /// @return the loaded theme pack
    /// @throws IOException if the file cannot be read or the manifest is invalid
    public static LoadedThemePack load(Path file) throws IOException {
        Objects.requireNonNull(file);

        Path normalizedFile = file.toAbsolutePath().normalize();
        try {
            if (Files.isDirectory(normalizedFile)) {
                Path manifestFile = normalizedFile.resolve(ThemePackExporter.MANIFEST_ENTRY);
                if (!Files.isRegularFile(manifestFile)) {
                    throw new IOException("Theme pack directory does not contain " + ThemePackExporter.MANIFEST_ENTRY);
                }

                ThemePackManifest manifest = JsonUtils.fromJsonFile(manifestFile, ThemePackManifest.class);
                if (manifest == null) {
                    throw new JsonParseException("Manifest is null");
                }
                return new LoadedThemePack(normalizedFile, manifest);
            }

            try (var reader = new ZipArchiveReader(normalizedFile)) {
                var manifestEntry = reader.getEntry(ThemePackExporter.MANIFEST_ENTRY);
                if (manifestEntry == null || manifestEntry.isDirectory()) {
                    throw new IOException("Theme pack does not contain " + ThemePackExporter.MANIFEST_ENTRY);
                }

                ThemePackManifest manifest;
                try (var inputStream = reader.getInputStream(manifestEntry)) {
                    manifest = JsonUtils.fromNonNullJsonFully(inputStream, ThemePackManifest.class);
                }
                return new LoadedThemePack(normalizedFile, manifest);
            }
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new IOException("Invalid theme-pack manifest", e);
        }
    }

    /// Loads built-in theme pack manifests from launcher resources.
    private static @Unmodifiable List<InstalledThemePack> loadBuiltinThemePacks() {
        ArrayList<InstalledThemePack> themePacks = new ArrayList<>();
        for (String id : BUILTIN_THEME_PACK_IDS) {
            try {
                ThemePackManifest manifest;

                try (InputStream input = ThemePackManager.class.getResourceAsStream(
                        "/assets/themes/" + id + "/" + ThemePackExporter.MANIFEST_ENTRY)) {
                    if (input == null) {
                        throw new IOException("Missing built-in theme-pack manifest: " + id);
                    }

                    manifest = JsonUtils.fromNonNullJsonFully(input, ThemePackManifest.class);
                }
                if (!manifest.id().equals(id)) {
                    throw new IOException("Built-in theme-pack id does not match resource directory: " + id);
                }
                themePacks.add(new InstalledThemePack(
                        builtinThemePackFile(manifest.id()),
                        manifest,
                        true));
            } catch (IOException | JsonParseException | IllegalArgumentException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        if (themePacks.isEmpty()) {
            throw new ExceptionInInitializerError("No built-in theme packs are declared");
        }
        return List.copyOf(themePacks);
    }

    /// Loads a manifest from an installed theme-pack file.
    ///
    /// @param file the installed theme-pack file
    /// @return the installed theme pack
    /// @throws IOException if the installed manifest cannot be read or parsed
    public static InstalledThemePack loadInstalled(Path file) throws IOException {
        LoadedThemePack loadedThemePack = load(file);
        return new InstalledThemePack(loadedThemePack.file(), loadedThemePack.manifest());
    }

    /// Loads the installed theme pack referenced by a theme reference.
    ///
    /// @param reference the referenced installed theme
    /// @return the installed theme pack, or `null` if the selected package file does not exist
    /// @throws IOException if the installed manifest cannot be read or parsed
    public static @Nullable InstalledThemePack findInstalled(ThemeReference reference) throws IOException {
        Objects.requireNonNull(reference);

        @Nullable InstalledThemePack themePack = findInstalled(THEME_PACKS_DIRECTORY, reference.packId());
        if (themePack != null) {
            return themePack;
        }

        themePack = findInstalled(USER_THEME_PACKS_DIRECTORY, reference.packId());
        if (themePack != null) {
            return themePack;
        }

        return findBuiltinThemePack(reference.packId());
    }

    /// Lists all installed theme packs.
    ///
    /// @return installed theme packs with built-in package IDs first and local packages overriding user packages
    /// @throws IOException if installed theme-pack files cannot be listed
    public static @Unmodifiable List<InstalledThemePack> listInstalled() throws IOException {
        LinkedHashMap<String, InstalledThemePack> highestPriorityThemePacks = new LinkedHashMap<>();
        for (InstalledThemePack themePack : BUILTIN_THEME_PACKS) {
            highestPriorityThemePacks.put(themePack.manifest().id(), themePack);
        }

        for (InstalledThemePack themePack : listInstalled(USER_THEME_PACKS_DIRECTORY)) {
            highestPriorityThemePacks.put(themePack.manifest().id(), themePack);
        }
        for (InstalledThemePack themePack : listInstalled(THEME_PACKS_DIRECTORY)) {
            highestPriorityThemePacks.put(themePack.manifest().id(), themePack);
        }

        ArrayList<InstalledThemePack> result = new ArrayList<>();
        for (InstalledThemePack themePack : BUILTIN_THEME_PACKS) {
            @Nullable InstalledThemePack selectedThemePack = highestPriorityThemePacks.remove(themePack.manifest().id());
            if (selectedThemePack != null) {
                result.add(selectedThemePack);
            }
        }

        ArrayList<InstalledThemePack> remainingThemePacks = new ArrayList<>(highestPriorityThemePacks.values());
        remainingThemePacks.sort(Comparator
                .comparing((InstalledThemePack themePack) -> themePack.manifest().displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(themePack -> themePack.manifest().id(), String.CASE_INSENSITIVE_ORDER));
        result.addAll(remainingThemePacks);
        return List.copyOf(result);
    }

    /// Installs a theme-pack file under the launcher's local theme-pack directory.
    ///
    /// Existing files for the same package ID are replaced.
    ///
    /// @param file the theme-pack file
    /// @return the installed theme pack
    /// @throws IOException if the theme pack cannot be validated or installed
    public static InstalledThemePack install(Path file) throws IOException {
        LoadedThemePack loadedThemePack = load(file);
        validateThemePackFile(loadedThemePack.file());

        Path targetFile = installedThemePackFile(loadedThemePack.manifest());
        Path temporaryFile = FileUtils.tmpSaveFile(targetFile);

        Files.createDirectories(Objects.requireNonNull(targetFile.getParent()));
        deleteIfExists(temporaryFile);
        deleteIfExists(installedThemePackCacheDirectory(loadedThemePack.manifest().id()));
        try {
            Files.copy(loadedThemePack.file(), temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temporaryFile, targetFile);
            return new InstalledThemePack(targetFile, loadedThemePack.manifest());
        } catch (IOException | RuntimeException e) {
            try {
                deleteIfExists(temporaryFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /// Removes an installed theme pack from the launcher's local theme-pack directory.
    ///
    /// If the removed package is currently selected, the stored theme reference is cleared.
    ///
    /// @param themePack the installed theme pack to remove
    /// @throws IOException if the installed package cannot be removed
    public static void uninstall(InstalledThemePack themePack) throws IOException {
        Objects.requireNonNull(themePack);

        if (themePack.builtin()) {
            throw new IOException("Cannot delete a built-in theme pack: " + themePack.manifest().id());
        }

        Path rootDirectory = THEME_PACKS_DIRECTORY.toAbsolutePath().normalize();
        Path targetFile = themePack.file().toAbsolutePath().normalize();
        if (!targetFile.startsWith(rootDirectory) || targetFile.equals(rootDirectory)) {
            throw new IOException("Theme-pack file is outside the managed directory: " + targetFile);
        }

        deleteIfExists(targetFile);
        deleteIfExists(installedThemePackCacheDirectory(themePack.manifest().id()));

        ThemeReference reference = settings().getThemeOrDefault();
        ThemePackManifest manifest = themePack.manifest();
        if (reference.packId().equals(manifest.id())) {
            @Nullable InstalledThemePack replacementThemePack = findInstalled(reference);
            @Nullable Theme replacementTheme = replacementThemePack == null
                    ? null
                    : replacementThemePack.manifest().findTheme(reference.themeId());
            if (replacementTheme == null) {
                settings().themeProperty().set(BUILTIN_DEFAULT_THEME_REFERENCE);
            }
        }
    }

    /// Applies one theme from a loaded theme pack to current launcher settings.
    ///
    /// @param themePack the loaded theme pack
    /// @param theme     the theme to apply
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
    /// @param theme     the theme to apply
    /// @throws IOException if referenced assets cannot be read or settings cannot be applied
    public static void apply(InstalledThemePack themePack, Theme theme) throws IOException {
        Objects.requireNonNull(themePack);
        Objects.requireNonNull(theme);

        apply(themePack.file(), themePack.manifest(), theme, currentResolveContext());
    }

    /// Applies one theme from an installed theme-pack file to current launcher settings.
    ///
    /// @param themePackFile the installed theme-pack file
    /// @param manifest      the parsed manifest
    /// @param theme         the theme to apply
    /// @param context       the condition resolution context
    /// @throws IOException if referenced assets cannot be read or settings cannot be applied
    public static void apply(
            Path themePackFile,
            ThemePackManifest manifest,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(themePackFile);
        Objects.requireNonNull(context);
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(theme);

        ThemeReference reference = new ThemeReference(manifest.id(), theme.id());
        LauncherSettings currentSettings = settings();
        currentSettings.getThemeAppearanceOverrides().clear();
        currentSettings.themeProperty().set(reference);
    }

    /// Exports the current launcher appearance to a theme-pack file.
    ///
    /// @param outputFile the target theme-pack file
    /// @param packId     the exported package identifier
    /// @param packName   the exported package display name
    /// @param authorName the exported package author name
    /// @throws IOException if the current appearance cannot be exported
    public static void exportCurrent(Path outputFile, String packId, String packName, String authorName) throws IOException {
        Objects.requireNonNull(outputFile);

        ExportedThemePack themePack = createCurrent(packId, packName, authorName);
        ThemePackExporter.export(themePack.manifest(), themePack.assets(), outputFile);
    }

    /// Creates an exportable theme pack from the current launcher appearance.
    ///
    /// @param packId     the exported package identifier
    /// @param packName   the exported package display name
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
                null,
                createCurrentBackground(assets),
                new ThemeTitleBar(currentTitleBarTransparent()));
        Theme theme = new Theme(
                null,
                null,
                List.of(),
                null,
                null,
                appearance,
                List.of());
        ThemePackManifest manifest = new ThemePackManifest(
                requireNonBlank(packId, "packId"),
                CURRENT_THEME_PACK_VERSION,
                LocalizedText.plain(packName),
                List.of(new ThemePackAuthor(LocalizedText.plain(requireNonBlank(authorName, "authorName")))),
                null,
                List.of(theme));

        return new ExportedThemePack(manifest, assets);
    }

    /// Returns the current launcher theme color source as a theme-pack directive.
    private static ThemeColorSource currentThemeColorSource() throws IOException {
        BackgroundType backgroundType = resolveCurrentBackground(currentResolveContext()).type();
        if (!SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_COLOR)) {
            @Nullable ThemeColorSource colorSource = resolveCurrentThemeColorSource(currentResolveContext());
            if (colorSource != null) {
                if (colorSource instanceof ThemeColorSource.Wallpaper && backgroundType == BackgroundType.THEME_COLOR) {
                    return ThemeColorSource.DEFAULT;
                }
                return colorSource;
            }
            return ThemeColorSource.DEFAULT;
        }

        ThemeColorType themeColorType = Objects.requireNonNullElse(
                settings().themeColorTypeProperty().get(),
                ThemeColorType.DEFAULT);
        if (themeColorType == ThemeColorType.DEFAULT) {
            return ThemeColorSource.DEFAULT;
        }
        if (themeColorType == ThemeColorType.BACKGROUND) {
            if (backgroundType == BackgroundType.THEME_COLOR) {
                return ThemeColorSource.DEFAULT;
            }
            return ThemeColorSource.wallpaper();
        }

        ThemeColor color = Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT);
        return ThemeColorSource.custom(color);
    }

    /// Returns the current launcher color style as a theme-pack directive.
    private static ColorStyle currentColorStyle() throws IOException {
        return SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_COLOR_STYLE)
                ? Objects.requireNonNullElse(settings().themeColorStyleProperty().get(), ResolvedTheme.DEFAULT.colorStyle())
                : resolveCurrentThemeColorStyle(currentResolveContext(), ResolvedTheme.DEFAULT.colorStyle());
    }

    /// Returns the current launcher brightness as an explicit theme-pack directive, or `null` for auto mode.
    private static @Nullable Brightness currentControlledBrightness() throws IOException {
        if (!SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BRIGHTNESS)) {
            return resolveCurrentThemeBrightness(currentResolveContext());
        }

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

    /// Returns whether the current title bar should be transparent.
    private static boolean currentTitleBarTransparent() throws IOException {
        return SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_TITLE_TRANSPARENT)
                ? settings().titleTransparentProperty().get()
                : resolveCurrentTitleBarTransparent(currentResolveContext(), false);
    }

    /// Resolves the current selected theme color source into a concrete launcher theme color.
    ///
    /// @param context        the condition resolution context
    /// @param fallback       the color used when no selected theme color source is available
    /// @param backgroundType the current launcher background type
    /// @return the resolved theme color
    /// @throws IOException if the selected theme pack exists but its referenced color assets cannot be read
    static ThemeColor resolveCurrentThemeColor(
            ThemeResolveContext context,
            ThemeColor fallback,
            BackgroundType backgroundType) throws IOException {
        Objects.requireNonNull(context);
        Objects.requireNonNull(fallback);
        Objects.requireNonNull(backgroundType);

        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        if (appearance == null) {
            return fallback;
        }
        @Nullable ThemeColorSource color = appearance.color();
        if (color == null) {
            return fallback;
        }
        if (color instanceof ThemeColorSource.Wallpaper && backgroundType == BackgroundType.THEME_COLOR) {
            return ThemeColor.DEFAULT;
        }
        ThemeReference reference = settings().getThemeOrDefault();
        @Nullable InstalledThemePack themePack = findInstalled(reference);
        return themePack != null ? resolveThemeColor(themePack.file(), appearance) : fallback;
    }

    /// Resolves the selected theme's controlled brightness.
    ///
    /// @param context the condition resolution context
    /// @return the selected theme brightness directive, or `null` when unavailable
    /// @throws IOException if the selected theme pack cannot be read
    public static @Nullable Brightness resolveCurrentThemeBrightness(ThemeResolveContext context) throws IOException {
        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        return appearance != null ? appearance.brightness() : null;
    }

    /// Resolves the selected theme brightness or returns the given fallback.
    ///
    /// @param context  the condition resolution context
    /// @param fallback the brightness used when the selected theme does not control brightness
    /// @return the effective brightness from the selected theme or fallback
    /// @throws IOException if the selected theme pack cannot be read
    static Brightness resolveCurrentThemeBrightness(ThemeResolveContext context, Brightness fallback) throws IOException {
        return Objects.requireNonNullElse(resolveCurrentThemeBrightness(context), fallback);
    }

    /// Resolves the selected theme color style or returns the given fallback.
    ///
    /// @param context  the condition resolution context
    /// @param fallback the color style used when the selected theme does not define one
    /// @return the effective color style from the selected theme or fallback
    /// @throws IOException if the selected theme pack cannot be read
    static ColorStyle resolveCurrentThemeColorStyle(ThemeResolveContext context, ColorStyle fallback) throws IOException {
        return Objects.requireNonNullElse(resolveCurrentThemeColorStyle(context), fallback);
    }

    /// Resolves the selected theme color style directive.
    ///
    /// @param context the condition resolution context
    /// @return the selected theme color style directive, or `null` when unavailable
    /// @throws IOException if the selected theme pack cannot be read
    public static @Nullable ColorStyle resolveCurrentThemeColorStyle(ThemeResolveContext context) throws IOException {
        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        return appearance != null ? appearance.colorStyle() : null;
    }

    /// Resolves the selected theme title-bar transparency or returns the given fallback.
    ///
    /// @param context  the condition resolution context
    /// @param fallback the value used when the selected theme does not define one
    /// @return the effective title-bar transparency from the selected theme or fallback
    /// @throws IOException if the selected theme pack cannot be read
    public static boolean resolveCurrentTitleBarTransparent(ThemeResolveContext context, boolean fallback) throws IOException {
        return Objects.requireNonNullElse(resolveCurrentTitleBarTransparent(context), fallback);
    }

    /// Resolves the selected theme title-bar transparency directive.
    ///
    /// @param context the condition resolution context
    /// @return the selected theme title-bar transparency directive, or `null` when unavailable
    /// @throws IOException if the selected theme pack cannot be read
    public static @Nullable Boolean resolveCurrentTitleBarTransparent(ThemeResolveContext context) throws IOException {
        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        return appearance != null && appearance.titleBar() != null ? appearance.titleBar().transparent() : null;
    }

    /// Resolves the current selected theme color source without extracting any wallpaper pixels.
    ///
    /// @param context the condition resolution context
    /// @return the selected theme color source, or `null` when unavailable
    /// @throws IOException if the selected theme pack manifest cannot be read
    public static @Nullable ThemeColorSource resolveCurrentThemeColorSource(ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(context);

        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        return appearance != null ? appearance.color() : null;
    }

    /// Resolves the selected theme background settings directive.
    ///
    /// @param context the condition resolution context
    /// @return the selected theme background settings directive, or `null` when unavailable
    /// @throws IOException if the selected theme pack cannot be read
    public static @Nullable ThemeBackgroundSettings resolveCurrentThemeBackgroundSettings(ThemeResolveContext context) throws IOException {
        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        return appearance != null ? appearance.background() : null;
    }

    /// Resolves the selected theme appearance for a condition context.
    private static @Nullable ThemeAppearance resolveCurrentThemeAppearance(ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(context);

        ThemeReference reference = settings().getThemeOrDefault();

        @Nullable InstalledThemePack themePack = findInstalled(reference);
        if (themePack == null) {
            return null;
        }

        @Nullable Theme theme = themePack.manifest().findTheme(reference.themeId());
        if (theme == null) {
            return null;
        }

        return theme.resolve(context);
    }

    /// Returns the current condition resolution context.
    ///
    /// @return the current resolution context
    public static ThemeResolveContext currentResolveContext() {
        return ThemeResolveContext.current(Themes.getThemeConditionBrightness());
    }

    /// Resolves the background currently selected by launcher settings.
    ///
    /// When the launcher background setting is inherited, this resolves the selected launcher theme background first.
    /// Otherwise it resolves the direct launcher background fields.
    ///
    /// @param context the condition resolution context
    /// @return the currently effective launcher background
    /// @throws IOException if the referenced theme pack exists but its background cannot be resolved
    public static ResolvedBackground resolveCurrentBackground(ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(context);

        if (!SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND)) {
            ThemeReference reference = settings().getThemeOrDefault();
            @Nullable ResolvedBackground resolved = resolveThemeBackground(reference, context);
            if (resolved != null) {
                return resolved;
            }
            return new ResolvedBackground(
                    BackgroundType.DEFAULT,
                    null,
                    null,
                    null,
                    null,
                    currentBackgroundOpacityOrDefault());
        }

        return resolveCustomBackground();
    }

    /// Resolves the fallback background used when the current launcher background cannot be loaded.
    ///
    /// @param context the condition resolution context
    /// @return the effective fallback background
    /// @throws IOException if the selected theme fallback exists but cannot be resolved
    public static ResolvedBackground resolveCurrentBackgroundFallback(ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(context);

        if (!SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK)) {
            ThemeReference reference = settings().getThemeOrDefault();
            @Nullable InstalledThemePack themePack = findInstalled(reference);
            @Nullable ThemeBackgroundSettings background = null;
            if (themePack != null) {
                @Nullable Theme theme = themePack.manifest().findTheme(reference.themeId());
                if (theme != null) {
                    background = theme.resolve(context).background();
                }
            }
            double opacity = SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY)
                    ? currentBackgroundOpacity()
                    : background != null && background.opacity() != null ? background.opacity() : 1.0;
            if (themePack != null && background != null && background.fallback() != null) {
                return resolveBackground(
                        themePack.file(),
                        new ThemeBackgroundSettings(background.fallback(), null),
                        opacity);
            }
            return new ResolvedBackground(
                    BackgroundType.BUILTIN,
                    BuiltinBackground.FALLBACK.id(),
                    null,
                    null,
                    null,
                    null,
                    opacity);
        }

        BackgroundType fallbackType = Objects.requireNonNullElse(
                settings().backgroundFallbackTypeProperty().get(),
                BackgroundType.BUILTIN);
        double opacity = currentBackgroundOpacityOrDefault();
        return switch (fallbackType) {
            case BUILTIN -> new ResolvedBackground(
                    BackgroundType.BUILTIN,
                    BuiltinBackground.FALLBACK.id(),
                    null,
                    null,
                    null,
                    null,
                    opacity);
            case PAINT -> new ResolvedBackground(
                    BackgroundType.PAINT,
                    null,
                    null,
                    null,
                    settings().backgroundFallbackPaintProperty().get(),
                    opacity);
            case THEME_COLOR -> new ResolvedBackground(
                    BackgroundType.THEME_COLOR,
                    null,
                    null,
                    null,
                    getThemeColorBackgroundPaint(),
                    opacity);
            case CUSTOM, NETWORK, DEFAULT -> new ResolvedBackground(
                    BackgroundType.BUILTIN,
                    BuiltinBackground.FALLBACK.id(),
                    null,
                    null,
                    null,
                    null,
                    opacity);
        };
    }

    /// Resolves the background loading policy used by current launcher settings.
    ///
    /// @param context the condition resolution context
    /// @return the effective background loading policy
    /// @throws IOException if the selected theme pack cannot be read
    public static BackgroundLoadPolicy resolveCurrentBackgroundLoadPolicy(ThemeResolveContext context) throws IOException {
        if (SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_LOAD_POLICY)) {
            return Objects.requireNonNullElse(
                    settings().backgroundLoadPolicyProperty().get(),
                    BackgroundLoadPolicy.WAIT_FOR_BACKGROUND);
        }

        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        @Nullable ThemeBackgroundSettings background = appearance != null ? appearance.background() : null;
        return background != null && background.loadPolicy() != null
                ? background.loadPolicy()
                : BackgroundLoadPolicy.WAIT_FOR_BACKGROUND;
    }

    /// Resolves the launcher custom background fields without considering theme-pack background references.
    ///
    /// @return the custom launcher background settings
    public static ResolvedBackground resolveCustomBackground() {
        BackgroundType type = Objects.requireNonNullElse(settings().backgroundTypeProperty().get(), BackgroundType.DEFAULT);
        double opacity = currentBackgroundOpacityOrDefault();
        return switch (type) {
            case DEFAULT -> new ResolvedBackground(
                    BackgroundType.DEFAULT,
                    null,
                    null,
                    null,
                    null,
                    opacity);
            case BUILTIN -> new ResolvedBackground(
                    BackgroundType.BUILTIN,
                    currentBuiltinBackgroundId(),
                    null,
                    null,
                    null,
                    null,
                    opacity);
            case CUSTOM -> {
                @Nullable String customBackgroundImagePath = settings().customBackgroundImagePathProperty().get();
                @Nullable Path imagePath = StringUtils.isBlank(customBackgroundImagePath)
                        ? null
                        : Path.of(customBackgroundImagePath).toAbsolutePath().normalize();
                yield new ResolvedBackground(
                        BackgroundType.CUSTOM,
                        imagePath,
                        null,
                        null,
                        null,
                        opacity);
            }
            case NETWORK -> {
                @Nullable String networkBackgroundImageUrl = settings().networkBackgroundImageUrlProperty().get();
                yield new ResolvedBackground(
                        BackgroundType.NETWORK,
                        null,
                        StringUtils.isBlank(networkBackgroundImageUrl) ? null : networkBackgroundImageUrl.trim(),
                        currentNetworkBackgroundImageCachePolicy(),
                        null,
                        opacity);
            }
            case PAINT -> new ResolvedBackground(
                    BackgroundType.PAINT,
                    null,
                    null,
                    null,
                    settings().customBackgroundPaintProperty().get(),
                    opacity);
            case THEME_COLOR -> new ResolvedBackground(
                    BackgroundType.THEME_COLOR,
                    null,
                    null,
                    null,
                    getThemeColorBackgroundPaint(),
                    opacity);
        };
    }

    /// Resolves the background contributed by a selected installed theme.
    ///
    /// @param reference the selected theme reference
    /// @param context   the condition resolution context
    /// @return the resolved background, or `null` when the theme or background is not available
    /// @throws IOException if the installed theme pack exists but its background cannot be resolved
    public static @Nullable ResolvedBackground resolveThemeBackground(
            ThemeReference reference,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(context);

        @Nullable InstalledThemePack themePack = findInstalled(reference);
        if (themePack == null) {
            return null;
        }

        @Nullable Theme theme = themePack.manifest().findTheme(reference.themeId());
        if (theme == null) {
            return null;
        }

        @Nullable ResolvedBackground resolved = resolveThemeBackground(themePack.file(), theme, context);
        if (resolved == null) {
            return null;
        }
        if (SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY)) {
            resolved = resolved.withOpacity(currentBackgroundOpacity());
        }
        if (resolved.type() == BackgroundType.NETWORK && SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_NETWORK_BACKGROUND_IMAGE_CACHE_POLICY)) {
            resolved = resolved.withNetworkImageCachePolicy(currentNetworkBackgroundImageCachePolicy());
        }
        return resolved;
    }

    /// Resolves the background contributed by one theme without applying launcher appearance overrides.
    ///
    /// @param themePackFile the installed theme-pack file
    /// @param theme         the theme to resolve
    /// @param context       the condition resolution context
    /// @return the resolved background, or `null` when the theme does not define one
    /// @throws IOException if the theme background cannot be resolved
    public static @Nullable ResolvedBackground resolveThemeBackground(
            Path themePackFile,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(themePackFile);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(context);

        ThemeAppearance appearance = theme.resolve(context);
        @Nullable ThemeBackgroundSettings background = appearance.background();
        if (background == null) {
            return null;
        }
        return resolveBackground(themePackFile, background, 1.0);
    }

    /// Resolves a concrete launcher background from a theme-pack background object.
    private static ResolvedBackground resolveBackground(
            Path themePackFile,
            ThemeBackgroundSettings background,
            double fallbackOpacity) throws IOException {
        double opacity = Objects.requireNonNullElse(background.opacity(), fallbackOpacity);
        @Nullable ThemeBackground source = background.source();
        if (source instanceof ThemeBackground.Default) {
            return new ResolvedBackground(
                    BackgroundType.DEFAULT,
                    null,
                    null,
                    null,
                    null,
                    opacity);
        }
        if (source instanceof ThemeBackground.Builtin builtin) {
            return new ResolvedBackground(
                    BackgroundType.BUILTIN,
                    resolveBuiltinBackgroundId(builtin.id()),
                    null,
                    null,
                    null,
                    null,
                    opacity);
        }
        if (source instanceof ThemeBackground.Image image) {
            return new ResolvedBackground(
                    BackgroundType.CUSTOM,
                    resolveInstalledAsset(themePackFile, requireNonBlank(image.path(), "background.path")),
                    null,
                    null,
                    null,
                    opacity);
        }
        if (source instanceof ThemeBackground.Network network) {
            return new ResolvedBackground(
                    BackgroundType.NETWORK,
                    null,
                    requireNonBlank(network.url(), "background.url"),
                    network.cache(),
                    null,
                    opacity);
        }
        if (source instanceof ThemeBackground.ThemeColor) {
            return new ResolvedBackground(
                    BackgroundType.THEME_COLOR,
                    null,
                    null,
                    null,
                    getThemeColorBackgroundPaint(),
                    opacity);
        }
        if (source instanceof ThemeBackground.Paint paint) {
            return new ResolvedBackground(
                    BackgroundType.PAINT,
                    null,
                    null,
                    null,
                    parsePaint(requireNonBlank(paint.paint(), "background.paint")),
                    opacity);
        }
        return new ResolvedBackground(
                BackgroundType.DEFAULT,
                null,
                null,
                null,
                null,
                opacity);
    }

    /// Resolves a concrete launcher color from a theme appearance.
    private static ThemeColor resolveThemeColor(Path themePackFile, ThemeAppearance appearance) throws IOException {
        ThemeColorSource color = Objects.requireNonNull(appearance.color());
        if (color instanceof ThemeColorSource.Custom || color instanceof ThemeColorSource.Default) {
            return color.resolveFallback();
        }

        ThemeColor fallback = color.resolveFallback();
        ThemeBackgroundSettings background = appearance.background();
        if (background == null) {
            return fallback;
        }

        @Nullable ThemeBackground source = background.source();
        if (source instanceof ThemeBackground.Image image) {
            String path = requireNonBlank(image.path(), "background.path");
            return WallpaperColorExtractor.extract(resolveInstalledAsset(themePackFile, path), fallback);
        }
        if (source instanceof ThemeBackground.Paint paintBackground) {
            Paint paint = parsePaint(requireNonBlank(paintBackground.paint(), "background.paint"));
            return paint instanceof Color paintColor ? ThemeColor.of(paintColor) : fallback;
        }
        return fallback;
    }

    /// Resolves one asset referenced by an installed theme.
    static Path resolveInstalledAsset(Path themePackFile, String entryName) throws IOException {
        String normalizedEntryName = ThemePackAsset.normalizeEntryName(entryName);
        Path installedFile = themePackFile.toAbsolutePath().normalize();
        @Nullable InstalledThemePack builtinThemePack = findBuiltinThemePack(installedFile);
        if (builtinThemePack != null) {
            return resolveBuiltinAsset(builtinThemePack, normalizedEntryName);
        }
        if (Files.isDirectory(installedFile)) {
            Path assetFile = installedFile.resolve(normalizedEntryName).normalize();
            if (!assetFile.startsWith(installedFile)) {
                throw new IOException("Theme-pack asset escapes the installed directory: " + normalizedEntryName);
            }
            if (!Files.isRegularFile(assetFile)) {
                throw new IOException("Installed theme-pack asset is missing: " + normalizedEntryName);
            }
            return assetFile;
        }
        if (!Files.isRegularFile(installedFile)) {
            throw new IOException("Installed theme-pack file is missing: " + installedFile);
        }

        Path cacheFile = cachedInstalledAssetFile(installedFile, normalizedEntryName);
        try (ZipFile zipFile = new ZipFile(installedFile.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(normalizedEntryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Installed theme-pack asset is missing: " + normalizedEntryName);
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                copyInputStreamToCache(input, cacheFile);
            }
        }
        return cacheFile;
    }

    /// Resolves one asset stored in a bundled theme pack.
    private static Path resolveBuiltinAsset(InstalledThemePack themePack, String entryName) throws IOException {
        Path cacheFile = cachedInstalledAssetFile(themePack.file(), entryName);
        String id = requirePackageId(themePack.manifest().id());
        String resourcePath = "/assets/themes/" + id + "/" + entryName;
        try (InputStream input = ThemePackManager.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Built-in theme-pack asset is missing: " + entryName);
            }
            copyInputStreamToCache(input, cacheFile);
        }
        return cacheFile;
    }

    /// Returns the installed theme-pack file for one manifest.
    private static Path installedThemePackFile(ThemePackManifest manifest) {
        return installedThemePackFile(manifest.id());
    }

    /// Returns the installed theme-pack file for one package ID.
    static Path installedThemePackFile(String packId) {
        return installedThemePackFile(THEME_PACKS_DIRECTORY, packId);
    }

    /// Returns the installed theme-pack file under a theme-pack directory for one package ID.
    private static Path installedThemePackFile(Path themePacksDirectory, String packId) {
        String id = requirePackageId(packId);
        return themePacksDirectory
                .resolve(id + ThemePackExporter.FILE_EXTENSION)
                .toAbsolutePath()
                .normalize();
    }

    /// Returns the synthetic local file path used to identify one bundled theme pack.
    private static Path builtinThemePackFile(String packId) {
        String id = requirePackageId(packId);
        return THEME_PACKS_DIRECTORY
                .resolve(CACHE_DIRECTORY)
                .resolve("builtin")
                .resolve(id + ThemePackExporter.FILE_EXTENSION)
                .toAbsolutePath()
                .normalize();
    }

    /// Returns the cache directory used for assets extracted from one installed theme pack.
    private static Path installedThemePackCacheDirectory(String packId) {
        String id = requirePackageId(packId);
        return THEME_PACKS_DIRECTORY
                .resolve(CACHE_DIRECTORY)
                .resolve(id)
                .toAbsolutePath()
                .normalize();
    }

    /// Returns the cache file used for one installed asset.
    private static Path cachedInstalledAssetFile(Path installedFile, String entryName) throws IOException {
        String fileName = installedFile.getFileName().toString();
        if (!fileName.endsWith(ThemePackExporter.FILE_EXTENSION)) {
            throw new IOException("Installed theme-pack file has an unsupported extension: " + installedFile);
        }

        String packId = fileName.substring(0, fileName.length() - ThemePackExporter.FILE_EXTENSION.length());
        Path cacheDirectory = installedThemePackCacheDirectory(packId);
        Path cacheFile = cacheDirectory.resolve(entryName).normalize();
        if (!cacheFile.startsWith(cacheDirectory)) {
            throw new IOException("Theme-pack asset escapes the cache directory: " + entryName);
        }
        return cacheFile;
    }

    /// Copies one input stream into the installed-asset cache.
    private static void copyInputStreamToCache(InputStream input, Path cacheFile) throws IOException {
        Files.createDirectories(Objects.requireNonNull(cacheFile.getParent()));
        Path temporaryFile = FileUtils.tmpSaveFile(cacheFile);
        deleteIfExists(temporaryFile);
        try {
            Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temporaryFile, cacheFile);
        } catch (IOException | RuntimeException e) {
            try {
                deleteIfExists(temporaryFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /// Returns whether a path is an installed theme-pack file candidate.
    private static boolean isInstalledThemePackFile(Path path) {
        String fileName = path.getFileName().toString();
        return !fileName.startsWith(".")
                && fileName.endsWith(ThemePackExporter.FILE_EXTENSION);
    }

    /// Returns whether a path is an unpacked installed theme-pack directory candidate.
    private static boolean isInstalledThemePackDirectory(Path path) {
        String fileName = path.getFileName().toString();
        return !fileName.startsWith(".")
                && Files.isDirectory(path)
                && Files.isRegularFile(path.resolve(ThemePackExporter.MANIFEST_ENTRY));
    }

    /// Returns whether a path is an installed theme-pack candidate.
    private static boolean isInstalledThemePackPath(Path path) {
        return Files.isRegularFile(path)
                ? isInstalledThemePackFile(path)
                : isInstalledThemePackDirectory(path);
    }

    /// Lists theme packs from one filesystem theme-pack directory.
    private static @Unmodifiable List<InstalledThemePack> listInstalled(Path themePacksDirectory) throws IOException {
        if (!Files.isDirectory(themePacksDirectory)) {
            return List.of();
        }

        ArrayList<InstalledThemePack> themePacks = new ArrayList<>();
        try (Stream<Path> themePackFiles = Files.list(themePacksDirectory)) {
            for (Path themePackFile : themePackFiles
                    .filter(ThemePackManager::isInstalledThemePackPath)
                    .toList()) {
                try {
                    themePacks.add(loadInstalled(themePackFile));
                } catch (IOException | RuntimeException e) {
                    LOG.warning("Failed to load installed theme pack: " + themePackFile, e);
                }
            }
        }

        themePacks.sort(Comparator
                .comparing((InstalledThemePack themePack) -> themePack.manifest().displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(themePack -> themePack.manifest().id(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(themePacks);
    }

    /// Finds a theme pack by package ID from one filesystem theme-pack directory.
    private static @Nullable InstalledThemePack findInstalled(Path themePacksDirectory, String packId) throws IOException {
        Path file = installedThemePackFile(themePacksDirectory, packId);
        if (Files.isRegularFile(file)) {
            return loadInstalled(file);
        }
        return findInstalledDirectory(themePacksDirectory, packId);
    }

    /// Finds an installed unpacked theme-pack directory by package ID.
    private static @Nullable InstalledThemePack findInstalledDirectory(Path themePacksDirectory, String packId) throws IOException {
        if (!Files.isDirectory(themePacksDirectory)) {
            return null;
        }

        try (Stream<Path> themePackDirectories = Files.list(themePacksDirectory)) {
            for (Path themePackDirectory : themePackDirectories
                    .filter(ThemePackManager::isInstalledThemePackDirectory)
                    .toList()) {
                try {
                    InstalledThemePack themePack = loadInstalled(themePackDirectory);
                    if (themePack.manifest().id().equals(packId)) {
                        return themePack;
                    }
                } catch (IOException | RuntimeException e) {
                    LOG.warning("Failed to load installed theme-pack directory: " + themePackDirectory, e);
                }
            }
        }
        return null;
    }

    /// Finds a bundled theme pack by package ID.
    private static @Nullable InstalledThemePack findBuiltinThemePack(String packId) {
        for (InstalledThemePack themePack : BUILTIN_THEME_PACKS) {
            if (themePack.manifest().id().equals(packId)) {
                return themePack;
            }
        }
        return null;
    }

    /// Finds a bundled theme pack by its synthetic file path.
    private static @Nullable InstalledThemePack findBuiltinThemePack(Path themePackFile) {
        Path normalizedFile = themePackFile.toAbsolutePath().normalize();
        for (InstalledThemePack themePack : BUILTIN_THEME_PACKS) {
            if (themePack.file().equals(normalizedFile)) {
                return themePack;
            }
        }
        return null;
    }

    /// Validates all zip entries in a theme-pack file.
    private static void validateThemePackFile(Path themePackFile) throws IOException {
        Set<String> entries = new HashSet<>();
        boolean hasManifest = false;

        try (ZipFile zipFile = new ZipFile(themePackFile.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String entryName = normalizeThemePackEntryName(entry.getName());
                checkSupportedThemePackEntry(entryName);

                if (!entries.add(entryName)) {
                    throw new IOException("Duplicate theme-pack entry: " + entryName);
                }
                if (ThemePackExporter.MANIFEST_ENTRY.equals(entryName) && !entry.isDirectory()) {
                    hasManifest = true;
                }
            }
        }

        if (!hasManifest) {
            throw new IOException("Theme pack does not contain " + ThemePackExporter.MANIFEST_ENTRY);
        }
    }

    /// Moves a file into place, using an atomic move when the platform supports it.
    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
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
    private static ThemeBackgroundSettings createCurrentBackground(List<ThemePackAsset> assets) throws IOException {
        ResolvedBackground background = resolveCurrentBackground(currentResolveContext());
        Double opacity = background.opacity();
        ThemeBackgroundSettings backgroundSettings = switch (background.type()) {
            case DEFAULT -> new ThemeBackgroundSettings(
                    new ThemeBackground.Default(),
                    opacity);
            case BUILTIN -> new ThemeBackgroundSettings(
                    new ThemeBackground.Builtin(currentBuiltinBackgroundId()),
                    opacity);
            case CUSTOM -> new ThemeBackgroundSettings(
                    createCurrentImageBackgroundSource(assets, background.imagePath()),
                    opacity);
            case NETWORK -> new ThemeBackgroundSettings(
                    new ThemeBackground.Network(
                            requireNonBlank(background.networkImageUrl(), "networkBackgroundImageUrl"),
                            background.networkImageCachePolicy() == NetworkBackgroundImageCachePolicy.ENABLED
                                    ? null
                                    : background.networkImageCachePolicy()),
                    opacity);
            case PAINT, THEME_COLOR -> new ThemeBackgroundSettings(
                    new ThemeBackground.Paint(Objects.requireNonNullElse(background.paint(), Color.WHITE).toString()),
                    opacity);
        };
        if (background.type() != BackgroundType.NETWORK) {
            return backgroundSettings;
        }
        return new ThemeBackgroundSettings(
                backgroundSettings.source(),
                backgroundSettings.opacity(),
                createCurrentBackgroundFallback(assets),
                resolveCurrentBackgroundLoadPolicy(currentResolveContext()));
    }

    /// Creates the fallback background model for the current launcher settings.
    private static ThemeBackground createCurrentBackgroundFallback(List<ThemePackAsset> assets) throws IOException {
        ResolvedBackground fallback = resolveCurrentBackgroundFallback(currentResolveContext());
        return switch (fallback.type()) {
            case BUILTIN, DEFAULT -> new ThemeBackground.Builtin(
                    Objects.requireNonNullElse(
                            fallback.builtinBackgroundId(),
                            BuiltinBackground.FALLBACK.id()));
            case CUSTOM -> createCurrentImageBackgroundSource(assets, fallback.imagePath());
            case NETWORK -> new ThemeBackground.Network(
                    requireNonBlank(fallback.networkImageUrl(), "networkBackgroundImageUrl"),
                    fallback.networkImageCachePolicy() == NetworkBackgroundImageCachePolicy.ENABLED
                            ? null
                            : fallback.networkImageCachePolicy());
            case PAINT -> new ThemeBackground.Paint(
                    Objects.requireNonNullElse(fallback.paint(), Color.WHITE).toString());
            case THEME_COLOR -> new ThemeBackground.ThemeColor();
        };
    }

    /// Returns the flat paint used by the theme-color background option.
    private static Color getThemeColorBackgroundPaint() {
        return Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER);
    }

    /// Creates the image background source for a local background file.
    private static ThemeBackground.Image createCurrentImageBackgroundSource(
            List<ThemePackAsset> assets,
            @Nullable Path imagePath) throws IOException {
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

        String entryName = "assets/wallpapers/" + sanitizePathSegment(source.getFileName().toString());
        assets.add(new ThemePackAsset(source, entryName));
        return new ThemeBackground.Image(entryName);
    }

    /// Returns the current launcher background opacity.
    private static double currentBackgroundOpacity() {
        double opacity = settings().backgroundOpacityProperty().get();
        if (!Double.isFinite(opacity)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, opacity));
    }

    /// Returns the effective custom launcher background opacity, or the built-in default when not overridden.
    private static double currentBackgroundOpacityOrDefault() {
        return SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY)
                ? currentBackgroundOpacity()
                : 1.0;
    }

    /// Returns the direct network background cache policy.
    private static NetworkBackgroundImageCachePolicy currentNetworkBackgroundImageCachePolicy() {
        return Objects.requireNonNullElse(
                settings().networkBackgroundImageCachePolicyProperty().get(),
                NetworkBackgroundImageCachePolicy.ENABLED);
    }

    /// Returns the currently selected built-in wallpaper ID.
    private static String currentBuiltinBackgroundId() {
        return BuiltinBackground.fromIdOrFallback(settings().builtinBackgroundIdProperty().get()).id();
    }

    /// Resolves a theme-pack built-in wallpaper ID.
    private static String resolveBuiltinBackgroundId(@Nullable String id) throws IOException {
        String normalizedId = StringUtils.isBlank(id)
                ? BuiltinBackground.FALLBACK.id()
                : id.trim().toLowerCase(Locale.ROOT);
        if (BuiltinBackground.fromId(normalizedId) != null) {
            return normalizedId;
        }
        throw new IOException("Theme packs cannot reference built-in wallpaper: " + normalizedId);
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

    /// Returns a package ID that can be used directly as an installed theme-pack file name.
    private static String requirePackageId(String packId) {
        return ThemePackManifest.requirePackageId(packId);
    }

    /// Sanitizes one path segment used for exported asset files.
    private static String sanitizePathSegment(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "_";
        }
        return sanitized;
    }
}
