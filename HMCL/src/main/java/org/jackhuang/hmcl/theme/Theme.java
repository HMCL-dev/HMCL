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
import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.paint.Color;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorScheme;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.beans.binding.ColorSchemeBinding;
import org.glavo.monetfx.beans.binding.ColorSchemeExpression;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

/// @author Glavo
public record Theme(ThemeColor primaryColorSeed,
                    Brightness brightness) {

    public static final Theme DEFAULT = new Theme(ThemeColor.DEFAULT, Brightness.DEFAULT);

    private static final ObjectExpression<Theme> theme = new ObjectBinding<>() {
        {
            List<Observable> observables = new ArrayList<>();

            observables.add(config().themeColorTypeProperty());
            observables.add(config().themeColorProperty());
            observables.add(config().themeBrightnessProperty());
            observables.add(Controllers.getDecorator().getDecorator().contentBackgroundProperty());
            if (FXUtils.DARK_MODE != null) {
                observables.add(FXUtils.DARK_MODE);
            }
            if (FXUtils.ACCENT_COLOR != null) {
                observables.add(FXUtils.ACCENT_COLOR);
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
            ThemeColor themeColor = switch (Objects.requireNonNullElse(config().getThemeColorType(), ThemeColorType.DEFAULT)) {
                case CUSTOM -> Objects.requireNonNullElse(config().getThemeColor(), ThemeColor.DEFAULT);
                case MONET -> {
                    Background contentBackground = Controllers.getDecorator().getDecorator().getContentBackground();
                    if (contentBackground != null) {
                        if (!contentBackground.getImages().isEmpty()) {
                            Image backgroundImage = contentBackground.getImages().get(0).getImage();
                            yield ThemeColor.of(ColorScheme.extractColor(backgroundImage, ThemeColor.DEFAULT.color()));
                        } else if (!contentBackground.getFills().isEmpty()
                                && contentBackground.getFills().get(0).getFill() instanceof Color color) {
                            yield ThemeColor.of(color);
                        } else {
                            yield ThemeColor.DEFAULT;
                        }
                    } else {
                        yield ThemeColor.DEFAULT;
                    }
                }
                case SYSTEM -> FXUtils.ACCENT_COLOR != null
                        ? ThemeColor.of(FXUtils.ACCENT_COLOR.get())
                        : ThemeColor.DEFAULT;
                default -> ThemeColor.DEFAULT;
            };

            return new Theme(themeColor, getBrightness());
        }
    };

    public static ObjectExpression<Theme> themeProperty() {
        return theme;
    }

    public static Theme getTheme() {
        return themeProperty().get();
    }

    private static final ColorSchemeBinding colorScheme = ColorSchemeBinding.createColorSchemeBinding(
            () -> theme.get().toColorScheme(),
            theme
    );

    public static ColorSchemeExpression colorSchemeProperty() {
        return colorScheme;
    }

    public static ColorScheme getColorScheme() {
        return colorScheme.get();
    }

    public static ObservableValue<Color> titleFillProperty() {
        return colorSchemeProperty().getOnPrimaryContainer();
    }

    private static final BooleanBinding darkMode = Bindings.createBooleanBinding(
            () -> colorScheme.get().getBrightness() == Brightness.DARK,
            colorScheme
    );

    public static BooleanBinding darkModeProperty() {
        return darkMode;
    }

    public ColorScheme toColorScheme() {
        return ColorScheme.newBuilder()
                .setPrimaryColorSeed(primaryColorSeed.color())
                .setColorStyle(ColorStyle.FIDELITY)
                .setBrightness(brightness)
                .build();
    }
}
