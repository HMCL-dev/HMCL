/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.setting;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.IOUtils;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

public class Theme {
    public static final Theme BLUE = new Theme("blue", "#5C6BC0");

    public static final Color[] SUGGESTED_COLORS = new Color[]{
            Color.web("#5C6BC0"), // blue
            Color.web("#283593"), // dark blue
            Color.web("#43A047"), // green
            Color.web("#E67E22"), // orange
            Color.web("#9C27B0"), // purple
            Color.web("#F44336")  // red
    };

    private final String color;
    private final String name;

    Theme(String name, String color) {
        this.name = name;
        this.color = color;
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
        return Color.web(color).grayscale().getRed() >= 0.5;
    }

    public Color getForegroundColor() {
        return isLight() ? Color.BLACK : Color.WHITE;
    }

    public String[] getStylesheets() {
        String css;
        try {
            File temp = File.createTempFile("hmcl", ".css");
            FileUtils.writeText(temp, IOUtils.readFullyAsString(Theme.class.getResourceAsStream("/assets/css/custom.css"))
                    .replace("%base-color%", color)
                    .replace("%font-color%", getColorDisplayName(getForegroundColor())));
            css = temp.toURI().toString();
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "Unable to create theme stylesheet. Fallback to blue theme.", e);
            css = Theme.class.getResource("/assets/css/blue.css").toExternalForm();
        }

        return new String[]{
                css,
                Theme.class.getResource("/assets/css/root.css").toExternalForm()
        };
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
            return Optional.of(custom("#5C6BC0"));
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

    public static ObjectBinding<Color> foregroundFillBinding() {
        return Bindings.createObjectBinding(() -> config().getTheme().getForegroundColor(), config().themeProperty());
    }

    public static ObjectBinding<Color> blackFillBinding() {
        return Bindings.createObjectBinding(() -> Color.BLACK);
    }

    public static ObjectBinding<Color> whiteFillBinding() {
        return Bindings.createObjectBinding(() -> Color.WHITE);
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
