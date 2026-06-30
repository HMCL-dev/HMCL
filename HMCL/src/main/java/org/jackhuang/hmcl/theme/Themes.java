/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.sun.jna.Pointer;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.SetChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorScheme;
import org.glavo.monetfx.ColorRole;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.glavo.monetfx.beans.property.ColorSchemeProperty;
import org.glavo.monetfx.beans.property.ReadOnlyColorSchemeProperty;
import org.glavo.monetfx.beans.property.SimpleColorSchemeProperty;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.MacOSNativeUtils;
import org.jackhuang.hmcl.ui.WindowsNativeUtils;
import org.jackhuang.hmcl.util.MathUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OSVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.windows.Dwmapi;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;
import org.jackhuang.hmcl.util.platform.windows.WinReg;
import org.jackhuang.hmcl.util.platform.windows.WinTypes;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.ui.FXUtils.newBuiltinImage;
import static org.jackhuang.hmcl.util.io.FileUtils.getExtension;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Provides the current launcher MonetFX theme and derived color bindings.
@NotNullByDefault
public final class Themes {
    /// The seed color extracted from the last loaded wallpaper image.
    private static final ReadOnlyObjectWrapper<@Nullable ThemeColor> wallpaperThemeColor = new ReadOnlyObjectWrapper<>();

    /// The resolved launcher theme used to build the current MonetFX color scheme.
    private static final ObjectExpression<ResolvedTheme> theme = new ObjectBinding<>() {
        {
            List<Observable> observables = new ArrayList<>();

            observables.add(settings().selectedThemeProperty());
            observables.add(settings().getThemeAppearanceOverrides());
            observables.add(settings().themeBrightnessModeProperty());
            observables.add(settings().customThemeColorProperty());
            observables.add(settings().themeColorTypeProperty());
            observables.add(settings().themeColorStyleProperty());
            observables.add(settings().backgroundTypeProperty());
            observables.add(settings().customBackgroundImagePathProperty());
            observables.add(settings().customBackgroundPaintProperty());
            observables.add(wallpaperThemeColor);
            if (FXUtils.DARK_MODE != null) {
                observables.add(FXUtils.DARK_MODE);
            }
            bind(observables.toArray(new Observable[0]));
        }

        /// Returns the configured MonetFX color style.
        private ColorStyle getColorStyle() {
            if (settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_COLOR_STYLE)) {
                return Objects.requireNonNullElse(
                        settings().themeColorStyleProperty().get(),
                        ResolvedTheme.DEFAULT.colorStyle());
            }
            try {
                return Objects.requireNonNullElse(
                        ThemePackManager.resolveCurrentThemeColorStyle(ThemePackManager.currentResolveContext()),
                        ResolvedTheme.DEFAULT.colorStyle());
            } catch (IOException | RuntimeException e) {
                return ResolvedTheme.DEFAULT.colorStyle();
            }
        }

        /// Returns the configured MonetFX contrast.
        private Contrast getContrast() {
            try {
                return Objects.requireNonNullElse(
                        ThemePackManager.resolveCurrentThemeContrast(ThemePackManager.currentResolveContext()),
                        ResolvedTheme.DEFAULT.contrast());
            } catch (IOException | RuntimeException e) {
                return ResolvedTheme.DEFAULT.contrast();
            }
        }

        /// Computes the resolved launcher theme.
        @Override
        protected ResolvedTheme computeValue() {
            return new ResolvedTheme(resolveCurrentThemeColor(), getCurrentBrightness(), getColorStyle(), getContrast());
        }
    };

    /// Returns the effective theme color for the current launcher settings.
    static ThemeColor resolveCurrentThemeColor() {
        ThemeColor fallback = settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_COLOR)
                ? Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT)
                : ThemeColor.DEFAULT;
        ThemeResolveContext context = ThemePackManager.currentResolveContext();
        BackgroundType backgroundType;
        try {
            backgroundType = ThemePackManager.resolveCurrentBackground(context).type();
        } catch (IOException | RuntimeException e) {
            backgroundType = BackgroundType.DEFAULT;
        }
        return resolveThemeColor(
                fallback,
                settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_COLOR)
                        ? Objects.requireNonNullElse(settings().themeColorTypeProperty().get(), ThemeColorType.DEFAULT)
                        : null,
                backgroundType,
                context);
    }

    /// Resolves a Monet seed color from configured theme color and background sources.
    static ThemeColor resolveThemeColor(
            ThemeColor fallback,
            @Nullable ThemeColorType themeColorType,
            BackgroundType backgroundType,
            ThemeResolveContext context) {
        Objects.requireNonNull(fallback);
        Objects.requireNonNull(backgroundType);
        Objects.requireNonNull(context);

        if (themeColorType == null) {
            try {
                return resolveThemeColorSource(
                        fallback,
                        ThemePackManager.resolveCurrentThemeColorSource(context),
                        backgroundType);
            } catch (IOException | RuntimeException e) {
                return fallback;
            }
        }
        return switch (themeColorType) {
            case DEFAULT -> ThemeColor.DEFAULT;
            case CUSTOM -> fallback;
            case BACKGROUND -> resolveWallpaperThemeColor(fallback, backgroundType);
        };
    }

    /// Resolves a theme-pack color source into a concrete Monet seed color.
    private static ThemeColor resolveThemeColorSource(
            ThemeColor fallback,
            @Nullable ThemeColorSource source,
            BackgroundType backgroundType) {
        if (source == null) {
            return fallback;
        }
        if (source instanceof ThemeColorSource.Default) {
            return ThemeColor.DEFAULT;
        }
        if (source instanceof ThemeColorSource.Custom custom) {
            return custom.color();
        }
        return resolveWallpaperThemeColor(fallback, backgroundType);
    }

    /// Resolves the Monet seed color represented by the effective wallpaper.
    private static ThemeColor resolveWallpaperThemeColor(ThemeColor fallback, BackgroundType backgroundType) {
        if (backgroundType == BackgroundType.THEME_COLOR) {
            return ThemeColor.DEFAULT;
        }
        @Nullable ThemeColor loadedWallpaperColor = wallpaperThemeColor.get();
        if (loadedWallpaperColor != null) {
            return loadedWallpaperColor;
        }
        return getBackgroundThemeColor(fallback);
    }

    /// Returns a Monet seed color extracted from the current background when possible.
    private static ThemeColor getBackgroundThemeColor(ThemeColor fallback) {
        try {
            ThemePackManager.ResolvedBackground background =
                    ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext());
            return switch (background.type()) {
                case CUSTOM -> getImageThemeColor(background.imagePath(), background.imageResource(), fallback);
                case BUILTIN -> BuiltinBackground.fromIdOrFallback(background.builtinBackgroundId()).themeColor();
                case PAINT -> background.paint() instanceof Color color ? ThemeColor.of(color) : fallback;
                case THEME_COLOR -> ThemeColor.DEFAULT;
                case DEFAULT, NETWORK -> fallback;
            };
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }

    /// Returns a Monet seed color extracted from the current custom background image.
    private static ThemeColor getImageThemeColor(
            @Nullable Path imageFile,
            @Nullable ThemePackResource imageResource,
            ThemeColor fallback) {
        if (imageResource != null) {
            try {
                return WallpaperColorExtractor.extract(imageResource, fallback);
            } catch (IOException | RuntimeException e) {
                return fallback;
            }
        }

        if (imageFile == null) {
            return fallback;
        }

        try {
            if (!Files.isRegularFile(imageFile)) {
                return fallback;
            }
            return WallpaperColorExtractor.extract(imageFile, fallback);
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }

    /// The current MonetFX color scheme generated from [#theme].
    private static final ColorSchemeProperty colorScheme = new SimpleColorSchemeProperty();

    /// Whether the current color scheme uses dark brightness.
    private static final BooleanBinding darkMode = Bindings.createBooleanBinding(
            () -> colorScheme.get().getBrightness() == Brightness.DARK,
            colorScheme
    );

    /// The current JavaFX launcher background.
    private static final ReadOnlyObjectWrapper<Background> background = new ReadOnlyObjectWrapper<>(
            new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));

    /// The last loaded background source used to rebuild the JavaFX background without reloading images.
    private static @Nullable LoadedBackground loadedBackground;

    /// Current background load generation used to ignore obsolete asynchronous updates.
    private static int backgroundLoadGeneration = 0;

    /// Whether JavaFX background loading has been requested by the UI.
    private static boolean backgroundUpdatesStarted = false;

    /// Whether theme application is already installing a preloaded background.
    private static boolean suppressBackgroundRefresh = false;

    /// A loaded JavaFX background source and the wallpaper color extracted while loading it.
    private sealed interface LoadedBackground {
        /// Returns the loaded JavaFX background.
        Background background();

        /// Returns whether this background is the configured fallback background.
        boolean fallbackBackground();

        /// Returns the seed color extracted from the actual wallpaper image, or `null` when unavailable.
        @Nullable ThemeColor wallpaperThemeColor();

        /// A loaded image background.
        ///
        /// @param background          the loaded JavaFX background
        /// @param image               the loaded image source
        /// @param wallpaperThemeColor the seed color extracted from [#image], or `null` when unavailable
        /// @param fallbackBackground  whether this background is the configured fallback background
        record Image(
                Background background,
                javafx.scene.image.Image image,
                @Nullable ThemeColor wallpaperThemeColor,
                boolean fallbackBackground) implements LoadedBackground {
            /// Creates a loaded image background.
            ///
            /// @param background          the loaded JavaFX background
            /// @param image               the loaded image source
            /// @param wallpaperThemeColor the seed color extracted from [#image], or `null` when unavailable
            /// @param fallbackBackground  whether this background is the configured fallback background
            public Image {
                Objects.requireNonNull(background);
                Objects.requireNonNull(image);
            }
        }

        /// A loaded fixed-paint background.
        ///
        /// @param background          the loaded JavaFX background
        /// @param paint               the paint source, or `null` for the launcher default paint
        /// @param wallpaperThemeColor the seed color represented by [#paint], or `null` when unavailable
        /// @param fallbackBackground  whether this background is the configured fallback background
        record Paint(
                Background background,
                @Nullable javafx.scene.paint.Paint paint,
                @Nullable ThemeColor wallpaperThemeColor,
                boolean fallbackBackground) implements LoadedBackground {
            /// Creates a loaded fixed-paint background.
            ///
            /// @param background          the loaded JavaFX background
            /// @param paint               the paint source, or `null` for the launcher default paint
            /// @param wallpaperThemeColor the seed color represented by [#paint], or `null` when unavailable
            /// @param fallbackBackground  whether this background is the configured fallback background
            public Paint {
                Objects.requireNonNull(background);
            }
        }

        /// A loaded background whose paint follows the current theme color scheme.
        ///
        /// @param background         the loaded JavaFX background
        /// @param fallbackBackground whether this background is the configured fallback background
        record ThemeColorFill(
                Background background,
                boolean fallbackBackground) implements LoadedBackground {
            /// Creates a loaded theme-color background.
            ///
            /// @param background         the loaded JavaFX background
            /// @param fallbackBackground whether this background is the configured fallback background
            public ThemeColorFill {
                Objects.requireNonNull(background);
            }

            @Override
            public @Nullable ThemeColor wallpaperThemeColor() {
                return null;
            }
        }
    }

    static {
        ChangeListener<ResolvedTheme> listener = (observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                colorScheme.set(newValue.toColorScheme());
            }
        };
        colorScheme.set(theme.get().toColorScheme());
        theme.addListener(listener);

        InvalidationListener backgroundListener = ignored -> {
            if (backgroundUpdatesStarted && !suppressBackgroundRefresh) {
                refreshBackground();
            }
        };
        settings().selectedThemeProperty().addListener(backgroundListener);
        settings().getThemeAppearanceOverrides().addListener((SetChangeListener<String>) change -> {
            String setting = change.wasAdded() ? change.getElementAdded() : change.getElementRemoved();
            switch (setting) {
                case LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE,
                     LauncherSettings.THEME_APPEARANCE_BACKGROUND ->
                        backgroundListener.invalidated(settings().getThemeAppearanceOverrides());
                case LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY -> refreshBackgroundOpacity();
                default -> {
                }
            }
        });
        settings().backgroundTypeProperty().addListener(backgroundListener);
        settings().builtinBackgroundIdProperty().addListener(backgroundListener);
        settings().customBackgroundImagePathProperty().addListener(backgroundListener);
        settings().networkBackgroundImageUrlProperty().addListener(backgroundListener);
        settings().networkBackgroundImageCachePolicyProperty().addListener(backgroundListener);
        settings().customBackgroundPaintProperty().addListener(backgroundListener);
        settings().backgroundOpacityProperty().addListener(ignored -> refreshBackgroundOpacity());
        settings().backgroundFallbackTypeProperty().addListener(ignored -> refreshFallbackBackground());
        settings().backgroundFallbackPaintProperty().addListener(ignored -> refreshFallbackBackground());
        settings().themeBrightnessModeProperty().addListener(backgroundListener);
        if (FXUtils.DARK_MODE != null) {
            FXUtils.DARK_MODE.addListener(backgroundListener);
        }
        colorSchemeProperty().addListener(ignored -> refreshThemeColorBackground());
    }

    /// Cached system default brightness.
    private static @Nullable Brightness defaultBrightness;

    /// Returns the brightness used for resolving theme conditions without reading the selected theme's brightness value.
    ///
    /// @return the brightness used by theme condition matching
    public static Brightness getThemeConditionBrightness() {
        if (!settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE)) {
            return getAutomaticBrightness();
        }

        String themeBrightnessMode = settings().themeBrightnessModeProperty().get();
        return switch (Objects.toString(themeBrightnessMode, "").toLowerCase(Locale.ROOT).trim()) {
            case "light" -> Brightness.LIGHT;
            case "dark" -> Brightness.DARK;
            default -> getAutomaticBrightness();
        };
    }

    /// Returns the effective brightness from launcher, theme, and system settings.
    ///
    /// @return the effective launcher brightness
    public static Brightness getCurrentBrightness() {
        if (!settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE)) {
            Brightness contextBrightness = getThemeConditionBrightness();
            try {
                return Objects.requireNonNullElse(
                        ThemePackManager.resolveCurrentThemeBrightness(ThemeResolveContext.current(contextBrightness)),
                        contextBrightness);
            } catch (IOException | RuntimeException e) {
                return contextBrightness;
            }
        }

        String themeBrightnessMode = settings().themeBrightnessModeProperty().get();
        return switch (Objects.toString(themeBrightnessMode, "").toLowerCase(Locale.ROOT).trim()) {
            case "auto" -> getAutomaticBrightness();
            case "dark" -> Brightness.DARK;
            case "light" -> Brightness.LIGHT;
            default -> getAutomaticBrightness();
        };
    }

    /// Returns the brightness requested by the current system or platform settings.
    private static Brightness getAutomaticBrightness() {
        if (FXUtils.DARK_MODE != null) {
            return FXUtils.DARK_MODE.get() ? Brightness.DARK : Brightness.LIGHT;
        }
        return getDefaultBrightness();
    }

    /// Detects and returns the system default brightness.
    private static Brightness getDefaultBrightness() {
        if (defaultBrightness != null)
            return defaultBrightness;

        LOG.info("Detecting system theme brightness");
        Brightness brightness = Brightness.DEFAULT;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            WinReg reg = WinReg.INSTANCE;
            if (reg != null) {
                Object appsUseLightTheme = reg.queryValue(WinReg.HKEY.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme");
                if (appsUseLightTheme instanceof Integer value) {
                    brightness = value == 0 ? Brightness.DARK : Brightness.LIGHT;
                }
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            try {
                String result = SystemUtils.run("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle").trim();
                brightness = "Dark".equalsIgnoreCase(result) ? Brightness.DARK : Brightness.LIGHT;
            } catch (Exception e) {
                // If the key does not exist, it means Light mode is used
                brightness = Brightness.LIGHT;
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            Path dbusSend = SystemUtils.which("dbus-send");
            if (dbusSend != null) {
                try {
                    String[] result = SystemUtils.run(List.of(
                            FileUtils.getAbsolutePath(dbusSend),
                            "--session",
                            "--print-reply=literal",
                            "--reply-timeout=1000",
                            "--dest=org.freedesktop.portal.Desktop",
                            "/org/freedesktop/portal/desktop",
                            "org.freedesktop.portal.Settings.Read",
                            "string:org.freedesktop.appearance",
                            "string:color-scheme"
                    ), Duration.ofSeconds(2)).trim().split(" ");

                    if (result.length > 0) {
                        String value = result[result.length - 1];
                        // 1: prefer dark
                        // 2: prefer light
                        if ("1".equals(value)) {
                            brightness = Brightness.DARK;
                        } else if ("2".equals(value)) {
                            brightness = Brightness.LIGHT;
                        }
                    }
                } catch (Exception e) {
                    LOG.warning("Failed to get system theme from D-Bus", e);
                }
            }
        }
        LOG.info("Detected system theme brightness: " + brightness);

        return defaultBrightness = brightness;
    }

    /// Returns the current resolved launcher theme.
    public static ResolvedTheme getTheme() {
        return theme.get();
    }

    /// Returns the current MonetFX color scheme property.
    public static ReadOnlyColorSchemeProperty colorSchemeProperty() {
        return colorScheme;
    }

    /// Returns the current MonetFX color scheme.
    public static ColorScheme getColorScheme() {
        return colorScheme.get();
    }

    /// Returns the current JavaFX launcher background property.
    public static ReadOnlyObjectProperty<Background> backgroundProperty() {
        startBackgroundUpdates();
        return background.getReadOnlyProperty();
    }

    /// Returns whether applying a theme should wait for its background before changing settings.
    public static boolean shouldWaitForThemeBackground() {
        return backgroundUpdatesStarted && getBackgroundLoadPolicy() == BackgroundLoadPolicy.WAIT_FOR_BACKGROUND;
    }

    /// Creates a task that applies one theme and waits for its background when requested.
    ///
    /// @param themePack the installed theme pack
    /// @param theme     the selected theme
    /// @return the task applying the theme
    public static Task<Void> applyTheme(ThemePackManager.InstalledThemePack themePack, Theme theme) {
        Objects.requireNonNull(themePack);
        Objects.requireNonNull(theme);

        if (!backgroundUpdatesStarted || getBackgroundLoadPolicy() != BackgroundLoadPolicy.WAIT_FOR_BACKGROUND) {
            return Task.runAsync(Schedulers.javafx(), () -> ThemePackManager.apply(themePack, theme));
        }

        ThemeResolveContext context = ThemeResolveContext.current(getAutomaticBrightness());
        return Task.supplyAsync(Schedulers.io(), () -> loadBackground(themePack, theme, context))
                .thenAcceptAsync(Schedulers.javafx(), loadedBackground -> {
                    suppressBackgroundRefresh = true;
                    try {
                        ThemePackManager.apply(themePack, theme);
                        applyLoadedBackground(loadedBackground, ++backgroundLoadGeneration);
                    } finally {
                        suppressBackgroundRefresh = false;
                    }
                });
    }

    /// Starts JavaFX launcher background loading when the UI first requests it.
    private static void startBackgroundUpdates() {
        if (!backgroundUpdatesStarted) {
            backgroundUpdatesStarted = true;
            if (getBackgroundLoadPolicy() == BackgroundLoadPolicy.WAIT_FOR_BACKGROUND) {
                loadInitialBackground();
            } else {
                refreshBackground();
            }
        }
    }

    /// Returns the configured launcher background loading policy.
    private static BackgroundLoadPolicy getBackgroundLoadPolicy() {
        try {
            return ThemePackManager.resolveCurrentBackgroundLoadPolicy();
        } catch (RuntimeException e) {
            return BackgroundLoadPolicy.WAIT_FOR_BACKGROUND;
        }
    }

    /// Loads the initial JavaFX launcher background synchronously before the UI uses it.
    private static void loadInitialBackground() {
        final int currentGeneration = ++backgroundLoadGeneration;
        applyLoadedBackground(loadBackground(), currentGeneration);
    }

    /// Refreshes the current JavaFX launcher background asynchronously.
    public static void refreshBackground() {
        final int currentGeneration = ++backgroundLoadGeneration;
        if (getBackgroundLoadPolicy() == BackgroundLoadPolicy.SHOW_FALLBACK_WHILE_LOADING) {
            applyLoadedBackground(loadFallbackBackground(), currentGeneration);
        }
        Task.supplyAsync(Schedulers.io(), Themes::loadBackground)
                .setName("Update background")
                .whenComplete(Schedulers.javafx(), (newLoadedBackground, exception) -> {
                    if (exception == null) {
                        applyLoadedBackground(newLoadedBackground, currentGeneration);
                    } else {
                        LOG.warning("Failed to update background", exception);
                    }
                }).start();
    }

    /// Applies one loaded background when it is still the newest requested update.
    private static void applyLoadedBackground(LoadedBackground newLoadedBackground, int generation) {
        if (backgroundLoadGeneration != generation) {
            return;
        }

        loadedBackground = newLoadedBackground;
        background.set(newLoadedBackground.background());
        wallpaperThemeColor.set(newLoadedBackground.wallpaperThemeColor());
    }

    /// Loads the current JavaFX launcher background.
    private static LoadedBackground loadBackground() {
        @Nullable LoadedBackground loaded = tryLoadBackground();
        return loaded != null ? loaded : loadFallbackBackground();
    }

    /// Loads the background that will become effective after applying the given theme.
    private static LoadedBackground loadBackground(
            ThemePackManager.InstalledThemePack themePack,
            Theme theme,
            ThemeResolveContext context) {
        try {
            ThemePackManager.ResolvedBackground resolved = ThemePackManager.resolveBackgroundAfterApplyingTheme(
                    themePack,
                    theme,
                    context);
            @Nullable LoadedBackground loaded = tryCreateResolvedBackground(resolved, false);
            if (loaded != null) {
                return loaded;
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("Couldn't load theme background before applying theme", e);
        }
        return loadFallbackBackground(theme, context);
    }

    /// Attempts to load the configured JavaFX launcher background.
    private static @Nullable LoadedBackground tryLoadBackground() {
        try {
            return tryCreateResolvedBackground(
                    ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext()),
                    false);
        } catch (IOException | RuntimeException e) {
            LOG.warning("Couldn't resolve background", e);
            return null;
        }
    }

    /// Creates a JavaFX launcher background from a resolved theme-pack background.
    private static @Nullable LoadedBackground tryCreateResolvedBackground(
            ThemePackManager.ResolvedBackground resolvedBackground,
            boolean fallbackBackground) {
        @Nullable Image image = null;
        @Nullable ThemeColor imageThemeColor = null;
        switch (resolvedBackground.type()) {
            case CUSTOM:
                @Nullable ThemePackResource imageResource = resolvedBackground.imageResource();
                if (imageResource != null) {
                    try {
                        image = FXUtils.loadImage(imageResource.openStream(), imageResource.name());
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                } else if (resolvedBackground.imagePath() != null) {
                    Path imagePath = resolvedBackground.imagePath();
                    try {
                        image = Files.isDirectory(imagePath)
                                ? randomImageIn(imagePath)
                                : tryLoadImage(imagePath);
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                }
                break;
            case NETWORK:
                @Nullable String networkBackgroundImageUrl = resolvedBackground.networkImageUrl();
                if (networkBackgroundImageUrl != null) {
                    try {
                        image = loadNetworkBackgroundImage(
                                networkBackgroundImageUrl,
                                resolvedBackground.networkImageCachePolicy());
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                }
                break;
            case BUILTIN:
                BuiltinBackground builtinBackground =
                        BuiltinBackground.fromIdOrFallback(resolvedBackground.builtinBackgroundId());
                image = loadBuiltinBackgroundImage(builtinBackground.id());
                imageThemeColor = builtinBackground.themeColor();
                break;
            case PAINT:
                @Nullable Paint paint = resolvedBackground.paint();
                return new LoadedBackground.Paint(
                        createPaintBackground(paint, resolvedBackground.opacity()),
                        paint,
                        paint instanceof Color color ? ThemeColor.of(color) : null,
                        fallbackBackground);
            case THEME_COLOR:
                return new LoadedBackground.ThemeColorFill(
                        createPaintBackground(resolvedBackground.paint(), resolvedBackground.opacity()),
                        fallbackBackground);
            case DEFAULT:
                image = loadLocalDefaultBackgroundImage();
                if (image == null) {
                    image = loadBuiltinBackgroundImage(BuiltinBackground.FALLBACK.id());
                    imageThemeColor = BuiltinBackground.FALLBACK.themeColor();
                }
                break;
        }
        if (image == null) {
            return null;
        }
        return new LoadedBackground.Image(
                createBackgroundWithOpacity(image, resolvedBackground.opacity()),
                image,
                imageThemeColor != null ? imageThemeColor : extractWallpaperThemeColor(image),
                fallbackBackground);
    }

    /// Loads the deterministic fallback background configured by launcher settings.
    private static LoadedBackground loadFallbackBackground() {
        try {
            @Nullable LoadedBackground loaded = tryCreateResolvedBackground(
                    ThemePackManager.resolveCurrentBackgroundFallback(ThemePackManager.currentResolveContext()),
                    true);
            if (loaded != null) {
                return loaded;
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("Couldn't resolve fallback background", e);
        }

        Image image = loadBuiltinBackgroundImage(BuiltinBackground.FALLBACK.id());
        return new LoadedBackground.Image(
                createBackgroundWithOpacity(image, getLoadedBackgroundOpacity(true)),
                image,
                BuiltinBackground.FALLBACK.themeColor(),
                true);
    }

    /// Loads the fallback background used while applying a not-yet-selected theme.
    private static LoadedBackground loadFallbackBackground(Theme theme, ThemeResolveContext context) {
        double opacity = 1.0;
        ThemeAppearance appearance = theme.resolve(context);
        @Nullable ThemeBackgroundSettings background = appearance.background();
        if (background != null && background.opacity() != null) {
            opacity = background.opacity();
        }

        Paint themeColorPaint = appearance.toResolvedTheme(context).toColorScheme().getColor(ColorRole.SURFACE_CONTAINER);
        BackgroundType fallbackType = Objects.requireNonNullElse(
                settings().backgroundFallbackTypeProperty().get(),
                BackgroundType.BUILTIN);
        ThemePackManager.ResolvedBackground resolvedBackground = switch (fallbackType) {
            case PAINT -> new ThemePackManager.ResolvedBackground(
                    BackgroundType.PAINT,
                    null,
                    null,
                    null,
                    settings().backgroundFallbackPaintProperty().get(),
                    opacity);
            case THEME_COLOR -> new ThemePackManager.ResolvedBackground(
                    BackgroundType.THEME_COLOR,
                    null,
                    null,
                    null,
                    themeColorPaint,
                    opacity);
            case DEFAULT, BUILTIN, CUSTOM, NETWORK -> new ThemePackManager.ResolvedBackground(
                    BackgroundType.BUILTIN,
                    BuiltinBackground.FALLBACK.id(),
                    null,
                    null,
                    null,
                    null,
                    opacity);
        };
        @Nullable LoadedBackground loaded = tryCreateResolvedBackground(resolvedBackground, true);
        if (loaded != null) {
            return loaded;
        }

        Image image = loadBuiltinBackgroundImage(BuiltinBackground.FALLBACK.id());
        return new LoadedBackground.Image(
                createBackgroundWithOpacity(image, opacity),
                image,
                BuiltinBackground.FALLBACK.themeColor(),
                true);
    }

    /// Updates the background paint when the background explicitly follows the current color scheme.
    private static void refreshThemeColorBackground() {
        if (!backgroundUpdatesStarted) {
            return;
        }
        @Nullable LoadedBackground loaded = loadedBackground;
        if (loaded instanceof LoadedBackground.ThemeColorFill themeColorFill) {
            Background newBackground = createThemeColorBackground(getLoadedBackgroundOpacity(loaded.fallbackBackground()));
            loadedBackground = new LoadedBackground.ThemeColorFill(newBackground, themeColorFill.fallbackBackground());
            background.set(newBackground);
        }
    }

    /// Rebuilds the current fallback background after fallback settings change.
    private static void refreshFallbackBackground() {
        if (!backgroundUpdatesStarted) {
            return;
        }
        @Nullable LoadedBackground loaded = loadedBackground;
        if (loaded != null && loaded.fallbackBackground()) {
            LoadedBackground fallback = loadFallbackBackground();
            loadedBackground = fallback;
            background.set(fallback.background());
            wallpaperThemeColor.set(fallback.wallpaperThemeColor());
        }
    }

    /// Rebuilds the current JavaFX background after opacity changes without reloading its source.
    private static void refreshBackgroundOpacity() {
        if (!backgroundUpdatesStarted) {
            return;
        }
        @Nullable LoadedBackground loaded = loadedBackground;
        if (loaded == null) {
            return;
        }

        double opacity = getLoadedBackgroundOpacity(loaded.fallbackBackground());
        LoadedBackground refreshed;
        if (loaded instanceof LoadedBackground.Image imageBackground) {
            refreshed = new LoadedBackground.Image(
                    createBackgroundWithOpacity(imageBackground.image(), opacity),
                    imageBackground.image(),
                    imageBackground.wallpaperThemeColor(),
                    imageBackground.fallbackBackground());
        } else if (loaded instanceof LoadedBackground.ThemeColorFill themeColorFill) {
            refreshed = new LoadedBackground.ThemeColorFill(
                    createThemeColorBackground(opacity),
                    themeColorFill.fallbackBackground());
        } else if (loaded instanceof LoadedBackground.Paint paintBackground) {
            refreshed = new LoadedBackground.Paint(
                    createPaintBackground(paintBackground.paint(), opacity),
                    paintBackground.paint(),
                    paintBackground.wallpaperThemeColor(),
                    paintBackground.fallbackBackground());
        } else {
            return;
        }

        loadedBackground = refreshed;
        background.set(refreshed.background());
    }

    /// Resolves the opacity for the currently loaded primary or fallback background.
    private static double getLoadedBackgroundOpacity(boolean fallbackBackground) {
        try {
            return fallbackBackground
                    ? ThemePackManager.resolveCurrentBackgroundFallback(ThemePackManager.currentResolveContext()).opacity()
                    : ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext()).opacity();
        } catch (IOException | RuntimeException e) {
            double configured = settings().backgroundOpacityProperty().get();
            return settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY) && Double.isFinite(configured)
                    ? MathUtils.clamp(configured, 0., 1.)
                    : 1.0;
        }
    }

    /// Extracts a seed color from a loaded wallpaper image.
    private static @Nullable ThemeColor extractWallpaperThemeColor(Image image) {
        try {
            ThemeColor fallback = !settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_COLOR)
                    ? ThemeColor.DEFAULT
                    : Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT);
            return WallpaperColorExtractor.extract(image, fallback);
        } catch (RuntimeException e) {
            LOG.warning("Couldn't extract theme color from background image", e);
            return null;
        }
    }

    /// Loads one remote background image using the requested cache policy.
    private static Image loadNetworkBackgroundImage(
            String url,
            @Nullable NetworkBackgroundImageCachePolicy cachePolicy) throws Exception {
        if (cachePolicy == NetworkBackgroundImageCachePolicy.DISABLED) {
            return FXUtils.loadImage(WebURL.parseBrowserInput(url));
        }
        return FXUtils.loadImage(new CacheFileTask(url).run());
    }

    /// Creates a JavaFX paint background with the requested opacity.
    private static Background createPaintBackground(@Nullable Paint paint, double opacity) {
        opacity = MathUtils.clamp(opacity, 0., 1.);
        if (paint instanceof Color || paint == null) {
            Color color = (Color) paint;
            if (color == null) {
                color = Color.WHITE;
            }
            if (opacity < 1.) {
                color = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * opacity);
            }
            return new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY));
        } else {
            return new Background(new BackgroundFill(paint, CornerRadii.EMPTY, Insets.EMPTY));
        }
    }

    /// Creates a JavaFX background from the current theme color scheme surface container.
    private static Background createThemeColorBackground(double opacity) {
        return createPaintBackground(getColorScheme().getColor(ColorRole.SURFACE_CONTAINER), opacity);
    }

    /// Creates a JavaFX image background with the requested opacity.
    private static Background createBackgroundWithOpacity(Image image, double opacity) {
        PixelReader pixelReader = image.getPixelReader();
        if (opacity <= 0) {
            return new Background(new BackgroundFill(new Color(1, 1, 1, 0), CornerRadii.EMPTY, Insets.EMPTY));
        } else if (opacity >= 1. || pixelReader == null) {
            return new Background(new BackgroundImage(
                    image,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.DEFAULT,
                    new BackgroundSize(800, 480, false, false, true, true)
            ));
        } else {
            WritableImage tempImage = new WritableImage((int) image.getWidth(), (int) image.getHeight());
            PixelWriter pixelWriter = tempImage.getPixelWriter();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    Color color = pixelReader.getColor(x, y);
                    Color newColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * opacity);
                    pixelWriter.setColor(x, y, newColor);
                }
            }

            return new Background(new BackgroundImage(
                    tempImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.DEFAULT,
                    new BackgroundSize(800, 480, false, false, true, true)
            ));
        }
    }

    /// Loads a default launcher background image from local workspace files.
    private static @Nullable Image loadLocalDefaultBackgroundImage() {
        @Nullable Image image = randomImageIn(Metadata.HMCL_LOCAL_HOME.resolve("background"));
        if (image != null) {
            return image;
        }

        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            image = tryLoadImage(Metadata.HMCL_LOCAL_HOME.resolve("background." + extension));
            if (image != null) {
                return image;
            }
        }

        image = randomImageIn(Metadata.CURRENT_DIRECTORY.resolve("bg"));
        if (image != null) {
            return image;
        }

        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            image = tryLoadImage(Metadata.CURRENT_DIRECTORY.resolve("background." + extension));
            if (image != null) {
                return image;
            }
        }

        return null;
    }

    /// Loads one built-in launcher wallpaper by ID.
    private static Image loadBuiltinBackgroundImage(@Nullable String id) {
        String wallpaperId = BuiltinBackground.fromIdOrFallback(id).id();
        return newBuiltinImage("/assets/img/wallpapers/" + wallpaperId + ".jpg");
    }

    /// Loads a random readable image from a directory.
    private static @Nullable Image randomImageIn(Path imageDir) {
        if (!Files.isDirectory(imageDir)) {
            return null;
        }

        ArrayList<Path> candidates;
        try (Stream<Path> stream = Files.list(imageDir)) {
            candidates = stream
                    .filter(it -> FXUtils.IMAGE_EXTENSIONS.contains(getExtension(it).toLowerCase(Locale.ROOT)))
                    .filter(Files::isReadable)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            LOG.warning("Failed to list files in " + imageDir, e);
            return null;
        }

        Random random = new Random();
        while (!candidates.isEmpty()) {
            int selected = random.nextInt(candidates.size());
            @Nullable Image loaded = tryLoadImage(candidates.get(selected));
            if (loaded != null) {
                return loaded;
            } else {
                candidates.remove(selected);
            }
        }
        return null;
    }

    /// Loads a readable image file.
    private static @Nullable Image tryLoadImage(Path path) {
        if (!Files.isReadable(path)) {
            return null;
        }

        try {
            return FXUtils.loadImage(path);
        } catch (Exception e) {
            LOG.warning("Couldn't load background image", e);
            return null;
        }
    }

    /// Whether the title area should be transparent after applying launcher and theme settings.
    private static final BooleanBinding titleBarTransparent = Bindings.createBooleanBinding(
            () -> {
                if (settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_TITLE_BAR_TRANSPARENT)) {
                    return settings().titleBarTransparentProperty().get();
                }
                try {
                    return Objects.requireNonNullElse(
                            ThemePackManager.resolveCurrentTitleBarTransparent(ThemePackManager.currentResolveContext()),
                            false);
                } catch (IOException | RuntimeException e) {
                    return false;
                }
            },
            settings().titleBarTransparentProperty(),
            settings().getThemeAppearanceOverrides(),
            settings().selectedThemeProperty(),
            settings().themeBrightnessModeProperty(),
            FXUtils.DARK_MODE != null ? FXUtils.DARK_MODE : settings().themeBrightnessModeProperty()
    );

    /// The title text fill derived from the current color scheme.
    private static final ObjectBinding<Color> titleFill = Bindings.createObjectBinding(
            () -> titleBarTransparent.get()
                    ? getColorScheme().getOnSurface()
                    : getColorScheme().getOnPrimaryContainer(),
            colorSchemeProperty(),
            titleBarTransparent
    );

    /// Returns the title text fill property derived from the current color scheme.
    public static ObservableValue<Color> titleFillProperty() {
        return titleFill;
    }

    /// Returns whether the title area should be transparent after applying launcher and theme settings.
    public static BooleanBinding titleBarTransparentProperty() {
        return titleBarTransparent;
    }

    /// Returns whether the current color scheme uses dark brightness.
    public static BooleanBinding darkModeProperty() {
        return darkMode;
    }

    /// Applies native dark-mode integration to a JavaFX stage where the platform supports it.
    public static void applyNativeDarkMode(Stage stage) {
        if (OperatingSystem.SYSTEM_VERSION.isAtLeast(OSVersion.WINDOWS_11) && NativeUtils.USE_JNA && Dwmapi.INSTANCE != null) {
            ChangeListener<Boolean> listener = FXUtils.onWeakChange(Themes.darkModeProperty(), darkMode -> {
                if (stage.isShowing()) {
                    WindowsNativeUtils.getWindowHandle(stage).ifPresent(handle -> {
                        if (handle == WinTypes.HANDLE.INVALID_VALUE)
                            return;

                        Dwmapi.INSTANCE.DwmSetWindowAttribute(
                                new WinTypes.HANDLE(Pointer.createConstant(handle)),
                                WinConstants.DWMWA_USE_IMMERSIVE_DARK_MODE,
                                new WinTypes.BOOLByReference(new WinTypes.BOOL(darkMode)),
                                WinTypes.BOOL.SIZE
                        );
                    });
                }
            });
            stage.getProperties().put("Themes.applyNativeDarkMode.listener", listener);

            if (stage.isShowing()) {
                listener.changed(null, false, Themes.darkModeProperty().get());
            } else {
                stage.addEventFilter(WindowEvent.WINDOW_SHOWN, new EventHandler<>() {
                    @Override
                    public void handle(WindowEvent event) {
                        stage.removeEventFilter(WindowEvent.WINDOW_SHOWN, this);
                        listener.changed(null, false, Themes.darkModeProperty().get());
                    }
                });
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS && MacOSNativeUtils.isSupported()) {
            MacOSNativeUtils.setAppearance(darkModeProperty().get());

            ChangeListener<Boolean> listener = FXUtils.onWeakChange(Themes.darkModeProperty(), MacOSNativeUtils::setAppearance);
            stage.getProperties().put("Themes.applyNativeDarkMode.listener", listener);
        }
    }

    /// Prevents instantiation.
    private Themes() {
    }
}
