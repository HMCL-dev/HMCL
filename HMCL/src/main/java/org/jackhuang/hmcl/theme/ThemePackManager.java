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

    /// Identifies where an installed theme pack is stored.
    public sealed interface ThemePackLocation permits ThemePackLocation.Local, ThemePackLocation.Builtin {
        /// Returns whether this location points to launcher-bundled resources.
        ///
        /// @return `true` for built-in theme packs
        boolean builtin();

        /// Returns the backing filesystem path for local theme packs.
        ///
        /// @return the backing file or directory, or `null` for non-filesystem locations
        @Nullable Path file();

        /// A theme pack stored as a filesystem file or directory.
        ///
        /// @param file the backing file or directory
        @NotNullByDefault
        record Local(Path file) implements ThemePackLocation {
            /// Creates a local theme-pack location.
            ///
            /// @param file the backing file or directory
            public Local {
                file = Objects.requireNonNull(file).toAbsolutePath().normalize();
            }

            /// Returns whether this location points to launcher-bundled resources.
            @Override
            public boolean builtin() {
                return false;
            }
        }

        /// A theme pack bundled in launcher classpath resources.
        ///
        /// @param id the built-in theme-pack ID
        @NotNullByDefault
        record Builtin(String id) implements ThemePackLocation {
            /// Creates a built-in theme-pack location.
            ///
            /// @param id the built-in theme-pack ID
            public Builtin {
                id = ThemePackManifest.requirePackageId(id);
            }

            /// Returns whether this location points to launcher-bundled resources.
            @Override
            public boolean builtin() {
                return true;
            }

            /// Returns the backing filesystem path for local theme packs.
            @Override
            public @Nullable Path file() {
                return null;
            }
        }
    }

    /// A theme pack available to the launcher.
    ///
    /// @param location where this theme pack is stored
    /// @param manifest the parsed manifest
    public record InstalledThemePack(ThemePackLocation location, ThemePackManifest manifest) {
        /// Creates an installed user theme-pack descriptor.
        ///
        /// @param file     the installed theme-pack file
        /// @param manifest the parsed manifest
        public InstalledThemePack(Path file, ThemePackManifest manifest) {
            this(new ThemePackLocation.Local(file), manifest);
        }

        /// Creates an available theme-pack descriptor.
        ///
        /// @param location where this theme pack is stored
        /// @param manifest the parsed manifest
        public InstalledThemePack {
            Objects.requireNonNull(location);
            Objects.requireNonNull(manifest);
        }

        /// Returns whether this package is bundled with the launcher resources.
        ///
        /// @return `true` for built-in theme packs
        public boolean builtin() {
            return location.builtin();
        }

        /// Returns the backing filesystem path for local theme packs.
        ///
        /// @return the backing file or directory, or `null` for built-in theme packs
        public @Nullable Path file() {
            return location.file();
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
    /// @param imageResource           the resolved theme-pack image resource, or `null` when not using a theme-pack image
    /// @param networkImageUrl         the remote image URL, or `null` when not using a network image
    /// @param networkImageCachePolicy whether the remote image cache policy is explicitly overridden, or `null` for default behavior
    /// @param paint                   the resolved background paint, or `null` when not using a paint background
    /// @param opacity                 the background opacity clamped to `[0, 1]`
    public record ResolvedBackground(
            BackgroundType type,
            @Nullable String builtinBackgroundId,
            @Nullable Path imagePath,
            @Nullable ThemePackResource imageResource,
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
            this(type, null, imagePath, null, networkImageUrl, networkImageCachePolicy, paint, opacity);
        }

        /// Creates a resolved launcher background with a theme-pack image resource.
        ///
        /// @param type          the launcher background source type
        /// @param imagePath     the resolved local image file, or `null` when the resource is not a direct file
        /// @param imageResource the resolved theme-pack image resource
        /// @param opacity       the background opacity clamped to `[0, 1]`
        public ResolvedBackground(
                BackgroundType type,
                @Nullable Path imagePath,
                ThemePackResource imageResource,
                double opacity) {
            this(type, null, imagePath, imageResource, null, null, null, opacity);
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
        public ResolvedBackground(
                BackgroundType type,
                @Nullable String builtinBackgroundId,
                @Nullable Path imagePath,
                @Nullable String networkImageUrl,
                @Nullable NetworkBackgroundImageCachePolicy networkImageCachePolicy,
                @Nullable Paint paint,
                double opacity) {
            this(type, builtinBackgroundId, imagePath, null, networkImageUrl, networkImageCachePolicy, paint, opacity);
        }

        /// Creates a resolved launcher background.
        ///
        /// @param type                    the launcher background source type
        /// @param builtinBackgroundId     the selected built-in wallpaper ID, or `null` when not using a built-in wallpaper
        /// @param imagePath               the resolved local image file or directory, or `null` when not using a local image
        /// @param imageResource           the resolved theme-pack image resource, or `null` when not using a theme-pack image
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
                    imageResource,
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
                        new ThemePackLocation.Builtin(manifest.id()),
                        manifest));
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

        Path targetFile = installedThemePackFile(THEME_PACKS_DIRECTORY, loadedThemePack.manifest().id());
        Path temporaryFile = FileUtils.tmpSaveFile(targetFile);

        Files.createDirectories(Objects.requireNonNull(targetFile.getParent()));
        deleteIfExists(temporaryFile);
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

    /// Removes an installed theme pack from a managed theme-pack directory.
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

        @Nullable Path file = themePack.file();
        if (file == null) {
            throw new IOException("Theme pack does not have a local file: " + themePack.manifest().id());
        }

        Path targetFile = file.toAbsolutePath().normalize();
        Path localDirectory = THEME_PACKS_DIRECTORY.toAbsolutePath().normalize();
        Path userDirectory = USER_THEME_PACKS_DIRECTORY.toAbsolutePath().normalize();
        boolean localThemePack = targetFile.startsWith(localDirectory) && !targetFile.equals(localDirectory);
        boolean userThemePack = targetFile.startsWith(userDirectory) && !targetFile.equals(userDirectory);
        if (!localThemePack && !userThemePack) {
            throw new IOException("Theme-pack file is outside the managed directory: " + targetFile);
        }

        deleteIfExists(targetFile);

        ThemeReference reference = settings().getSelectedThemeOrDefault();
        ThemePackManifest manifest = themePack.manifest();
        if (reference.packId().equals(manifest.id())) {
            @Nullable InstalledThemePack replacementThemePack = findInstalled(reference);
            @Nullable Theme replacementTheme = replacementThemePack == null
                    ? null
                    : replacementThemePack.manifest().findTheme(reference.themeId());
            if (replacementTheme == null) {
                settings().selectedThemeProperty().set(BUILTIN_DEFAULT_THEME_REFERENCE);
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

        apply(themePack.manifest(), theme);
    }

    /// Applies one theme to current launcher settings.
    ///
    /// @param manifest the parsed manifest
    /// @param theme    the theme to apply
    /// @throws IOException if referenced assets cannot be read or settings cannot be applied
    private static void apply(ThemePackManifest manifest, Theme theme) throws IOException {
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(theme);

        ThemeReference reference = new ThemeReference(manifest.id(), theme.id());
        LauncherSettings currentSettings = settings();
        currentSettings.getThemeAppearanceOverrides().clear();
        currentSettings.selectedThemeProperty().set(reference);
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
        packId = ThemePackManifest.requirePackageId(packId);
        packName = requireNonBlank(packName, "packName");

        List<ThemePackAsset> assets = new ArrayList<>();
        ThemeBackgroundSettings background = createCurrentBackground(assets);
        ThemeAppearance appearance = new ThemeAppearance(
                currentThemeColorSource(),
                currentControlledBrightness(),
                currentColorStyle(),
                null,
                background,
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
                packId,
                CURRENT_THEME_PACK_VERSION,
                LocalizedText.plain(packName),
                List.of(new ThemePackAuthor(LocalizedText.plain(requireNonBlank(authorName, "authorName")))),
                background.source() instanceof ThemeBackground.Image image ? image.path() : null,
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
                : Objects.requireNonNullElse(
                        resolveCurrentThemeColorStyle(currentResolveContext()),
                        ResolvedTheme.DEFAULT.colorStyle());
    }

    /// Returns the current launcher brightness as an explicit theme-pack directive, or `null` for auto mode.
    private static @Nullable Brightness currentControlledBrightness() throws IOException {
        if (!SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE)) {
            return resolveCurrentThemeBrightness(currentResolveContext());
        }

        String brightness = settings().themeBrightnessModeProperty().get();
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
        return SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_TITLE_BAR_TRANSPARENT)
                ? settings().titleBarTransparentProperty().get()
                : Objects.requireNonNullElse(resolveCurrentTitleBarTransparent(currentResolveContext()), false);
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

    /// Resolves the selected theme color style directive.
    ///
    /// @param context the condition resolution context
    /// @return the selected theme color style directive, or `null` when unavailable
    /// @throws IOException if the selected theme pack cannot be read
    public static @Nullable ColorStyle resolveCurrentThemeColorStyle(ThemeResolveContext context) throws IOException {
        @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
        return appearance != null ? appearance.colorStyle() : null;
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

    /// Resolves the selected theme appearance for a condition context.
    private static @Nullable ThemeAppearance resolveCurrentThemeAppearance(ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(context);

        ThemeReference reference = settings().getSelectedThemeOrDefault();

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
            ThemeReference reference = settings().getSelectedThemeOrDefault();
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

    /// Resolves the background that will be effective after applying the given theme.
    ///
    /// Applying a theme clears launcher appearance overrides, so a theme without a
    /// background resolves to the launcher default background with built-in opacity.
    ///
    /// @param themePack the theme pack to apply
    /// @param theme     the theme to apply
    /// @param context   the condition resolution context
    /// @return the background that should be displayed after the theme is applied
    /// @throws IOException if the theme background cannot be resolved
    public static ResolvedBackground resolveBackgroundAfterApplyingTheme(
            InstalledThemePack themePack,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(themePack);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(context);

        @Nullable ResolvedBackground resolved = resolveThemeBackground(themePack, theme, context);
        if (resolved != null) {
            return resolved;
        }
        return new ResolvedBackground(
                BackgroundType.DEFAULT,
                null,
                null,
                null,
                null,
                1.0);
    }

    /// Resolves the fallback background used when the current launcher background cannot be loaded.
    ///
    /// @param context the condition resolution context
    /// @return the effective fallback background
    /// @throws IOException if the selected theme fallback exists but cannot be resolved
    public static ResolvedBackground resolveCurrentBackgroundFallback(ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(context);

        if (!SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK)) {
            @Nullable ThemeAppearance appearance = resolveCurrentThemeAppearance(context);
            @Nullable ThemeBackgroundSettings background = appearance != null ? appearance.background() : null;
            double opacity = SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY)
                    ? currentBackgroundOpacity()
                    : background != null && background.opacity() != null ? background.opacity() : 1.0;
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
                    Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER),
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
    /// @return the effective background loading policy
    public static BackgroundLoadPolicy resolveCurrentBackgroundLoadPolicy() {
        if (SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_LOAD_POLICY)) {
            return Objects.requireNonNullElse(
                    settings().backgroundLoadPolicyProperty().get(),
                    BackgroundLoadPolicy.WAIT_FOR_BACKGROUND);
        }

        return BackgroundLoadPolicy.WAIT_FOR_BACKGROUND;
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
                        Objects.requireNonNullElse(
                                settings().networkBackgroundImageCachePolicyProperty().get(),
                                NetworkBackgroundImageCachePolicy.ENABLED),
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
                    Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER),
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

        @Nullable ResolvedBackground resolved = resolveThemeBackground(themePack, theme, context);
        if (resolved == null) {
            return null;
        }
        if (SettingsManager.settings().isThemeAppearanceOverridden(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY)) {
            resolved = resolved.withOpacity(currentBackgroundOpacity());
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

        return resolveThemeBackground(new ThemePackLocation.Local(themePackFile), theme, context);
    }

    /// Resolves the background contributed by one installed theme without applying launcher appearance overrides.
    ///
    /// @param themePack the installed theme pack
    /// @param theme     the theme to resolve
    /// @param context   the condition resolution context
    /// @return the resolved background, or `null` when the theme does not define one
    /// @throws IOException if the theme background cannot be resolved
    public static @Nullable ResolvedBackground resolveThemeBackground(
            InstalledThemePack themePack,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(themePack);
        return resolveThemeBackground(themePack.location(), theme, context);
    }

    /// Resolves the background contributed by one theme without applying launcher appearance overrides.
    private static @Nullable ResolvedBackground resolveThemeBackground(
            ThemePackLocation location,
            Theme theme,
            ThemeResolveContext context) throws IOException {
        Objects.requireNonNull(location);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(context);

        ThemeAppearance appearance = theme.resolve(context);
        @Nullable ThemeBackgroundSettings background = appearance.background();
        if (background == null) {
            return null;
        }
        return resolveBackground(
                location,
                background,
                1.0,
                appearance.toResolvedTheme(context).toColorScheme().getColor(ColorRole.SURFACE_CONTAINER));
    }

    /// Resolves a concrete launcher background from a theme-pack background object.
    private static ResolvedBackground resolveBackground(
            ThemePackLocation location,
            ThemeBackgroundSettings background,
            double fallbackOpacity,
            Paint themeColorPaint) throws IOException {
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
            ThemePackResource resource = resolveInstalledAsset(
                    location,
                    requireNonBlank(image.path(), "background.path"));
            return new ResolvedBackground(
                    BackgroundType.CUSTOM,
                    resource.file(),
                    resource,
                    opacity);
        }
        if (source instanceof ThemeBackground.ThemeColor) {
            return new ResolvedBackground(
                    BackgroundType.THEME_COLOR,
                    null,
                    null,
                    null,
                    themeColorPaint,
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

    /// Resolves one asset referenced by an installed theme pack.
    ///
    /// @param location the installed theme-pack location
    /// @param entryName the theme-pack relative asset entry name
    /// @return the resolved resource
    /// @throws IOException if the asset cannot be read
    public static ThemePackResource resolveInstalledAsset(ThemePackLocation location, String entryName) throws IOException {
        Objects.requireNonNull(location);
        String normalizedEntryName = ThemePackAsset.normalizeEntryName(entryName);
        if (location instanceof ThemePackLocation.Builtin builtin) {
            return resolveBuiltinAsset(builtin, normalizedEntryName);
        }

        @Nullable Path file = location.file();
        if (file == null) {
            throw new IOException("Theme pack location has no readable file: " + location);
        }
        Path installedFile = file.toAbsolutePath().normalize();
        if (Files.isDirectory(installedFile)) {
            Path assetFile = installedFile.resolve(normalizedEntryName).normalize();
            if (!assetFile.startsWith(installedFile)) {
                throw new IOException("Theme-pack asset escapes the installed directory: " + normalizedEntryName);
            }
            if (!Files.isRegularFile(assetFile)) {
                throw new IOException("Installed theme-pack asset is missing: " + normalizedEntryName);
            }
            return new ThemePackResource.File(assetFile);
        }
        if (!Files.isRegularFile(installedFile)) {
            throw new IOException("Installed theme-pack file is missing: " + installedFile);
        }

        try (ZipFile zipFile = new ZipFile(installedFile.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(normalizedEntryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Installed theme-pack asset is missing: " + normalizedEntryName);
            }
        }
        return new ThemePackResource.Zip(installedFile, normalizedEntryName);
    }

    /// Resolves one asset stored in a bundled theme pack.
    private static ThemePackResource resolveBuiltinAsset(ThemePackLocation.Builtin builtin, String entryName) throws IOException {
        String id = ThemePackManifest.requirePackageId(builtin.id());
        String resourcePath = "/assets/themes/" + id + "/" + entryName;
        try (InputStream input = ThemePackManager.class.getResourceAsStream(resourcePath)) {
            if (input != null) {
                return new ThemePackResource.Builtin(resourcePath, entryName);
            }
        }
        throw new IOException("Built-in theme-pack asset is missing: " + entryName);
    }

    /// Returns the installed theme-pack file under a theme-pack directory for one package ID.
    private static Path installedThemePackFile(Path themePacksDirectory, String packId) {
        String id = ThemePackManifest.requirePackageId(packId);
        return themePacksDirectory
                .resolve(id + ThemePackExporter.FILE_EXTENSION)
                .toAbsolutePath()
                .normalize();
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
        return switch (background.type()) {
            case DEFAULT -> new ThemeBackgroundSettings(
                    new ThemeBackground.Default(),
                    opacity);
            case BUILTIN -> new ThemeBackgroundSettings(
                    new ThemeBackground.Builtin(background.builtinBackgroundId()),
                    opacity);
            case CUSTOM -> new ThemeBackgroundSettings(
                    createCurrentImageBackgroundSource(assets, background),
                    opacity);
            case NETWORK -> throw new IOException("Network backgrounds cannot be exported as theme-pack wallpapers");
            case PAINT -> new ThemeBackgroundSettings(
                    new ThemeBackground.Paint(Objects.requireNonNullElse(background.paint(), Color.WHITE).toString()),
                    opacity);
            case THEME_COLOR -> new ThemeBackgroundSettings(
                    new ThemeBackground.ThemeColor(),
                    opacity);
        };
    }

    /// Creates the image background source for the resolved current background image.
    private static ThemeBackground.Image createCurrentImageBackgroundSource(
            List<ThemePackAsset> assets,
            ResolvedBackground background) throws IOException {
        @Nullable ThemePackResource imageResource = background.imageResource();
        if (imageResource != null) {
            assets.add(new ThemePackAsset(imageResource, imageResource.name()));
            return new ThemeBackground.Image(imageResource.name());
        }

        @Nullable Path imagePath = background.imagePath();
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

    /// Sanitizes one path segment used for exported asset files.
    private static String sanitizePathSegment(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "_";
        }
        return sanitized;
    }
}
