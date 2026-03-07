/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.theme2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.Locale;

/// @author Glavo
public sealed interface ThemeBackground {

    static ThemeBackground fromJson(JsonElement json) throws JsonParseException {
        if (json instanceof JsonPrimitive primitive) {
            String value = primitive.getAsString();

            try {
                return BuiltIn.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return new Local(value);
            }
        } else if (json instanceof JsonObject object) {
            String type;
            if (object.get("type") instanceof JsonPrimitive elementType)
                type = elementType.getAsString();
            else
                throw new JsonParseException("Invalid theme background: " + json);

            switch (type) {
                case "local" -> {
                    if (object.get("path") instanceof JsonPrimitive path)
                        return new Local(path.getAsString());
                    else
                        throw new JsonParseException("Invalid theme background: " + json);
                }
                case "remote" -> {
                    if (object.get("url") instanceof JsonPrimitive url)
                        return new Remote(url.getAsString());
                    else
                        throw new JsonParseException("Invalid theme background: " + json);
                }
                case "fill" -> {
                    Paint paint;

                    if (object.get("paint") instanceof JsonPrimitive paintJson) {
                        try {
                            paint = Paint.valueOf(paintJson.getAsString());
                        } catch (IllegalArgumentException ignored) {
                            paint = null;
                        }
                    } else if (object.get("color") instanceof JsonPrimitive colorJson) {
                        try {
                            paint = Color.web(colorJson.getAsString());
                        } catch (IllegalArgumentException ignored) {
                            paint = null;
                        }
                    } else {
                        paint = null;
                    }

                    if (paint != null)
                        return new Fill(paint);
                    else
                        throw new JsonParseException("Invalid theme background: " + json);
                }
                default -> throw new JsonParseException("Invalid theme background: " + json);
            }
        } else {
            throw new JsonParseException("Invalid theme background: " + json);
        }
    }

    enum BuiltIn implements ThemeBackground {
        DEFAULT,
        CLASSIC
    }

    record Local(String path) implements ThemeBackground {
    }

    record Remote(String url) implements ThemeBackground {
    }

    record Fill(Paint paint) implements ThemeBackground {
    }
}
