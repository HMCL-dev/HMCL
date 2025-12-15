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

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.paint.Color;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorScheme;
import org.glavo.monetfx.Contrast;
import org.glavo.monetfx.beans.property.ColorSchemeProperty;
import org.glavo.monetfx.beans.property.ReadOnlyColorSchemeProperty;
import org.glavo.monetfx.beans.property.SimpleColorSchemeProperty;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

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
                        yield Brightness.DEFAULT;
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

    private Themes() {
    }
}
