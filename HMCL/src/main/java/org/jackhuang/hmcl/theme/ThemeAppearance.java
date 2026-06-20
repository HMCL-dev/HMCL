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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Appearance values contributed by a theme preset or override.
///
/// All fields are optional so the same type can represent both a default appearance
/// and a conditional patch.
///
/// @param primaryColor the primary color seed, or `null` when inherited
/// @param brightness the brightness directive, or `null` when inherited
/// @param colorStyle the MonetFX color style, or `null` when inherited
/// @param contrast the MonetFX contrast level, or `null` when inherited
/// @param background the background settings, or `null` when inherited
/// @param titleTransparent whether the title area should be transparent, or `null` when inherited
@NotNullByDefault
public record ThemeAppearance(
        @Nullable ThemeColor primaryColor,
        @Nullable ThemeBrightness brightness,
        @Nullable ColorStyle colorStyle,
        @Nullable Contrast contrast,
        @Nullable ThemeBackground background,
        @Nullable Boolean titleTransparent) {

    /// JSON member name for the primary color seed.
    static final String FIELD_PRIMARY_COLOR = "primaryColor";

    /// JSON member name for the brightness directive.
    static final String FIELD_BRIGHTNESS = "brightness";

    /// JSON member name for the MonetFX color style.
    static final String FIELD_COLOR_STYLE = "colorStyle";

    /// JSON member name for the MonetFX contrast level.
    static final String FIELD_CONTRAST = "contrast";

    /// JSON member name for background settings.
    static final String FIELD_BACKGROUND = "background";

    /// JSON member name for title-area transparency.
    static final String FIELD_TITLE_TRANSPARENT = "titleTransparent";

    /// Field names accepted as appearance fields.
    static final Set<String> FIELDS = Set.of(
            FIELD_PRIMARY_COLOR,
            FIELD_BRIGHTNESS,
            FIELD_COLOR_STYLE,
            FIELD_CONTRAST,
            FIELD_BACKGROUND,
            FIELD_TITLE_TRANSPARENT);

    /// Creates an appearance patch.
    ///
    /// @param primaryColor the primary color seed, or `null` when inherited
    /// @param brightness the brightness directive, or `null` when inherited
    /// @param colorStyle the MonetFX color style, or `null` when inherited
    /// @param contrast the MonetFX contrast level, or `null` when inherited
    /// @param background the background settings, or `null` when inherited
    /// @param titleTransparent whether the title area should be transparent, or `null` when inherited
    public ThemeAppearance {
        if (background != null && background.isEmpty()) {
            background = null;
        }
    }

    /// Parses appearance fields from a JSON object while ignoring known metadata fields.
    ///
    /// @param object the JSON object containing appearance fields
    /// @param ignoredFields non-appearance fields accepted in the same object
    /// @param sourceName the source name used in parse error messages
    /// @return the parsed appearance patch
    /// @throws JsonParseException if an unsupported or malformed appearance field is present
    static ThemeAppearance fromJson(JsonObject object, Set<String> ignoredFields, String sourceName) throws JsonParseException {
        Objects.requireNonNull(object);
        Objects.requireNonNull(ignoredFields);
        Objects.requireNonNull(sourceName);
        checkUnknownFields(object, ignoredFields, sourceName);

        return new ThemeAppearance(
                readPrimaryColor(object),
                readBrightness(object),
                readColorStyle(object),
                readContrast(object),
                readBackground(object),
                readTitleTransparent(object));
    }

    /// Returns whether this appearance contains no concrete fields.
    ///
    /// @return `true` when every appearance field is inherited
    public boolean isEmpty() {
        return primaryColor == null
                && brightness == null
                && colorStyle == null
                && contrast == null
                && background == null
                && titleTransparent == null;
    }

    /// Adds this appearance patch's concrete fields to a JSON object.
    ///
    /// @param object the target JSON object
    void addToJsonObject(JsonObject object) {
        Objects.requireNonNull(object);

        if (primaryColor != null) {
            object.addProperty(FIELD_PRIMARY_COLOR, primaryColor.name());
        }
        if (brightness != null) {
            object.addProperty(FIELD_BRIGHTNESS, brightness.toJsonValue());
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
        if (titleTransparent != null) {
            object.addProperty(FIELD_TITLE_TRANSPARENT, titleTransparent);
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
                patch.primaryColor != null ? patch.primaryColor : primaryColor,
                patch.brightness != null ? patch.brightness : brightness,
                patch.colorStyle != null ? patch.colorStyle : colorStyle,
                patch.contrast != null ? patch.contrast : contrast,
                background != null && patch.background != null
                        ? background.merge(patch.background)
                        : patch.background != null ? patch.background : background,
                patch.titleTransparent != null ? patch.titleTransparent : titleTransparent);
    }

    /// Converts this appearance to the existing launcher [Theme] model.
    ///
    /// @param context the resolution context that provides adaptive brightness
    /// @return the concrete theme used by MonetFX
    public Theme toTheme(ThemeResolveContext context) {
        Objects.requireNonNull(context);

        ThemeColor resolvedPrimaryColor = primaryColor != null ? primaryColor : Theme.DEFAULT.primaryColorSeed();
        Brightness resolvedBrightness = brightness != null
                ? brightness.resolve(context.brightness())
                : context.brightness();
        ColorStyle resolvedColorStyle = colorStyle != null ? colorStyle : Theme.DEFAULT.colorStyle();
        Contrast resolvedContrast = contrast != null ? contrast : Contrast.DEFAULT;

        return new Theme(resolvedPrimaryColor, resolvedBrightness, resolvedColorStyle, resolvedContrast);
    }

    /// Checks that no unsupported fields are present.
    private static void checkUnknownFields(JsonObject object, Set<String> ignoredFields, String sourceName) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            if (!FIELDS.contains(key) && !ignoredFields.contains(key)) {
                throw new JsonParseException("Unsupported theme " + sourceName + " field: " + key);
            }
        }
    }

    /// Reads the optional primary color field.
    private static @Nullable ThemeColor readPrimaryColor(JsonObject object) {
        @Nullable String value = readString(object, FIELD_PRIMARY_COLOR);
        if (value == null) {
            return null;
        }

        @Nullable ThemeColor color = ThemeColor.of(value);
        if (color == null) {
            throw new JsonParseException("Invalid theme primary color: " + value);
        }
        return color;
    }

    /// Reads the optional brightness directive field.
    private static @Nullable ThemeBrightness readBrightness(JsonObject object) {
        @Nullable String value = readString(object, FIELD_BRIGHTNESS);
        if (value == null) {
            return null;
        }

        try {
            return ThemeBrightness.parse(value);
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

    /// Reads the optional title-transparent field.
    private static @Nullable Boolean readTitleTransparent(JsonObject object) {
        JsonElement element = object.get(FIELD_TITLE_TRANSPARENT);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new JsonParseException("Theme titleTransparent must be a boolean");
        }
        return primitive.getAsBoolean();
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
}
