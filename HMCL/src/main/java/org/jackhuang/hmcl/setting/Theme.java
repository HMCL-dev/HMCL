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

import javafx.scene.paint.Color;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.IOUtils;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

public class Theme {
    public static final Theme BLUE = new Theme("blue", "#5C6BC0");
    public static final Theme DARK_BLUE = new Theme("dark_blue", "#283593");
    public static final Theme GREEN = new Theme("green", "#43A047");
    public static final Theme ORANGE = new Theme("orange", "#E67E22");
    public static final Theme PURPLE = new Theme("purple", "#9C27B0");
    public static final Theme RED = new Theme("red", "#F44336");

    public static final Theme[] VALUES = new Theme[]{BLUE, DARK_BLUE, GREEN, ORANGE, PURPLE, RED};

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

    public String[] getStylesheets() {
        String name = isCustom() ? BLUE.getName() : this.name;
        String css = Theme.class.getResource("/assets/css/" + name + ".css").toExternalForm();
        try {
            File temp = File.createTempFile("hmcl", ".css");
            FileUtils.writeText(temp, IOUtils.readFullyAsString(Theme.class.getResourceAsStream("/assets/css/custom.css")).replace("%base-color%", color));
            css = temp.toURI().toString();
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "Unable to create theme stylesheet", e);
        }

        return new String[]{
                Theme.class.getResource("/css/jfoenix-fonts.css").toExternalForm(),
                Theme.class.getResource("/css/jfoenix-design.css").toExternalForm(),
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

        for (Theme theme : VALUES)
            if (theme.name.equalsIgnoreCase(name))
                return Optional.of(theme);

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


}
