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
package org.jackhuang.hmcl.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/// Appearance values contributed by a theme or override.
///
/// All fields are optional so the same type can represent both a default appearance
/// and a conditional patch.
///
/// @param color the color seed source, or `null` when inherited
/// @param brightness the controlled brightness, or `null` when inherited by a patch or not controlled by a resolved appearance
/// @param colorStyle the MonetFX color style, or `null` when inherited
/// @param contrast the MonetFX contrast level, or `null` when inherited
/// @param background the background settings, or `null` when inherited
/// @param titleBar the title-bar settings, or `null` when inherited
@NotNullByDefault
public record ThemeAppearance(
        @Nullable ThemeColorSource color,
        @Nullable Brightness brightness,
        @Nullable ColorStyle colorStyle,
        @Nullable Contrast contrast,
        @Nullable ThemeBackground background,
        @Nullable ThemeTitleBar titleBar) {

    /// JSON member name for the color seed.
    static final String FIELD_COLOR = "color";

    /// JSON member name for the controlled launcher brightness.
    static final String FIELD_BRIGHTNESS = "brightness";

    /// JSON member name for the MonetFX color style.
    static final String FIELD_COLOR_STYLE = "colorStyle";

    /// JSON member name for the MonetFX contrast level.
    static final String FIELD_CONTRAST = "contrast";

    /// JSON member name for background settings.
    static final String FIELD_BACKGROUND = "background";

    /// JSON member name for title-bar settings.
    static final String FIELD_TITLE_BAR = "titleBar";

    /// Creates an appearance patch.
    ///
    /// @param color the color seed source, or `null` when inherited
    /// @param brightness the controlled brightness, or `null` when inherited by a patch or not controlled by a resolved appearance
    /// @param colorStyle the MonetFX color style, or `null` when inherited
    /// @param contrast the MonetFX contrast level, or `null` when inherited
    /// @param background the background settings, or `null` when inherited
    /// @param titleBar the title-bar settings, or `null` when inherited
    public ThemeAppearance {
        if (background != null && background.isEmpty()) {
            background = null;
        }
        if (titleBar != null && titleBar.isEmpty()) {
            titleBar = null;
        }
    }

    /// Parses known appearance fields from a JSON object.
    ///
    /// @param object the JSON object containing appearance fields
    /// @return the parsed appearance patch
    /// @throws JsonParseException if a known appearance field is malformed
    static ThemeAppearance fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        return new ThemeAppearance(
                readColor(object),
                readBrightness(object),
                readColorStyle(object),
                readContrast(object),
                readBackground(object),
                readTitleBar(object));
    }

    /// Returns whether this appearance contains no concrete fields.
    ///
    /// @return `true` when every appearance field is inherited
    public boolean isEmpty() {
        return color == null
                && brightness == null
                && colorStyle == null
                && contrast == null
                && background == null
                && titleBar == null;
    }

    /// Adds this appearance patch's concrete fields to a JSON object.
    ///
    /// @param object the target JSON object
    void addToJsonObject(JsonObject object) {
        Objects.requireNonNull(object);

        if (color != null) {
            object.add(FIELD_COLOR, color.toJsonElement());
        }
        if (brightness != null) {
            object.addProperty(FIELD_BRIGHTNESS, toJsonBrightness(brightness));
        }
        if (colorStyle != null) {
            object.addProperty(FIELD_COLOR_STYLE, colorStyle.name().toLowerCase(Locale.ROOT));
        }
        if (contrast != null) {
            addContrast(object, contrast);
        }
        if (background != null) {
            object.add(FIELD_BACKGROUND, background.toJsonObject());
        }
        if (titleBar != null) {
            object.add(FIELD_TITLE_BAR, titleBar.toJsonObject());
        }
    }

    /// Converts this appearance patch to its JSON representation.
    ///
    /// @return the JSON object representing this appearance patch
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        addToJsonObject(object);
        return object;
    }

    /// Applies the given patch over this appearance.
    ///
    /// @param patch the patch to apply
    /// @return the merged appearance
    public ThemeAppearance merge(ThemeAppearance patch) {
        Objects.requireNonNull(patch);

        return new ThemeAppearance(
                patch.color != null ? patch.color : color,
                patch.brightness != null ? patch.brightness : brightness,
                patch.colorStyle != null ? patch.colorStyle : colorStyle,
                patch.contrast != null ? patch.contrast : contrast,
                background != null && patch.background != null
                        ? background.merge(patch.background)
                        : patch.background != null ? patch.background : background,
                titleBar != null && patch.titleBar != null
                        ? titleBar.merge(patch.titleBar)
                        : patch.titleBar != null ? patch.titleBar : titleBar);
    }

    /// Converts this appearance to concrete launcher theme values.
    ///
    /// @param context the resolution context that provides fallback brightness when this appearance does not control it
    /// @return the concrete theme used by MonetFX
    public ResolvedTheme toResolvedTheme(ThemeResolveContext context) {
        Objects.requireNonNull(context);

        ThemeColor resolvedColor = color != null ? color.resolveFallback() : ResolvedTheme.DEFAULT.primaryColorSeed();
        Brightness resolvedBrightness = brightness != null ? brightness : context.brightness();
        ColorStyle resolvedColorStyle = colorStyle != null ? colorStyle : ResolvedTheme.DEFAULT.colorStyle();
        Contrast resolvedContrast = contrast != null ? contrast : Contrast.DEFAULT;

        return new ResolvedTheme(resolvedColor, resolvedBrightness, resolvedColorStyle, resolvedContrast);
    }

    /// Reads the optional color field.
    private static @Nullable ThemeColorSource readColor(JsonObject object) {
        JsonElement element = object.get(FIELD_COLOR);
        if (element == null) {
            return null;
        }
        return ThemeColorSource.fromJson(element);
    }

    /// Reads the optional controlled brightness field.
    private static @Nullable Brightness readBrightness(JsonObject object) {
        @Nullable String value = readString(object, FIELD_BRIGHTNESS);
        if (value == null) {
            return null;
        }

        try {
            return parseBrightness(value);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid theme brightness: " + value, e);
        }
    }

    /// Reads the optional color style field.
    private static @Nullable ColorStyle readColorStyle(JsonObject object) {
        @Nullable String value = readString(object, FIELD_COLOR_STYLE);
        if (value == null) {
            return null;
        }

        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return ColorStyle.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid theme color style: " + value, e);
        }
    }

    /// Reads the optional contrast field.
    private static @Nullable Contrast readContrast(JsonObject object) {
        JsonElement element = object.get(FIELD_CONTRAST);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive)) {
            throw new JsonParseException("Theme contrast must be a string or number");
        }

        if (primitive.isNumber()) {
            return readContrastValue(primitive.getAsDouble());
        }
        if (!primitive.isString()) {
            throw new JsonParseException("Theme contrast must be a string or number");
        }

        String value = primitive.getAsString();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "low" -> Contrast.LOW;
            case "standard", "default" -> Contrast.DEFAULT;
            case "medium" -> Contrast.MEDIUM;
            case "high" -> Contrast.HIGH;
            default -> {
                try {
                    yield readContrastValue(Double.parseDouble(normalized));
                } catch (NumberFormatException e) {
                    throw new JsonParseException("Invalid theme contrast: " + value, e);
                }
            }
        };
    }

    /// Reads the optional background object.
    private static @Nullable ThemeBackground readBackground(JsonObject object) {
        JsonElement element = object.get(FIELD_BACKGROUND);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonObject background)) {
            throw new JsonParseException("Theme background must be an object");
        }
        return ThemeBackground.fromJson(background);
    }

    /// Reads the optional title-bar object.
    private static @Nullable ThemeTitleBar readTitleBar(JsonObject object) {
        JsonElement element = object.get(FIELD_TITLE_BAR);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonObject titleBar)) {
            throw new JsonParseException("Theme titleBar must be an object");
        }
        return ThemeTitleBar.fromJson(titleBar);
    }

    /// Reads an optional string field.
    private static @Nullable String readString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Theme field must be a string: " + field);
        }
        return primitive.getAsString();
    }

    /// Returns a validated contrast value.
    private static Contrast readContrastValue(double value) {
        try {
            return Contrast.of(value);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Theme contrast value must be between -1 and 1: " + value, e);
        }
    }

    /// Adds a canonical contrast value to a JSON object.
    private static void addContrast(JsonObject object, Contrast contrast) {
        if (contrast.equals(Contrast.LOW)) {
            object.addProperty(FIELD_CONTRAST, "low");
        } else if (contrast.equals(Contrast.DEFAULT)) {
            object.addProperty(FIELD_CONTRAST, "default");
        } else if (contrast.equals(Contrast.MEDIUM)) {
            object.addProperty(FIELD_CONTRAST, "medium");
        } else if (contrast.equals(Contrast.HIGH)) {
            object.addProperty(FIELD_CONTRAST, "high");
        } else {
            object.addProperty(FIELD_CONTRAST, contrast.getValue());
        }
    }

    /// Parses a serialized theme brightness value.
    private static Brightness parseBrightness(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "light" -> Brightness.LIGHT;
            case "dark" -> Brightness.DARK;
            default -> throw new IllegalArgumentException("Unsupported theme brightness: " + value);
        };
    }

    /// Converts a controlled brightness value to its canonical JSON string.
    private static String toJsonBrightness(Brightness brightness) {
        return switch (brightness) {
            case LIGHT -> "light";
            case DARK -> "dark";
        };
    }
}
