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
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.jackhuang.hmcl.game.CompatibilityRule;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public record Theme2(
        @Nullable String version,
        @Nullable Brightness brightness,

        @Nullable ThemeColor2 color,
        @Nullable ColorStyle colorStyle,
        @Nullable ThemeBackground background,
        @Nullable Double backgroundOpacity,
        @Nullable Contrast contrast,

        @NotNull List<CompatibilityRule> rules,
        @NotNull List<Theme2> overrides
) {
    public static final String DEFAULT_VERSION = "1";

    public boolean isResolved() {
        return overrides.isEmpty();
    }

    public Theme2 resolve() {
        if (isResolved())
            return this;

        // TODO: resolve overrides
        return this;
    }

    public static Theme2 fromJson(JsonObject json) throws JsonParseException {
        if (json.get("version") instanceof JsonPrimitive version) {
            if (VersionNumber.compare(version.getAsString(), DEFAULT_VERSION) >= 0)
                throw new JsonParseException("Unsupported theme version: " + version.getAsString());
        }

        Brightness brightness;
        JsonElement brightnessJson = json.get("brightness");
        if (brightnessJson != null) {
            if (brightnessJson instanceof JsonPrimitive primitive)
                brightness = switch (primitive.getAsString().toLowerCase(Locale.ROOT)) {
                    case "light" -> Brightness.LIGHT;
                    case "dark" -> Brightness.DARK;
                    default -> null;
                };
            else
                brightness = null;

            if (brightness == null)
                LOG.warning("Invalid brightness: " + brightnessJson);
        } else {
            brightness = null;
        }

        ThemeColor2 color;
        JsonElement colorJson = json.get("color");
        if (colorJson != null) {
            color = ThemeColor2.of(colorJson.getAsString());
            if (color == null)
                LOG.warning("Invalid color: " + colorJson);
        } else {
            color = null;
        }

        ColorStyle colorStyle;
        JsonElement colorStyleJson = json.get("colorStyle");
        if (colorStyleJson != null) {
            try {
                colorStyle = ColorStyle.valueOf(colorStyleJson.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOG.warning("Invalid color style: " + colorStyleJson);
                colorStyle = null;
            }
        } else {
            colorStyle = null;
        }

        Double backgroundOpacity;
        JsonElement backgroundOpacityJson = json.get("backgroundOpacity");
        if (backgroundOpacityJson != null) {
            if (backgroundOpacityJson instanceof JsonPrimitive primitive) {
                double value = primitive.getAsDouble();
                backgroundOpacity = value >= 0 && value <= 1 ? value : null;
            } else
                backgroundOpacity = null;
            if (backgroundOpacity == null)
                LOG.warning("Invalid background opacity: " + backgroundOpacityJson);
        } else {
            backgroundOpacity = null;
        }

        return new Theme2(
                json.get("version") instanceof JsonPrimitive version ? version.getAsString() : null,
                brightness,
                color,
                colorStyle,
                null,
                backgroundOpacity,
                null,
                List.of(),
                List.of()
        );
    }
}
