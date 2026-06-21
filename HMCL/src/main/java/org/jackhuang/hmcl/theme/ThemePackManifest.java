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
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Parsed metadata and themes from a theme-pack manifest.
///
/// @param id the stable package identifier
/// @param version the package version string
/// @param name the display name
/// @param authors the package authors
/// @param description the optional package description
/// @param themes selectable themes declared by the package
@NotNullByDefault
public record ThemePackManifest(
        String id,
        String version,
        String name,
        @Unmodifiable List<String> authors,
        @Nullable String description,
        @Unmodifiable List<Theme> themes) {

    /// JSON schema for the current manifest format.
    public static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("theme-pack", new JsonSchema.Version(1, 0, 0));

    /// JSON member name for the package ID.
    private static final String FIELD_ID = "id";

    /// JSON member name for the package version.
    private static final String FIELD_VERSION = "version";

    /// JSON member name for the display name.
    private static final String FIELD_NAME = "name";

    /// JSON member name for package authors.
    private static final String FIELD_AUTHORS = "authors";

    /// JSON member name for the package description.
    private static final String FIELD_DESCRIPTION = "description";

    /// JSON member name for theme declarations.
    private static final String FIELD_THEMES = "themes";

    /// Manifest fields accepted by this parser.
    private static final Set<String> FIELDS = Set.of(
            JsonSchema.PROPERTY_SCHEMA,
            FIELD_ID,
            FIELD_VERSION,
            FIELD_NAME,
            FIELD_AUTHORS,
            FIELD_DESCRIPTION,
            FIELD_THEMES);

    /// Creates a theme-pack manifest.
    ///
    /// @param id the stable package identifier
    /// @param version the package version string
    /// @param name the display name
    /// @param authors the package authors
    /// @param description the optional package description
    /// @param themes selectable themes declared by the package
    public ThemePackManifest {
        id = requireNonBlank(id, FIELD_ID);
        version = requireNonBlank(version, FIELD_VERSION);
        name = requireNonBlank(name, FIELD_NAME);
        authors = List.copyOf(authors);
        if (description != null) {
            description = requireNonBlank(description, FIELD_DESCRIPTION);
        }
        themes = List.copyOf(themes);
        if (themes.isEmpty()) {
            throw new IllegalArgumentException("Theme pack must declare at least one theme");
        }
        checkThemeIds(themes);
    }

    /// Parses a theme-pack manifest from a JSON string.
    ///
    /// @param json the manifest JSON
    /// @return the parsed manifest
    /// @throws JsonParseException if the manifest is malformed or unsupported
    public static ThemePackManifest fromJson(String json) throws JsonParseException {
        Objects.requireNonNull(json);

        JsonElement element = JsonParser.parseString(json);
        if (!(element instanceof JsonObject object)) {
            throw new JsonParseException("Theme-pack manifest must be a JSON object");
        }
        return fromJson(object);
    }

    /// Parses a theme-pack manifest from a JSON object.
    ///
    /// @param object the manifest JSON object
    /// @return the parsed manifest
    /// @throws JsonParseException if the manifest is malformed or unsupported
    public static ThemePackManifest fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);
        checkUnknownFields(object);
        checkSchema(object);

        return new ThemePackManifest(
                requireMemberString(object, FIELD_ID),
                requireMemberString(object, FIELD_VERSION),
                requireMemberString(object, FIELD_NAME),
                readAuthors(object),
                readString(object, FIELD_DESCRIPTION),
                readThemes(object));
    }

    /// Returns the first theme with the given ID.
    ///
    /// A `null` theme ID matches only a single-theme manifest.
    ///
    /// @param themeId the theme ID to find, or `null` for an unnamed single-theme manifest
    /// @return the matching theme, or `null` when no theme matches
    public @Nullable Theme findTheme(@Nullable String themeId) {
        if (themeId == null) {
            return themes.size() == 1 ? themes.get(0) : null;
        }

        for (Theme theme : themes) {
            if (themeId.equals(theme.id())) {
                return theme;
            }
        }
        return null;
    }

    /// Converts this manifest to its JSON object representation.
    ///
    /// @return the JSON object representing this manifest
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        object.addProperty(JsonSchema.PROPERTY_SCHEMA, CURRENT_SCHEMA.url());
        object.addProperty(FIELD_ID, id);
        object.addProperty(FIELD_VERSION, version);
        object.addProperty(FIELD_NAME, name);

        if (!authors.isEmpty()) {
            JsonArray array = new JsonArray();
            for (String author : authors) {
                array.add(author);
            }
            object.add(FIELD_AUTHORS, array);
        }
        if (description != null) {
            object.addProperty(FIELD_DESCRIPTION, description);
        }

        JsonArray themeArray = new JsonArray();
        for (Theme theme : themes) {
            themeArray.add(theme.toJsonObject());
        }
        object.add(FIELD_THEMES, themeArray);
        return object;
    }

    /// Serializes this manifest to formatted JSON.
    ///
    /// @return the formatted manifest JSON
    public String toJson() {
        return JsonUtils.GSON.toJson(toJsonObject());
    }

    /// Checks that no unsupported manifest fields are present.
    private static void checkUnknownFields(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!FIELDS.contains(entry.getKey())) {
                throw new JsonParseException("Unsupported theme-pack manifest field: " + entry.getKey());
            }
        }
    }

    /// Checks that the manifest declares the supported schema.
    private static void checkSchema(JsonObject object) {
        JsonSchema.CompatibilityResult result = JsonSchema.check(object, CURRENT_SCHEMA);
        if (!result.readable()) {
            throw new JsonParseException("Unsupported theme-pack schema: " + schemaDescription(result));
        }
    }

    /// Returns a compact schema description for parse errors.
    private static String schemaDescription(JsonSchema.CompatibilityResult result) {
        if (result.actual() != null) {
            return result.actual().url();
        }
        if (result.invalidValue() != null) {
            return result.invalidValue();
        }
        return result.status().name();
    }

    /// Reads the required authors list.
    private static List<String> readAuthors(JsonObject object) {
        JsonElement element = object.get(FIELD_AUTHORS);
        if (element == null) {
            return List.of();
        }
        if (!(element instanceof JsonArray array)) {
            throw new JsonParseException("Theme-pack authors must be an array");
        }

        ArrayList<String> authors = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (!(item instanceof JsonPrimitive primitive) || !primitive.isString()) {
                throw new JsonParseException("Theme-pack authors must contain only strings");
            }
            authors.add(requireNonBlank(primitive.getAsString(), FIELD_AUTHORS));
        }
        return authors;
    }

    /// Reads the required themes list.
    private static List<Theme> readThemes(JsonObject object) {
        JsonElement element = object.get(FIELD_THEMES);
        if (!(element instanceof JsonArray array)) {
            throw new JsonParseException("Theme-pack manifest is missing themes array");
        }
        if (array.isEmpty()) {
            throw new JsonParseException("Theme-pack manifest must declare at least one theme");
        }

        ArrayList<Theme> themes = new ArrayList<>(array.size());
        boolean requireThemeId = array.size() > 1;
        for (JsonElement item : array) {
            if (!(item instanceof JsonObject themeObject)) {
                throw new JsonParseException("Theme-pack theme must be an object");
            }
            themes.add(Theme.fromJson(themeObject, requireThemeId));
        }
        return themes;
    }

    /// Checks that theme IDs are present whenever the manifest needs them for disambiguation.
    private static void checkThemeIds(List<Theme> themes) {
        if (themes.size() <= 1) {
            return;
        }

        for (Theme theme : themes) {
            if (theme.id() == null) {
                throw new IllegalArgumentException("Theme ID is required when a theme pack declares multiple themes");
            }
        }
    }

    /// Reads a required string member.
    private static String requireMemberString(JsonObject object, String field) {
        @Nullable String value = readString(object, field);
        if (value == null) {
            throw new JsonParseException("Theme-pack manifest is missing required string field: " + field);
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
            throw new JsonParseException("Theme-pack manifest field must be a string: " + field);
        }
        return primitive.getAsString();
    }

    /// Returns a non-blank string value.
    private static String requireNonBlank(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme-pack manifest field is blank: " + field);
        }
        return trimmed;
    }
}
