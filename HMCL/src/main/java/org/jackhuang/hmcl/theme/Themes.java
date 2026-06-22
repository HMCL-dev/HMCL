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
import org.jackhuang.hmcl.setting.NetworkBackgroundImageCachePolicy;
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
            if (FXUtils.DARK_MODE != null) {
                observables.add(FXUtils.DARK_MODE);
            }
            bind(observables.toArray(new Observable[0]));
        }

        /// Returns the configured MonetFX color style.
        private ColorStyle getColorStyle() {
            return Objects.requireNonNullElse(settings().themeColorStyleProperty().get(), ResolvedTheme.DEFAULT.colorStyle());
        }

        /// Computes the resolved launcher theme.
        @Override
        protected ResolvedTheme computeValue() {
            return new ResolvedTheme(resolveCurrentThemeColor(), getCurrentBrightness(), getColorStyle(), Contrast.DEFAULT);
        }
    };

    /// Returns the effective theme color for the current launcher settings.
    static ThemeColor resolveCurrentThemeColor() {
        ThemeColorType themeColorType = Objects.requireNonNullElse(settings().themeColorTypeProperty().get(), ThemeColorType.DEFAULT);
        ThemeColor fallback = themeColorType == ThemeColorType.DEFAULT
                ? ThemeColor.DEFAULT
                : Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT);
        BackgroundType backgroundType = Objects.requireNonNullElse(settings().backgroundTypeProperty().get(), BackgroundType.DEFAULT);
        return resolveThemeColor(fallback, themeColorType, backgroundType);
    }

    /// Resolves a Monet seed color from configured theme color and background sources.
    static ThemeColor resolveThemeColor(
            ThemeColor fallback,
            ThemeColorType themeColorType,
            BackgroundType backgroundType) {
        Objects.requireNonNull(fallback);
        Objects.requireNonNull(themeColorType);
        Objects.requireNonNull(backgroundType);

        if (themeColorType == ThemeColorType.DEFAULT) {
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

    /// Monotonic counter used to ignore obsolete asynchronous background updates.
    private static int backgroundUpdateCount = 0;

    /// Whether JavaFX background loading has been requested by the UI.
    private static boolean backgroundUpdatesStarted = false;

    static {
        ChangeListener<ResolvedTheme> listener = (observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                colorScheme.set(newValue.toColorScheme());
            }
        };
        colorScheme.set(theme.get().toColorScheme());
        theme.addListener(listener);

        InvalidationListener themeContextListener = ignored -> ThemePackManager.refreshCurrentThemeForContext();
        settings().themeBrightnessProperty().addListener(themeContextListener);
        if (FXUtils.DARK_MODE != null) {
            FXUtils.DARK_MODE.addListener(themeContextListener);
        }

        InvalidationListener backgroundListener = ignored -> {
            if (backgroundUpdatesStarted) {
                refreshBackground();
            }
        };
        settings().themeProperty().addListener(backgroundListener);
        settings().backgroundTypeProperty().addListener(backgroundListener);
        settings().builtinBackgroundNameProperty().addListener(backgroundListener);
        settings().customBackgroundImagePathProperty().addListener(backgroundListener);
        settings().networkBackgroundImageUrlProperty().addListener(backgroundListener);
        settings().networkBackgroundImageCachePolicyProperty().addListener(backgroundListener);
        settings().customBackgroundPaintProperty().addListener(backgroundListener);
        settings().backgroundOpacityProperty().addListener(backgroundListener);
        colorSchemeProperty().addListener(backgroundListener);
    }

    /// Cached system default brightness.
    private static @Nullable Brightness defaultBrightness;

    /// Returns the effective brightness from launcher and system settings without resolving the full launcher theme.
    ///
    /// @return the effective launcher brightness
    public static Brightness getCurrentBrightness() {
        @Nullable String themeBrightness = settings().themeBrightnessProperty().get();
        if (themeBrightness == null)
            return Brightness.DEFAULT;

        return switch (themeBrightness.toLowerCase(Locale.ROOT).trim()) {
            case "auto" -> {
                if (FXUtils.DARK_MODE != null) {
                    yield FXUtils.DARK_MODE.get() ? Brightness.DARK : Brightness.LIGHT;
                } else {
                    yield getDefaultBrightness();
                }
            }
            case "dark" -> Brightness.DARK;
            case "light" -> Brightness.LIGHT;
            default -> Brightness.DEFAULT;
        };
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
            refreshBackground();
        }
    }

    /// Refreshes the current JavaFX launcher background asynchronously.
    public static void refreshBackground() {
        final int currentCount = ++backgroundUpdateCount;
        Task.supplyAsync(Schedulers.io(), Themes::loadBackground)
                .setName("Update background")
                .whenComplete(Schedulers.javafx(), (newBackground, exception) -> {
                    if (exception == null) {
                        if (backgroundUpdateCount == currentCount) {
                            background.set(newBackground);
                        }
                    } else {
                        LOG.warning("Failed to update background", exception);
                    }
                }).start();
    }

    /// Loads the current JavaFX launcher background.
    private static Background loadBackground() {
        BackgroundType backgroundType = Objects.requireNonNullElse(
                settings().backgroundTypeProperty().get(),
                BackgroundType.DEFAULT);

        @Nullable Image image = null;
        switch (backgroundType) {
            case DEFAULT:
                try {
                    return createResolvedBackground(ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext()));
                } catch (IOException | RuntimeException e) {
                    LOG.warning("Couldn't resolve default background", e);
                }
                break;
            case BUILTIN:
                image = loadBuiltinBackgroundImage(settings().builtinBackgroundNameProperty().get());
                break;
            case CUSTOM:
                @Nullable String customBackgroundImagePath = settings().customBackgroundImagePathProperty().get();
                if (customBackgroundImagePath != null) {
                    try {
                        Path path = Path.of(customBackgroundImagePath);
                        image = Files.isDirectory(path)
                                ? randomImageIn(path)
                                : tryLoadImage(path);
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                }
                break;
            case NETWORK:
                @Nullable String networkBackgroundImageUrl = settings().networkBackgroundImageUrlProperty().get();
                if (networkBackgroundImageUrl != null) {
                    try {
                        image = loadNetworkBackgroundImage(
                                networkBackgroundImageUrl,
                                settings().networkBackgroundImageCachePolicyProperty().get());
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                }
                break;
            case PAINT:
                return createPaintBackground(settings().customBackgroundPaintProperty().get(), settings().backgroundOpacityProperty().get());
            case THEME_COLOR:
                return createThemeColorBackground(settings().backgroundOpacityProperty().get());
        }
        if (image == null) {
            image = loadDefaultBackgroundImage();
        }
        return createBackgroundWithOpacity(image, settings().backgroundOpacityProperty().get());
    }

    /// Creates a JavaFX launcher background from a resolved theme-pack background.
    private static Background createResolvedBackground(ThemePackManager.ResolvedBackground resolvedBackground) {
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
                image = loadBuiltinBackgroundImage(settings().builtinBackgroundNameProperty().get());
                break;
            case PAINT:
            case THEME_COLOR:
                return createPaintBackground(resolvedBackground.paint(), resolvedBackground.opacity());
            case DEFAULT:
                break;
        }
        if (image == null) {
            image = loadDefaultBackgroundImage();
        }
        return createBackgroundWithOpacity(image, resolvedBackground.opacity());
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

        return newBuiltinImage("/assets/img/background.jpg");
    }

    /// Loads one named built-in launcher background.
    private static Image loadBuiltinBackgroundImage(@Nullable String name) {
        if (BackgroundType.BUILTIN_CLASSIC.equals(name)) {
            return newBuiltinImage("/assets/img/background-classic.jpg");
        }
        return newBuiltinImage("/assets/img/background.jpg");
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

    /// The title text fill derived from the current color scheme.
    private static final ObjectBinding<Color> titleFill = Bindings.createObjectBinding(
            () -> settings().titleTransparentProperty().get()
                    ? getColorScheme().getOnSurface()
                    : getColorScheme().getOnPrimaryContainer(),
            colorSchemeProperty(),
            settings().titleTransparentProperty()
    );

    /// Returns the title text fill property derived from the current color scheme.
    public static ObservableValue<Color> titleFillProperty() {
        return titleFill;
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
