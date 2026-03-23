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
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public record Theme(
        @Nullable String version,

        @Nullable Brightness brightness,
        @Nullable ThemeColor2 color,
        @Nullable ColorStyle colorStyle,
        @Nullable ThemeBackground background,
        @Nullable Double backgroundOpacity,
        @Nullable Contrast contrast,

        @NotNull List<CompatibilityRule> rules,
        @NotNull List<Theme> overrides
) {
    public static final String DEFAULT_VERSION = "1";

    public boolean isResolved() {
        return overrides.isEmpty();
    }

    public Theme resolve(Map<String, Boolean> features) {
        if (isResolved())
            return this;

        String version = this.version;
        Brightness brightness = this.brightness;
        ThemeColor2 color = this.color;
        ColorStyle colorStyle = this.colorStyle;
        ThemeBackground background = this.background;
        Double backgroundOpacity = this.backgroundOpacity;
        Contrast contrast = this.contrast;

        boolean hasOverride = false;
        for (Theme override : overrides) {
            if (!override.rules().isEmpty()) {
                if (!CompatibilityRule.appliesToCurrentEnvironment(override.rules(), features)) {
                    continue;
                }
            }

            hasOverride = true;

            if (override.brightness != null)
                brightness = override.brightness;
            if (override.color != null)
                color = override.color;
            if (override.colorStyle != null)
                colorStyle = override.colorStyle;
            if (override.background != null)
                background = override.background;
            if (override.backgroundOpacity != null)
                backgroundOpacity = override.backgroundOpacity;
            if (override.contrast != null)
                contrast = override.contrast;
        }

        return hasOverride ? new Theme(
                version,
                brightness,
                color,
                colorStyle,
                background,
                backgroundOpacity,
                contrast,
                rules,
                overrides
        ) : this;
    }

    public static Theme fromJson(JsonObject json) throws JsonParseException {
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

        ThemeBackground background;
        JsonElement backgroundJson = json.get("background");
        if (backgroundJson != null) {
            try {
                background = ThemeBackground.fromJson(backgroundJson);
            } catch (Exception e) {
                LOG.warning("Invalid theme background: " + backgroundJson, e);
                background = null;
            }
        } else {
            background = null;
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

        Contrast contrast;
        JsonElement contrastJson = json.get("contrast");
        if (contrastJson != null) {
            if (contrastJson instanceof JsonPrimitive primitive)
                contrast = switch (primitive.getAsString().toLowerCase(Locale.ROOT)) {
                    case "high" -> Contrast.HIGH;
                    case "low" -> Contrast.LOW;
                    default -> null;
                };
            else
                contrast = null;

            if (contrast == null)
                LOG.warning("Invalid contrast: " + contrastJson);
        } else {
            contrast = null;
        }

        return new Theme(
                json.get("version") instanceof JsonPrimitive version ? version.getAsString() : null,
                brightness,
                color,
                colorStyle,
                background,
                backgroundOpacity,
                contrast,
                List.of(),
                List.of()
        );
    }
}
