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
import org.jackhuang.hmcl.setting.BackgroundOpacityType;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.MacOSNativeUtils;
import org.jackhuang.hmcl.ui.WindowsNativeUtils;
import org.jackhuang.hmcl.util.MathUtils;
import org.jackhuang.hmcl.util.StringUtils;
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

            observables.add(settings().themeProperty());
            observables.add(settings().themeBrightnessProperty());
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
            @Nullable ColorStyle configured = settings().themeColorStyleProperty().get();
            if (configured != null) {
                return configured;
            }
            try {
                return ThemePackManager.resolveCurrentThemeColorStyle(
                        ThemePackManager.currentResolveContext(),
                        ResolvedTheme.DEFAULT.colorStyle());
            } catch (IOException | RuntimeException e) {
                return ResolvedTheme.DEFAULT.colorStyle();
            }
        }

        /// Computes the resolved launcher theme.
        @Override
        protected ResolvedTheme computeValue() {
            return new ResolvedTheme(resolveCurrentThemeColor(), getCurrentBrightness(), getColorStyle(), Contrast.DEFAULT);
        }
    };

    /// Returns the effective theme color for the current launcher settings.
    static ThemeColor resolveCurrentThemeColor() {
        @Nullable ThemeColorType themeColorType = settings().themeColorTypeProperty().get();
        ThemeColor fallback = themeColorType == null
                ? ThemeColor.DEFAULT
                : Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT);
        BackgroundType backgroundType = Objects.requireNonNullElse(settings().backgroundTypeProperty().get(), BackgroundType.DEFAULT);
        return resolveThemeColor(fallback, themeColorType, backgroundType);
    }

    /// Resolves a Monet seed color from configured theme color and background sources.
    static ThemeColor resolveThemeColor(
            ThemeColor fallback,
            @Nullable ThemeColorType themeColorType,
            BackgroundType backgroundType) {
        Objects.requireNonNull(fallback);
        Objects.requireNonNull(backgroundType);

        if (themeColorType == null) {
            try {
                return ThemePackManager.resolveCurrentThemeColor(
                        ThemeResolveContext.current(getCurrentBrightness()),
                        fallback,
                        backgroundType);
            } catch (IOException | RuntimeException e) {
                return fallback;
            }
        }
        if (themeColorType != ThemeColorType.BACKGROUND) {
            return fallback;
        }
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
                    ThemePackManager.resolveCurrentBackground(ThemeResolveContext.current(getCurrentBrightness()));
            return switch (background.type()) {
                case CUSTOM -> getImageThemeColor(background.imagePath(), fallback);
                case PAINT -> background.paint() instanceof Color color ? ThemeColor.of(color) : fallback;
                case THEME_COLOR -> ThemeColor.DEFAULT;
                case DEFAULT, BUILTIN, NETWORK -> fallback;
            };
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }

    /// Returns a Monet seed color extracted from the current custom background image.
    private static ThemeColor getImageThemeColor(@Nullable Path imageFile, ThemeColor fallback) {
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

    /// Monotonic counter used to ignore obsolete asynchronous background updates.
    private static int backgroundUpdateCount = 0;

    /// Whether JavaFX background loading has been requested by the UI.
    private static boolean backgroundUpdatesStarted = false;

    /// A loaded JavaFX background source and the wallpaper color extracted while loading it.
    ///
    /// @param background the loaded JavaFX background
    /// @param image the loaded image used by image backgrounds, or `null` for paint backgrounds
    /// @param paint the paint used by paint backgrounds, or `null` for image and theme-color backgrounds
    /// @param paintBackground whether the background should be rebuilt as a paint background
    /// @param themeColorBackground whether the background should be rebuilt from the current theme color scheme
    /// @param wallpaperThemeColor the color extracted from the actual wallpaper image, or `null` when unavailable
    /// @param fallbackBackground whether this background is the configured fallback background
    public record LoadedBackground(
            Background background,
            @Nullable Image image,
            @Nullable Paint paint,
            boolean paintBackground,
            boolean themeColorBackground,
            @Nullable ThemeColor wallpaperThemeColor,
            boolean fallbackBackground) {
        /// Creates a loaded background result.
        ///
        /// @param background the loaded JavaFX background
        /// @param image the loaded image used by image backgrounds, or `null` for paint backgrounds
        /// @param paint the paint used by paint backgrounds, or `null` for image and theme-color backgrounds
        /// @param paintBackground whether the background should be rebuilt as a paint background
        /// @param themeColorBackground whether the background should be rebuilt from the current theme color scheme
        /// @param wallpaperThemeColor the color extracted from the actual wallpaper image, or `null` when unavailable
        /// @param fallbackBackground whether this background is the configured fallback background
        public LoadedBackground {
            Objects.requireNonNull(background);
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
            if (backgroundUpdatesStarted) {
                refreshBackground();
            }
        };
        settings().themeProperty().addListener(backgroundListener);
        settings().backgroundTypeProperty().addListener(backgroundListener);
        settings().builtinBackgroundIdProperty().addListener(backgroundListener);
        settings().customBackgroundImagePathProperty().addListener(backgroundListener);
        settings().networkBackgroundImageUrlProperty().addListener(backgroundListener);
        settings().networkBackgroundImageCachePolicyProperty().addListener(backgroundListener);
        settings().customBackgroundPaintProperty().addListener(backgroundListener);
        settings().backgroundOpacityTypeProperty().addListener(ignored -> refreshBackgroundOpacity());
        settings().backgroundOpacityProperty().addListener(ignored -> refreshBackgroundOpacity());
        settings().backgroundFallbackTypeProperty().addListener(ignored -> refreshFallbackBackground());
        settings().backgroundFallbackPaintProperty().addListener(ignored -> refreshFallbackBackground());
        settings().themeBrightnessProperty().addListener(backgroundListener);
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
        @Nullable String themeBrightness = settings().themeBrightnessProperty().get();
        if (themeBrightness == null) {
            return getAutomaticBrightness();
        }

        return switch (themeBrightness.toLowerCase(Locale.ROOT).trim()) {
            case "light" -> Brightness.LIGHT;
            case "dark" -> Brightness.DARK;
            default -> getAutomaticBrightness();
        };
    }

    /// Returns the effective brightness from launcher, theme, and system settings.
    ///
    /// @return the effective launcher brightness
    public static Brightness getCurrentBrightness() {
        @Nullable String themeBrightness = settings().themeBrightnessProperty().get();
        if (StringUtils.isBlank(themeBrightness) || "default".equalsIgnoreCase(themeBrightness.trim())) {
            Brightness contextBrightness = getThemeConditionBrightness();
            try {
                return ThemePackManager.resolveCurrentThemeBrightness(
                        ThemeResolveContext.current(contextBrightness),
                        contextBrightness);
            } catch (IOException | RuntimeException e) {
                return contextBrightness;
            }
        }

        return switch (themeBrightness.toLowerCase(Locale.ROOT).trim()) {
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

    /// Returns the resolved launcher theme property.
    public static ObjectExpression<ResolvedTheme> themeProperty() {
        return theme;
    }

    /// Returns the current resolved launcher theme.
    public static ResolvedTheme getTheme() {
        return themeProperty().get();
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

    /// Returns the current JavaFX launcher background.
    public static Background getBackground() {
        return backgroundProperty().get();
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
            return ThemePackManager.resolveCurrentBackgroundLoadPolicy(ThemePackManager.currentResolveContext());
        } catch (IOException | RuntimeException e) {
            return BackgroundLoadPolicy.WAIT_FOR_BACKGROUND;
        }
    }

    /// Loads the initial JavaFX launcher background synchronously before the UI uses it.
    private static void loadInitialBackground() {
        final int currentCount = ++backgroundUpdateCount;
        applyLoadedBackground(loadBackground(), currentCount);
    }

    /// Refreshes the current JavaFX launcher background asynchronously.
    public static void refreshBackground() {
        final int currentCount = ++backgroundUpdateCount;
        if (getBackgroundLoadPolicy() == BackgroundLoadPolicy.SHOW_FALLBACK_WHILE_LOADING) {
            applyLoadedBackground(loadFallbackBackground(), currentCount);
        }
        Task.supplyAsync(Schedulers.io(), Themes::loadBackground)
                .setName("Update background")
                .whenComplete(Schedulers.javafx(), (newLoadedBackground, exception) -> {
                    if (exception == null) {
                        applyLoadedBackground(newLoadedBackground, currentCount);
                    } else {
                        LOG.warning("Failed to update background", exception);
                    }
                }).start();
    }

    /// Applies one loaded background when it is still the newest requested update.
    private static void applyLoadedBackground(LoadedBackground newLoadedBackground, int updateCount) {
        if (backgroundUpdateCount != updateCount) {
            return;
        }

        loadedBackground = newLoadedBackground;
        background.set(newLoadedBackground.background());
        wallpaperThemeColor.set(newLoadedBackground.wallpaperThemeColor());
    }

    /// Applies a preloaded background and ignores older pending background refreshes.
    ///
    /// @param newLoadedBackground the preloaded background
    public static void applyLoadedBackground(LoadedBackground newLoadedBackground) {
        Objects.requireNonNull(newLoadedBackground);
        applyLoadedBackground(newLoadedBackground, ++backgroundUpdateCount);
    }

    /// Loads the current JavaFX launcher background.
    private static LoadedBackground loadBackground() {
        @Nullable LoadedBackground loaded = tryLoadBackground();
        return loaded != null ? loaded : loadFallbackBackground();
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
        switch (resolvedBackground.type()) {
            case CUSTOM:
                @Nullable Path imagePath = resolvedBackground.imagePath();
                if (imagePath != null) {
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
                image = loadBuiltinBackgroundImage(resolvedBackground.builtinBackgroundId());
                break;
            case PAINT:
                @Nullable Paint paint = resolvedBackground.paint();
                return new LoadedBackground(
                        createPaintBackground(paint, resolvedBackground.opacity()),
                        null,
                        paint,
                        true,
                        false,
                        paint instanceof Color color ? ThemeColor.of(color) : null,
                        fallbackBackground);
            case THEME_COLOR:
                return new LoadedBackground(
                        createPaintBackground(resolvedBackground.paint(), resolvedBackground.opacity()),
                        null,
                        null,
                        true,
                        true,
                        null,
                        fallbackBackground);
            case DEFAULT:
                image = loadDefaultBackgroundImage();
                break;
        }
        if (image == null) {
            return null;
        }
        return createImageBackground(image, resolvedBackground.opacity(), fallbackBackground);
    }

    /// Loads a resolved theme-pack background without changing the current launcher background.
    ///
    /// @param resolvedBackground the resolved background to load
    /// @return the loaded background, or `null` if it cannot be loaded
    public static @Nullable LoadedBackground loadResolvedBackground(ThemePackManager.ResolvedBackground resolvedBackground) {
        Objects.requireNonNull(resolvedBackground);
        return tryCreateResolvedBackground(resolvedBackground, false);
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

        Image image = loadBuiltinBackgroundImage(BackgroundType.FALLBACK_BUILTIN_WALLPAPER_ID);
        return new LoadedBackground(
                createBackgroundWithOpacity(image, getLoadedBackgroundOpacity(true)),
                image,
                null,
                false,
                false,
                extractWallpaperThemeColor(image),
                true);
    }

    /// Updates the background paint when the background explicitly follows the current color scheme.
    private static void refreshThemeColorBackground() {
        if (!backgroundUpdatesStarted) {
            return;
        }
        @Nullable LoadedBackground loaded = loadedBackground;
        if (loaded != null && loaded.themeColorBackground()) {
            Background newBackground = createThemeColorBackground(getLoadedBackgroundOpacity(loaded.fallbackBackground()));
            loadedBackground = new LoadedBackground(newBackground, null, null, true, true, null, loaded.fallbackBackground());
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
        Background newBackground;
        if (loaded.image() != null) {
            newBackground = createBackgroundWithOpacity(loaded.image(), opacity);
        } else if (loaded.themeColorBackground()) {
            newBackground = createThemeColorBackground(opacity);
        } else if (loaded.paintBackground()) {
            newBackground = createPaintBackground(loaded.paint(), opacity);
        } else {
            return;
        }

        loadedBackground = new LoadedBackground(
                newBackground,
                loaded.image(),
                loaded.paint(),
                loaded.paintBackground(),
                loaded.themeColorBackground(),
                loaded.wallpaperThemeColor(),
                loaded.fallbackBackground());
        background.set(newBackground);
    }

    /// Creates a JavaFX image background and extracts a seed color from the same loaded image.
    private static LoadedBackground createImageBackground(Image image, double opacity, boolean fallbackBackground) {
        return new LoadedBackground(
                createBackgroundWithOpacity(image, opacity),
                image,
                null,
                false,
                false,
                extractWallpaperThemeColor(image),
                fallbackBackground);
    }

    /// Resolves the opacity for the currently loaded primary or fallback background.
    private static double getLoadedBackgroundOpacity(boolean fallbackBackground) {
        try {
            return fallbackBackground
                    ? ThemePackManager.resolveCurrentBackgroundFallback(ThemePackManager.currentResolveContext()).opacity()
                    : ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext()).opacity();
        } catch (IOException | RuntimeException e) {
            double configured = settings().backgroundOpacityProperty().get();
            return settings().backgroundOpacityTypeProperty().get() == BackgroundOpacityType.CUSTOM && Double.isFinite(configured)
                    ? MathUtils.clamp(configured, 0., 1.)
                    : 1.0;
        }
    }

    /// Extracts a seed color from a loaded wallpaper image.
    private static @Nullable ThemeColor extractWallpaperThemeColor(Image image) {
        try {
            return WallpaperColorExtractor.extract(image, getWallpaperThemeColorFallback());
        } catch (RuntimeException e) {
            LOG.warning("Couldn't extract theme color from background image", e);
            return null;
        }
    }

    /// Returns the fallback seed color used while extracting wallpaper colors.
    private static ThemeColor getWallpaperThemeColorFallback() {
        @Nullable ThemeColorType themeColorType = settings().themeColorTypeProperty().get();
        return themeColorType == null
                ? ThemeColor.DEFAULT
                : Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT);
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

    /// Loads the default launcher background image.
    private static Image loadDefaultBackgroundImage() {
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

        return loadBuiltinBackgroundImage(BackgroundType.FALLBACK_BUILTIN_WALLPAPER_ID);
    }

    /// Loads one built-in launcher wallpaper by ID.
    private static Image loadBuiltinBackgroundImage(@Nullable String id) {
        String wallpaperId = BackgroundType.BUILTIN_WALLPAPER_IDS.contains(id)
                ? id
                : BackgroundType.FALLBACK_BUILTIN_WALLPAPER_ID;
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
    private static final BooleanBinding titleTransparent = Bindings.createBooleanBinding(
            () -> {
                @Nullable Boolean configured = settings().titleTransparentProperty().get();
                if (configured != null) {
                    return configured;
                }
                try {
                    return ThemePackManager.resolveCurrentTitleBarTransparent(
                            ThemePackManager.currentResolveContext(),
                            false);
                } catch (IOException | RuntimeException e) {
                    return false;
                }
            },
            settings().titleTransparentProperty(),
            settings().themeProperty(),
            settings().themeBrightnessProperty(),
            FXUtils.DARK_MODE != null ? FXUtils.DARK_MODE : settings().themeBrightnessProperty()
    );

    /// The title text fill derived from the current color scheme.
    private static final ObjectBinding<Color> titleFill = Bindings.createObjectBinding(
            () -> titleTransparent.get()
                    ? getColorScheme().getOnSurface()
                    : getColorScheme().getOnPrimaryContainer(),
            colorSchemeProperty(),
            titleTransparent
    );

    /// Returns the title text fill property derived from the current color scheme.
    public static ObservableValue<Color> titleFillProperty() {
        return titleFill;
    }

    /// Returns whether the title area should be transparent after applying launcher and theme settings.
    public static BooleanBinding titleTransparentProperty() {
        return titleTransparent;
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
