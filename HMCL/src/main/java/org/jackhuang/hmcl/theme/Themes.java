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
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorScheme;
import org.glavo.monetfx.Contrast;
import org.glavo.monetfx.beans.property.ColorSchemeProperty;
import org.glavo.monetfx.beans.property.ReadOnlyColorSchemeProperty;
import org.glavo.monetfx.beans.property.SimpleColorSchemeProperty;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.MacOSNativeUtils;
import org.jackhuang.hmcl.ui.WindowsNativeUtils;
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

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Provides the current launcher MonetFX theme and derived color bindings.
@NotNullByDefault
public final class Themes {

    /// The resolved launcher theme used to build the current MonetFX color scheme.
    private static final ObjectExpression<ResolvedTheme> theme = new ObjectBinding<>() {
        {
            List<Observable> observables = new ArrayList<>();

            observables.add(settings().themeBrightnessProperty());
            observables.add(settings().customThemeColorProperty());
            observables.add(settings().themeColorTypeProperty());
            observables.add(settings().backgroundTypeProperty());
            observables.add(settings().backgroundImageProperty());
            observables.add(settings().backgroundPaintProperty());
            if (FXUtils.DARK_MODE != null) {
                observables.add(FXUtils.DARK_MODE);
            }
            bind(observables.toArray(new Observable[0]));
        }

        /// Returns the effective brightness for the current launcher settings.
        private Brightness getBrightness() {
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

        /// Returns the effective theme color for the current launcher settings.
        private ThemeColor getThemeColor() {
            ThemeColor fallback = Objects.requireNonNullElse(settings().customThemeColorProperty().get(), ThemeColor.DEFAULT);
            ThemeColorType themeColorType = Objects.requireNonNullElse(settings().themeColorTypeProperty().get(), ThemeColorType.CUSTOM);
            if (themeColorType != ThemeColorType.BACKGROUND) {
                return fallback;
            }
            return getBackgroundThemeColor(fallback);
        }

        /// Returns a Monet seed color extracted from the current background when possible.
        private ThemeColor getBackgroundThemeColor(ThemeColor fallback) {
            BackgroundType backgroundType = Objects.requireNonNullElse(settings().backgroundTypeProperty().get(), BackgroundType.DEFAULT);
            return switch (backgroundType) {
                case CUSTOM -> getImageThemeColor(fallback);
                case PAINT -> {
                    @Nullable Paint paint = settings().backgroundPaintProperty().get();
                    yield paint instanceof Color color ? ThemeColor.of(color) : fallback;
                }
                case DEFAULT, CLASSIC, NETWORK -> fallback;
            };
        }

        /// Returns a Monet seed color extracted from the current custom background image.
        private ThemeColor getImageThemeColor(ThemeColor fallback) {
            @Nullable String backgroundImage = settings().backgroundImageProperty().get();
            if (backgroundImage == null || backgroundImage.isBlank()) {
                return fallback;
            }

            try {
                @Nullable ThemePackResourceURL resourceURL = ThemePackResourceURL.parse(backgroundImage);
                Path imageFile = resourceURL != null
                        ? resourceURL.resolve()
                        : Path.of(backgroundImage).toAbsolutePath().normalize();
                if (!Files.isRegularFile(imageFile)) {
                    return fallback;
                }
                return WallpaperColorExtractor.extract(imageFile, fallback);
            } catch (IOException | RuntimeException e) {
                return fallback;
            }
        }

        /// Computes the resolved launcher theme.
        @Override
        protected ResolvedTheme computeValue() {
            return new ResolvedTheme(getThemeColor(), getBrightness(), ResolvedTheme.DEFAULT.colorStyle(), Contrast.DEFAULT);
        }
    };

    /// The current MonetFX color scheme generated from [#theme].
    private static final ColorSchemeProperty colorScheme = new SimpleColorSchemeProperty();

    /// Whether the current color scheme uses dark brightness.
    private static final BooleanBinding darkMode = Bindings.createBooleanBinding(
            () -> colorScheme.get().getBrightness() == Brightness.DARK,
            colorScheme
    );

    static {
        ChangeListener<ResolvedTheme> listener = (observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                colorScheme.set(newValue.toColorScheme());
            }
        };
        colorScheme.set(theme.get().toColorScheme());
        theme.addListener(listener);
    }

    /// Cached system default brightness.
    private static @Nullable Brightness defaultBrightness;

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
