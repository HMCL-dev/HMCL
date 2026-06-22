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
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Parsed metadata and themes from a theme-pack manifest.
///
/// @param id the stable package identifier
/// @param version the package version string
/// @param name the localized display name
/// @param authors the package authors
/// @param description the optional localized package description
/// @param themes selectable themes declared by the package
@NotNullByDefault
public record ThemePackManifest(
        String id,
        String version,
        LocalizedText name,
        @Unmodifiable List<ThemePackAuthor> authors,
        @Nullable LocalizedText description,
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

    /// JSON member name for a single theme declaration.
    private static final String FIELD_THEME = "theme";

    /// JSON member name for multiple theme declarations.
    private static final String FIELD_THEMES = "themes";

    /// Fallback package version used when the manifest version field is malformed.
    private static final String DEFAULT_VERSION = "1.0.0";

    /// Package ID format that can be used directly as an installed theme-pack file name.
    private static final Pattern PACKAGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /// Creates a theme-pack manifest.
    ///
    /// @param id the stable package identifier
    /// @param version the package version string
    /// @param name the localized display name
    /// @param authors the package authors
    /// @param description the optional localized package description
    /// @param themes selectable themes declared by the package
    public ThemePackManifest {
        id = requirePackageId(id);
        version = requireNonBlank(version, FIELD_VERSION);
        name = requireLocalizedText(name, FIELD_NAME);
        authors = List.copyOf(authors);
        if (description != null) {
            description = requireLocalizedText(description, FIELD_DESCRIPTION);
        }
        themes = List.copyOf(themes);
        if (themes.isEmpty()) {
            throw new IllegalArgumentException("Theme pack must declare at least one theme");
        }
        checkThemeIdentities(themes);
    }

    /// Parses a theme-pack manifest from a JSON string.
    ///
    /// @param json the manifest JSON
    /// @return the parsed manifest
    /// @throws JsonParseException if the manifest structure or schema is unsupported
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
    /// @throws JsonParseException if the manifest structure or schema is unsupported
    public static ThemePackManifest fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);
        checkSchema(object);

        String id = requireMemberString(object, FIELD_ID);
        @Nullable String version = readOptionalValue(FIELD_VERSION, () -> requireMemberString(object, FIELD_VERSION));
        @Nullable LocalizedText name = readOptionalValue(
                FIELD_NAME,
                () -> requireMemberLocalizedText(object, FIELD_NAME));

        return new ThemePackManifest(
                id,
                Objects.requireNonNullElse(version, DEFAULT_VERSION),
                Objects.requireNonNullElse(name, LocalizedText.plain(id)),
                readAuthors(object),
                readOptionalValue(FIELD_DESCRIPTION, () -> readLocalizedText(object, FIELD_DESCRIPTION)),
                readThemes(object));
    }

    /// Returns the package display name in the current locale.
    ///
    /// @return the localized display name, or the package ID when no localized text matches
    public String displayName() {
        return Objects.requireNonNullElse(name.getText(I18n.getLocale().getCandidateLocales()), id);
    }

    /// Returns the package description in the current locale.
    ///
    /// @return the localized description, or `null` when no localized text matches
    public @Nullable String displayDescription() {
        return description != null ? description.getText(I18n.getLocale().getCandidateLocales()) : null;
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
        object.add(FIELD_NAME, JsonUtils.GSON.toJsonTree(name, LocalizedText.class));

        if (!authors.isEmpty()) {
            JsonArray array = new JsonArray();
            for (ThemePackAuthor author : authors) {
                array.add(author.toJsonObject());
            }
            object.add(FIELD_AUTHORS, array);
        }
        if (description != null) {
            object.add(FIELD_DESCRIPTION, JsonUtils.GSON.toJsonTree(description, LocalizedText.class));
        }

        if (themes.size() == 1) {
            object.add(FIELD_THEME, themes.get(0).toJsonObject());
        } else {
            JsonArray themeArray = new JsonArray();
            for (Theme theme : themes) {
                themeArray.add(theme.toJsonObject());
            }
            object.add(FIELD_THEMES, themeArray);
        }
        return object;
    }

    /// Serializes this manifest to formatted JSON.
    ///
    /// @return the formatted manifest JSON
    public String toJson() {
        return JsonUtils.GSON.toJson(toJsonObject());
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

    /// Reads the authors list.
    private static List<ThemePackAuthor> readAuthors(JsonObject object) {
        JsonElement element = object.get(FIELD_AUTHORS);
        if (element == null) {
            return List.of();
        }
        if (!(element instanceof JsonArray array)) {
            LOG.warning("Ignored invalid theme-pack authors: expected an array, got " + element);
            return List.of();
        }

        ArrayList<ThemePackAuthor> authors = new ArrayList<>(array.size());
        int index = 0;
        for (JsonElement item : array) {
            String field = FIELD_AUTHORS + "[" + index + "]";
            if (item instanceof JsonObject authorObject) {
                @Nullable ThemePackAuthor author = readOptionalValue(
                        field,
                        () -> new ThemePackAuthor(requireAuthorName(authorObject)));
                if (author != null) {
                    authors.add(author);
                }
            } else {
                LOG.warning("Ignored invalid theme-pack author `" + field + "`: expected an object, got " + item);
            }
            index++;
        }
        return authors;
    }

    /// Reads an author display name.
    private static LocalizedText requireAuthorName(JsonObject object) {
        JsonElement element = object.get(ThemePackAuthor.FIELD_NAME);
        if (element == null) {
            throw new JsonParseException("Theme-pack author is missing required localized text field: "
                    + ThemePackAuthor.FIELD_NAME);
        }
        return parseLocalizedText(element, ThemePackAuthor.FIELD_NAME);
    }

    /// Reads the required theme declaration.
    private static List<Theme> readThemes(JsonObject object) {
        boolean hasSingleTheme = object.has(FIELD_THEME);
        boolean hasMultipleThemes = object.has(FIELD_THEMES);
        if (hasSingleTheme == hasMultipleThemes) {
            throw new JsonParseException("Theme-pack manifest must declare exactly one of theme or themes");
        }

        if (hasSingleTheme) {
            JsonElement element = object.get(FIELD_THEME);
            if (!(element instanceof JsonObject themeObject)) {
                throw new JsonParseException("Theme-pack theme must be an object");
            }
            return List.of(Theme.fromJson(themeObject, false));
        }

        JsonElement element = object.get(FIELD_THEMES);
        if (!(element instanceof JsonArray array)) {
            throw new JsonParseException("Theme-pack manifest is missing themes array");
        }
        if (array.isEmpty()) {
            throw new JsonParseException("Theme-pack themes array must declare at least one theme");
        }

        ArrayList<Theme> themes = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (!(item instanceof JsonObject themeObject)) {
                throw new JsonParseException("Theme-pack theme must be an object");
            }
            themes.add(Theme.fromJson(themeObject, true));
        }
        return themes;
    }

    /// Checks that theme IDs and names are present whenever the manifest needs them for disambiguation.
    private static void checkThemeIdentities(List<Theme> themes) {
        if (themes.size() <= 1) {
            return;
        }

        for (Theme theme : themes) {
            if (theme.id() == null) {
                throw new IllegalArgumentException("Theme ID is required when a theme pack declares multiple themes");
            }
            if (theme.name() == null) {
                throw new IllegalArgumentException("Theme name is required when a theme pack declares multiple themes");
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
        return requireNonBlank(primitive.getAsString(), field);
    }

    /// Reads a required localized text member.
    private static LocalizedText requireMemberLocalizedText(JsonObject object, String field) {
        @Nullable LocalizedText value = readLocalizedText(object, field);
        if (value == null) {
            throw new JsonParseException("Theme-pack manifest is missing required localized text field: " + field);
        }
        return value;
    }

    /// Reads an optional localized text member.
    private static @Nullable LocalizedText readLocalizedText(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        return parseLocalizedText(element, field);
    }

    /// Parses a localized text value.
    static LocalizedText parseLocalizedText(JsonElement element, String field) {
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            return LocalizedText.plain(requireNonBlank(primitive.getAsString(), field));
        }
        if (element instanceof JsonObject localizedObject) {
            if (localizedObject.isEmpty()) {
                throw new JsonParseException("Localized text field is empty: " + field);
            }

            LinkedHashMap<String, String> localizedValues = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : localizedObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
                    throw new JsonParseException("Localized text values must be strings: " + field);
                }
                localizedValues.put(
                        requireNonBlank(entry.getKey(), field),
                        requireNonBlank(primitive.getAsString(), field));
            }
            return new LocalizedText(localizedValues);
        }
        throw new JsonParseException("Theme-pack localized text must be a string or object: " + field);
    }

    /// Returns a validated localized text value.
    static LocalizedText requireLocalizedText(LocalizedText value, String field) {
        Objects.requireNonNull(value);

        JsonElement element = JsonUtils.GSON.toJsonTree(value, LocalizedText.class);
        return parseLocalizedText(element, field);
    }

    /// Reads one optional theme-pack value and logs malformed known fields instead of failing the whole manifest.
    ///
    /// @param field the field name to include in the warning message
    /// @param reader the value reader
    /// @return the parsed value, or `null` when the field is malformed
    static <T> @Nullable T readOptionalValue(String field, Supplier<@Nullable T> reader) {
        Objects.requireNonNull(field);
        Objects.requireNonNull(reader);

        try {
            return reader.get();
        } catch (JsonParseException | IllegalArgumentException e) {
            LOG.warning("Ignored invalid theme-pack field `" + field + "`: " + e.getMessage(), e);
            return null;
        }
    }

    /// Returns a non-blank string value.
    private static String requireNonBlank(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme-pack manifest field is blank: " + field);
        }
        return trimmed;
    }

    /// Returns a package ID that can be used directly as an installed theme-pack file name.
    static String requirePackageId(String value) {
        String id = requireNonBlank(value, FIELD_ID);
        if (!PACKAGE_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Theme-pack manifest ID cannot be used as a file name: " + value);
        }
        return id;
    }
}
