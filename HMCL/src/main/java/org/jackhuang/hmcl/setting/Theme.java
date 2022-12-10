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
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

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

        Color textFill = getForegroundColor();
        String fontFamily = System.getProperty("hmcl.font.override", overrideFontFamily);

        if (true) {
            try {
                File temp = File.createTempFile("hmcl", ".css");
                FileUtils.writeText(temp, IOUtils.readFullyAsString(Theme.class.getResourceAsStream("/assets/css/custom.css"))
                        .replace("%base-color%", color)
                        .replace("%base-red%", Integer.toString((int) Math.ceil(paint.getRed() * 256)))
                        .replace("%base-green%", Integer.toString((int) Math.ceil(paint.getGreen() * 256)))
                        .replace("%base-blue%", Integer.toString((int) Math.ceil(paint.getBlue() * 256)))
                        .replace("%base-rippler-color%", String.format("rgba(%d, %d, %d, 0.3)", (int) Math.ceil(paint.getRed() * 256), (int) Math.ceil(paint.getGreen() * 256), (int) Math.ceil(paint.getBlue() * 256)))
                        .replace("%disabled-font-color%", String.format("rgba(%d, %d, %d, 0.7)", (int) Math.ceil(textFill.getRed() * 256), (int) Math.ceil(textFill.getGreen() * 256), (int) Math.ceil(textFill.getBlue() * 256)))
                        .replace("%font-color%", getColorDisplayName(getForegroundColor()))
                        .replace("%font%", Optional.ofNullable(fontFamily).map(f -> "-fx-font-family: \"" + f + "\";").orElse("")));
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
        else if (name.equalsIgnoreCase("blue"))
            return Optional.of(BLUE);
        else if (name.equalsIgnoreCase("darker_blue"))
            return Optional.of(custom("#283593"));
        else if (name.equalsIgnoreCase("green"))
            return Optional.of(custom("#43A047"));
        else if (name.equalsIgnoreCase("orange"))
            return Optional.of(custom("#E67E22"));
        else if (name.equalsIgnoreCase("purple"))
            return Optional.of(custom("#9C27B0"));
        else if (name.equalsIgnoreCase("red"))
            return Optional.of(custom("#F44336"));

        if (name.startsWith("#"))
            try {
                Color.web(name);
                return Optional.of(custom(name));
            } catch (IllegalArgumentException ignore) {
            }

        return Optional.empty();
    }

    public static String getColorDisplayName(Color c) {
        return c != null ? String.format("#%02x%02x%02x", Math.round(c.getRed() * 255.0D), Math.round(c.getGreen() * 255.0D), Math.round(c.getBlue() * 255.0D)).toUpperCase() : null;
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
            out.value(value.getName().toLowerCase());
        }

        @Override
        public Theme read(JsonReader in) throws IOException {
            return getTheme(in.nextString()).orElse(Theme.BLUE);
        }
    }
}
