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
/// @param formatVersion the theme-pack manifest format version
/// @param id the stable package identifier
/// @param version the package version string
/// @param name the display name
/// @param authors the package authors
/// @param description the optional package description
/// @param themes selectable themes declared by the package
@NotNullByDefault
public record ThemePackManifest(
        int formatVersion,
        String id,
        String version,
        String name,
        @Unmodifiable List<String> authors,
        @Nullable String description,
        @Unmodifiable List<ThemePreset> themes) {

    /// The manifest format supported by this implementation.
    public static final int CURRENT_FORMAT_VERSION = 1;

    /// JSON member name for the format version.
    private static final String FIELD_FORMAT_VERSION = "formatVersion";

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

    /// JSON member name for an optional schema marker.
    private static final String FIELD_SCHEMA = "$schema";

    /// Manifest fields accepted by this parser.
    private static final Set<String> FIELDS = Set.of(
            FIELD_SCHEMA,
            FIELD_FORMAT_VERSION,
            FIELD_ID,
            FIELD_VERSION,
            FIELD_NAME,
            FIELD_AUTHORS,
            FIELD_DESCRIPTION,
            FIELD_THEMES);

    /// Creates a theme-pack manifest.
    ///
    /// @param formatVersion the theme-pack manifest format version
    /// @param id the stable package identifier
    /// @param version the package version string
    /// @param name the display name
    /// @param authors the package authors
    /// @param description the optional package description
    /// @param themes selectable themes declared by the package
    public ThemePackManifest {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported theme-pack format version: " + formatVersion);
        }
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

        int formatVersion = readFormatVersion(object);
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new JsonParseException("Unsupported theme-pack format version: " + formatVersion);
        }

        return new ThemePackManifest(
                formatVersion,
                requireMemberString(object, FIELD_ID),
                requireMemberString(object, FIELD_VERSION),
                requireMemberString(object, FIELD_NAME),
                readAuthors(object),
                readString(object, FIELD_DESCRIPTION),
                readThemes(object));
    }

    /// Returns the first theme with the given ID.
    ///
    /// @param themeId the theme ID to find
    /// @return the matching theme, or `null` when no theme has that ID
    public @Nullable ThemePreset findTheme(String themeId) {
        Objects.requireNonNull(themeId);

        for (ThemePreset theme : themes) {
            if (theme.id().equals(themeId)) {
                return theme;
            }
        }
        return null;
    }

    /// Checks that no unsupported manifest fields are present.
    private static void checkUnknownFields(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!FIELDS.contains(entry.getKey())) {
                throw new JsonParseException("Unsupported theme-pack manifest field: " + entry.getKey());
            }
        }
    }

    /// Reads the required manifest format version.
    private static int readFormatVersion(JsonObject object) {
        JsonElement element = object.get(FIELD_FORMAT_VERSION);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new JsonParseException("Theme-pack manifest is missing numeric formatVersion");
        }
        return primitive.getAsInt();
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
    private static List<ThemePreset> readThemes(JsonObject object) {
        JsonElement element = object.get(FIELD_THEMES);
        if (!(element instanceof JsonArray array)) {
            throw new JsonParseException("Theme-pack manifest is missing themes array");
        }
        if (array.isEmpty()) {
            throw new JsonParseException("Theme-pack manifest must declare at least one theme");
        }

        ArrayList<ThemePreset> themes = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (!(item instanceof JsonObject themeObject)) {
                throw new JsonParseException("Theme-pack theme must be an object");
            }
            themes.add(ThemePreset.fromJson(themeObject));
        }
        return themes;
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
