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
package org.jackhuang.hmcl.setting;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorRole;
import org.glavo.monetfx.ColorScheme;
import org.jackhuang.hmcl.theme.Theme;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class StyleSheets {
    private static final int FONT_STYLE_SHEET_INDEX = 0;
    private static final int THEME_STYLE_SHEET_INDEX = 1;
    private static final int BRIGHTNESS_SHEET_INDEX = 2;

    private static final ObservableList<String> stylesheets;

    static {
        String[] array = new String[]{
                getFontStyleSheet(),
                getThemeStyleSheet(),
                getBrightnessStyleSheet(),
                "/assets/css/root.css"
        };
        stylesheets = FXCollections.observableList(Arrays.asList(array));

        FontManager.fontProperty().addListener(o -> stylesheets.set(FONT_STYLE_SHEET_INDEX, getFontStyleSheet()));
        Themes.colorSchemeProperty().addListener(o -> {
            stylesheets.set(THEME_STYLE_SHEET_INDEX, getThemeStyleSheet());
            stylesheets.set(BRIGHTNESS_SHEET_INDEX, getBrightnessStyleSheet());
        });
    }

    private static String toStyleSheetUri(String styleSheet, String fallback) {
        if (FXUtils.JAVAFX_MAJOR_VERSION >= 17)
            // JavaFX 17+ support loading stylesheets from data URIs
            // https://bugs.openjdk.org/browse/JDK-8267554
            return "data:text/css;charset=UTF-8;base64," + Base64.getEncoder().encodeToString(styleSheet.getBytes(StandardCharsets.UTF_8));
        else
            try {
                Path temp = Files.createTempFile("hmcl", ".css");
                // For JavaFX 17 or earlier, CssParser uses the default charset
                // https://bugs.openjdk.org/browse/JDK-8279328
                Files.writeString(temp, styleSheet, Charset.defaultCharset());
                temp.toFile().deleteOnExit();
                return temp.toUri().toString();
            } catch (IOException | NullPointerException e) {
                LOG.error("Unable to create stylesheet, fallback to " + fallback, e);
                return fallback;
            }
    }

    private static String getFontStyleSheet() {
        final String defaultCss = "/assets/css/font.css";
        final FontManager.FontReference font = FontManager.getFont();

        if (font == null || "System".equals(font.family()))
            return defaultCss;

        String fontFamily = font.family();
        String style = font.style();
        String weight = null;
        String posture = null;

        if (style != null) {
            style = style.toLowerCase(Locale.ROOT);

            if (style.contains("thin"))
                weight = "100";
            else if (style.contains("extralight") || style.contains("extra light") || style.contains("ultralight") | style.contains("ultra light"))
                weight = "200";
            else if (style.contains("medium"))
                weight = "500";
            else if (style.contains("semibold") || style.contains("semi bold") || style.contains("demibold") || style.contains("demi bold"))
                weight = "600";
            else if (style.contains("extrabold") || style.contains("extra bold") || style.contains("ultrabold") || style.contains("ultra bold"))
                weight = "800";
            else if (style.contains("black") || style.contains("heavy"))
                weight = "900";
            else if (style.contains("light"))
                weight = "lighter";
            else if (style.contains("bold"))
                weight = "bold";

            posture = style.contains("italic") || style.contains("oblique") ? "italic" : null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(".root {");
        builder.append("-fx-font-family:\"").append(fontFamily).append("\";");

        if (weight != null)
            builder.append("-fx-font-weight:").append(weight).append(";");

        if (posture != null)
            builder.append("-fx-font-style:").append(posture).append(";");

        builder.append('}');

        return toStyleSheetUri(builder.toString(), defaultCss);
    }

    private static String getBrightnessStyleSheet() {
        return Themes.getColorScheme().getBrightness() == Brightness.LIGHT
                ? "/assets/css/brightness-light.css"
                : "/assets/css/brightness-dark.css";
    }

    private static void addColor(StringBuilder builder, String name, Color color) {
        builder.append("  ").append(name)
                .append(": ").append(ThemeColor.getColorDisplayName(color)).append(";\n");
    }

    private static void addColor(StringBuilder builder, String name, Color color, double opacity) {
        builder.append("  ").append(name)
                .append(": ").append(ThemeColor.getColorDisplayNameWithOpacity(color, opacity)).append(";\n");
    }

    private static void addColor(StringBuilder builder, ColorScheme scheme, ColorRole role, double opacity) {
        builder.append("  ").append(role.getVariableName()).append("-transparent-%02d".formatted((int) (100 * opacity)))
                .append(": ").append(ThemeColor.getColorDisplayNameWithOpacity(scheme.getColor(role), opacity))
                .append(";\n");
    }

    private static String getThemeStyleSheet() {
        final String blueCss = "/assets/css/blue.css";

        if (Theme.DEFAULT.equals(Themes.getTheme()))
            return blueCss;

        ColorScheme scheme = Themes.getColorScheme();

        StringBuilder builder = new StringBuilder();
        builder.append("* {\n");
        for (ColorRole colorRole : ColorRole.ALL) {
            addColor(builder, colorRole.getVariableName(), scheme.getColor(colorRole));
        }

        addColor(builder, "-monet-primary-seed", scheme.getPrimaryColorSeed());

        addColor(builder, scheme, ColorRole.PRIMARY, 0.5);
        addColor(builder, scheme, ColorRole.SECONDARY_CONTAINER, 0.5);
        addColor(builder, scheme, ColorRole.SURFACE, 0.5);
        addColor(builder, scheme, ColorRole.SURFACE, 0.8);
        addColor(builder, scheme, ColorRole.ON_SURFACE_VARIANT, 0.38);
        addColor(builder, scheme, ColorRole.SURFACE_CONTAINER_LOW, 0.8);
        addColor(builder, scheme, ColorRole.SECONDARY_CONTAINER, 0.8);
        addColor(builder, scheme, ColorRole.INVERSE_SURFACE, 0.8);

        builder.append("}\n");
        return toStyleSheetUri(builder.toString(), blueCss);
    }

    public static void init(Scene scene) {
        Bindings.bindContent(scene.getStylesheets(), stylesheets);
    }

    private StyleSheets() {
    }
}
