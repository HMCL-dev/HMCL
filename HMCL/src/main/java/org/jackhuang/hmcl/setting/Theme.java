/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

@JsonAdapter(Theme.TypeAdapter.class)
public class Theme {
    public static final Theme BLUE = new Theme("blue", "#5C6BC0");
    public static final Color BLACK = Color.web("#292929");
    public static final Color[] SUGGESTED_COLORS = new Color[]{
            Color.web("#3D6DA3"), // blue
            Color.web("#283593"), // dark blue
            Color.web("#43A047"), // green
            Color.web("#E67E22"), // orange
            Color.web("#9C27B0"), // purple
            Color.web("#B71C1C")  // red
    };

    private static Charset cssCharset;

    private static Charset getCssCharset() {
        if (cssCharset != null)
            return cssCharset;

        Charset defaultCharset = Charset.defaultCharset();
        if (defaultCharset != StandardCharsets.UTF_8) {
            // https://bugs.openjdk.org/browse/JDK-8279328
            // For JavaFX 17 or earlier, native encoding should be used
            String jfxVersion = System.getProperty("javafx.version");
            if (jfxVersion != null) {
                Matcher matcher = Pattern.compile("^(?<version>[0-9]+)").matcher(jfxVersion);
                if (matcher.find()) {
                    int v = Lang.parseInt(matcher.group(), -1);
                    if (v >= 18) {
                        cssCharset = StandardCharsets.UTF_8;
                    }
                }
            }
        }

        if (cssCharset == null)
            cssCharset = defaultCharset;

        return cssCharset;
    }

    public static Theme getTheme() {
        Theme theme = config().getTheme();
        return theme == null ? BLUE : theme;
    }

    private final Color paint;
    private final String color;
    private final String name;

    Theme(String name, String color) {
        this.name = name;
        this.color = Objects.requireNonNull(color);
        this.paint = Color.web(color);
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public boolean isCustom() {
        return name.startsWith("#");
    }

    public boolean isLight() {
        return paint.grayscale().getRed() >= 0.5;
    }

    public Color getForegroundColor() {
        return isLight() ? Color.BLACK : Color.WHITE;
    }

    public String[] getStylesheets(String overrideFontFamily) {
        String css = "/assets/css/blue.css";

        String fontFamily = System.getProperty("hmcl.font.override", overrideFontFamily);

        if (fontFamily != null || !this.color.equalsIgnoreCase(BLUE.color)) {
            Color textFill = getForegroundColor();
            try {
                File temp = File.createTempFile("hmcl", ".css");
                String themeText = IOUtils.readFullyAsString(Theme.class.getResourceAsStream("/assets/css/custom.css"))
                        .replace("%base-color%", color)
                        .replace("%base-red%", Integer.toString((int) Math.ceil(paint.getRed() * 256)))
                        .replace("%base-green%", Integer.toString((int) Math.ceil(paint.getGreen() * 256)))
                        .replace("%base-blue%", Integer.toString((int) Math.ceil(paint.getBlue() * 256)))
                        .replace("%base-rippler-color%", String.format("rgba(%d, %d, %d, 0.3)", (int) Math.ceil(paint.getRed() * 256), (int) Math.ceil(paint.getGreen() * 256), (int) Math.ceil(paint.getBlue() * 256)))
                        .replace("%disabled-font-color%", String.format("rgba(%d, %d, %d, 0.7)", (int) Math.ceil(textFill.getRed() * 256), (int) Math.ceil(textFill.getGreen() * 256), (int) Math.ceil(textFill.getBlue() * 256)))
                        .replace("%font-color%", getColorDisplayName(getForegroundColor()))
                        .replace("%font%", Optional.ofNullable(fontFamily).map(f -> "-fx-font-family: \"" + f + "\";").orElse(""));
                FileUtils.writeText(temp, themeText, getCssCharset());
                temp.deleteOnExit();
                css = temp.toURI().toString();
            } catch (IOException | NullPointerException e) {
                Logging.LOG.log(Level.SEVERE, "Unable to create theme stylesheet. Fallback to blue theme.", e);
            }
        }

        return new String[]{css, "/assets/css/root.css"};
    }

    public static Theme custom(String color) {
        if (!color.startsWith("#"))
            throw new IllegalArgumentException();
        return new Theme(color, color);
    }

    public static Optional<Theme> getTheme(String name) {
        if (name == null)
            return Optional.empty();
        else if (name.startsWith("#"))
            try {
                Color.web(name);
                return Optional.of(custom(name));
            } catch (IllegalArgumentException ignore) {
            }
        else {
            String color = null;
            switch (name.toLowerCase(Locale.ROOT)) {
                case "blue":
                    return Optional.of(BLUE);
                case "darker_blue":
                    color = "#283593";
                    break;
                case "green":
                    color = "#43A047";
                    break;
                case "orange":
                    color = "#E67E22";
                    break;
                case "purple":
                    color = "#9C27B0";
                    break;
                case "red":
                    color = "#F44336";
            }
            if (color != null)
                return Optional.of(new Theme(name, color));
        }

        return Optional.empty();
    }

    public static String getColorDisplayName(Color c) {
        return c != null ? String.format("#%02x%02x%02x", Math.round(c.getRed() * 255.0D), Math.round(c.getGreen() * 255.0D), Math.round(c.getBlue() * 255.0D)).toUpperCase(Locale.ROOT) : null;
    }

    private static final ObjectBinding<Color> BLACK_FILL = Bindings.createObjectBinding(() -> BLACK);
    private static final ObjectBinding<Color> WHITE_FILL = Bindings.createObjectBinding(() -> Color.WHITE);
    private static ObjectBinding<Color> FOREGROUND_FILL;

    public static ObjectBinding<Color> foregroundFillBinding() {
        if (FOREGROUND_FILL == null)
            FOREGROUND_FILL = Bindings.createObjectBinding(
                    () -> Theme.getTheme().getForegroundColor(),
                    config().themeProperty()
            );

        return FOREGROUND_FILL;
    }

    public static ObjectBinding<Color> blackFillBinding() {
        return BLACK_FILL;
    }

    public static ObjectBinding<Color> whiteFillBinding() {
        return WHITE_FILL;
    }

    public static class TypeAdapter extends com.google.gson.TypeAdapter<Theme> {
        @Override
        public void write(JsonWriter out, Theme value) throws IOException {
            out.value(value.getName().toLowerCase(Locale.ROOT));
        }

        @Override
        public Theme read(JsonReader in) throws IOException {
            return getTheme(in.nextString()).orElse(Theme.BLUE);
        }
    }
}
