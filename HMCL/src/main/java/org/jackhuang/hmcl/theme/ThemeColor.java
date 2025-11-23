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

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/// @author Glavo
@JsonAdapter(ThemeColor.TypeAdapter.class)
@JsonSerializable
public record ThemeColor(@NotNull String name, @NotNull Color color) {

    public static final ThemeColor DEFAULT = new ThemeColor("blue", Color.web("#5C6BC0"));

    public static final List<ThemeColor> STANDARD_COLORS = List.of(
            DEFAULT,
            new ThemeColor("darker_blue", Color.web("#283593")),
            new ThemeColor("green", Color.web("#43A047")),
            new ThemeColor("orange", Color.web("#E67E22")),
            new ThemeColor("purple", Color.web("#9C27B0")),
            new ThemeColor("red", Color.web("#B71C1C"))
    );

    public static String getColorDisplayName(Color c) {
        return c != null ? String.format("#%02X%02X%02X",
                Math.round(c.getRed() * 255.0D),
                Math.round(c.getGreen() * 255.0D),
                Math.round(c.getBlue() * 255.0D))
                : null;
    }

    public static String getColorDisplayNameWithOpacity(Color c, double opacity) {
        return c != null ? String.format("#%02X%02X%02X%02X",
                Math.round(c.getRed() * 255.0D),
                Math.round(c.getGreen() * 255.0D),
                Math.round(c.getBlue() * 255.0D),
                Math.round(opacity * 255.0))
                : null;
    }

    public static @Nullable ThemeColor of(String name) {
        if (name == null)
            return null;

        if (!name.startsWith("#")) {
            for (ThemeColor color : STANDARD_COLORS) {
                if (name.equalsIgnoreCase(color.name()))
                    return color;
            }
        }

        try {
            return new ThemeColor(name, Color.web(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Contract("null -> null; !null -> !null")
    public static ThemeColor of(Color color) {
        return color != null ? new ThemeColor(getColorDisplayName(color), color) : null;
    }

    static final class TypeAdapter extends com.google.gson.TypeAdapter<ThemeColor> {
        @Override
        public void write(JsonWriter out, ThemeColor value) throws IOException {
            out.value(value.name());
        }

        @Override
        public ThemeColor read(JsonReader in) throws IOException {
            return Objects.requireNonNullElse(of(in.nextString()), ThemeColor.DEFAULT);
        }
    }
}
