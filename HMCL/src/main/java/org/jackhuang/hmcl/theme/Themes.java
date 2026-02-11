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
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorScheme;
import org.glavo.monetfx.Contrast;
import org.glavo.monetfx.beans.property.ColorSchemeProperty;
import org.glavo.monetfx.beans.property.ReadOnlyColorSchemeProperty;
import org.glavo.monetfx.beans.property.SimpleColorSchemeProperty;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.ui.WindowsNativeUtils;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OSVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.windows.Dwmapi;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;
import org.jackhuang.hmcl.util.platform.windows.WinReg;
import org.jackhuang.hmcl.util.platform.windows.WinTypes;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public final class Themes {

    private static final ObjectExpression<Theme> theme = new ObjectBinding<>() {
        {
            List<Observable> observables = new ArrayList<>();

            observables.add(config().themeBrightnessProperty());
            observables.add(config().themeColorProperty());
            if (FXUtils.DARK_MODE != null) {
                observables.add(FXUtils.DARK_MODE);
            }
            bind(observables.toArray(new Observable[0]));
        }

        private Brightness getBrightness() {
            String themeBrightness = config().getThemeBrightness();
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

        @Override
        protected Theme computeValue() {
            ThemeColor themeColor = Objects.requireNonNullElse(config().getThemeColor(), ThemeColor.DEFAULT);

            return new Theme(themeColor, getBrightness(), Theme.DEFAULT.colorStyle(), Contrast.DEFAULT);
        }
    };
    private static final ColorSchemeProperty colorScheme = new SimpleColorSchemeProperty();
    private static final BooleanBinding darkMode = Bindings.createBooleanBinding(
            () -> colorScheme.get().getBrightness() == Brightness.DARK,
            colorScheme
    );

    static {
        ChangeListener<Theme> listener = (observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                colorScheme.set(newValue != null ? newValue.toColorScheme() : Theme.DEFAULT.toColorScheme());
            }
        };
        listener.changed(theme, null, theme.get());
        theme.addListener(listener);
    }

    private static Brightness defaultBrightness;

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

    public static ObjectExpression<Theme> themeProperty() {
        return theme;
    }

    public static Theme getTheme() {
        return themeProperty().get();
    }

    public static ReadOnlyColorSchemeProperty colorSchemeProperty() {
        return colorScheme;
    }

    public static ColorScheme getColorScheme() {
        return colorScheme.get();
    }

    private static final ObjectBinding<Color> titleFill = Bindings.createObjectBinding(
            () -> config().isTitleTransparent()
                    ? getColorScheme().getOnSurface()
                    : getColorScheme().getOnPrimaryContainer(),
            colorSchemeProperty(),
            config().titleTransparentProperty()
    );

    public static ObservableValue<Color> titleFillProperty() {
        return titleFill;
    }

    public static BooleanBinding darkModeProperty() {
        return darkMode;
    }

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
        }
    }

    private Themes() {
    }
}
