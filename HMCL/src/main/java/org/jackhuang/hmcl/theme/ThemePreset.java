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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// One selectable theme inside a theme pack.
///
/// The theme object's top-level appearance fields provide default values. Each
/// matching override is applied in array order to produce the resolved appearance.
///
/// @param id the stable theme identifier inside its pack
/// @param name the display name
/// @param description the optional description
/// @param family the optional family identifier used to group related themes
/// @param thumbnail the optional theme-pack relative thumbnail path
/// @param appearance the default appearance fields
/// @param overrides conditional appearance patches applied in declaration order
@NotNullByDefault
public record ThemePreset(
        String id,
        String name,
        @Nullable String description,
        @Nullable String family,
        @Nullable String thumbnail,
        ThemeAppearance appearance,
        @Unmodifiable List<ThemeOverride> overrides) {

    /// JSON member name for the theme ID.
    private static final String FIELD_ID = "id";

    /// JSON member name for the display name.
    private static final String FIELD_NAME = "name";

    /// JSON member name for the optional description.
    private static final String FIELD_DESCRIPTION = "description";

    /// JSON member name for the optional theme family.
    private static final String FIELD_FAMILY = "family";

    /// JSON member name for the optional thumbnail path.
    private static final String FIELD_THUMBNAIL = "thumbnail";

    /// JSON member name for conditional overrides.
    private static final String FIELD_OVERRIDES = "overrides";

    /// Non-appearance fields accepted by a theme object.
    private static final Set<String> IGNORED_FIELDS = Set.of(
            FIELD_ID,
            FIELD_NAME,
            FIELD_DESCRIPTION,
            FIELD_FAMILY,
            FIELD_THUMBNAIL,
            FIELD_OVERRIDES);

    /// Creates a theme preset.
    ///
    /// @param id the stable theme identifier inside its pack
    /// @param name the display name
    /// @param description the optional description
    /// @param family the optional family identifier used to group related themes
    /// @param thumbnail the optional theme-pack relative thumbnail path
    /// @param appearance the default appearance fields
    /// @param overrides conditional appearance patches applied in declaration order
    public ThemePreset {
        id = requireNonBlank(id, FIELD_ID);
        name = requireNonBlank(name, FIELD_NAME);
        if (description != null) {
            description = requireNonBlank(description, FIELD_DESCRIPTION);
        }
        if (family != null) {
            family = requireNonBlank(family, FIELD_FAMILY);
        }
        if (thumbnail != null) {
            thumbnail = requireNonBlank(thumbnail, FIELD_THUMBNAIL);
        }
        Objects.requireNonNull(appearance);
        overrides = List.copyOf(overrides);
    }

    /// Parses a theme preset from JSON.
    ///
    /// @param object the theme JSON object
    /// @return the parsed theme preset
    /// @throws JsonParseException if the theme object is malformed
    static ThemePreset fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        String id = requireMemberString(object, FIELD_ID);
        String name = requireMemberString(object, FIELD_NAME);
        ThemeAppearance appearance = ThemeAppearance.fromJson(object, IGNORED_FIELDS, "preset");

        return new ThemePreset(
                id,
                name,
                readString(object, FIELD_DESCRIPTION),
                readString(object, FIELD_FAMILY),
                readString(object, FIELD_THUMBNAIL),
                appearance,
                readOverrides(object));
    }

    /// Resolves this preset against a context by applying matching overrides.
    ///
    /// @param context the resolution context
    /// @return the resolved appearance
    public ThemeAppearance resolve(ThemeResolveContext context) {
        Objects.requireNonNull(context);

        ThemeAppearance resolved = appearance;
        for (ThemeOverride override : overrides) {
            if (override.matches(context)) {
                resolved = resolved.merge(override.appearance());
            }
        }
        return resolved;
    }

    /// Resolves this preset and converts it to the existing launcher [Theme] model.
    ///
    /// @param context the resolution context
    /// @return the concrete theme used by MonetFX
    public Theme toTheme(ThemeResolveContext context) {
        return resolve(context).toTheme(context);
    }

    /// Reads all conditional override objects.
    private static List<ThemeOverride> readOverrides(JsonObject object) {
        JsonElement element = object.get(FIELD_OVERRIDES);
        if (element == null) {
            return List.of();
        }
        if (!(element instanceof JsonArray array)) {
            throw new JsonParseException("Theme overrides must be an array");
        }

        ArrayList<ThemeOverride> overrides = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (!(item instanceof JsonObject overrideObject)) {
                throw new JsonParseException("Theme override must be an object");
            }
            overrides.add(ThemeOverride.fromJson(overrideObject));
        }
        return overrides;
    }

    /// Reads a required string member.
    private static String requireMemberString(JsonObject object, String field) {
        @Nullable String value = readString(object, field);
        if (value == null) {
            throw new JsonParseException("Theme preset is missing required string field: " + field);
        }
        return value;
    }

    /// Reads an optional string member.
    private static @Nullable String readString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Theme preset field must be a string: " + field);
        }
        return primitive.getAsString();
    }

    /// Returns a non-blank string value.
    private static String requireNonBlank(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme preset field is blank: " + field);
        }
        return trimmed;
    }
}
