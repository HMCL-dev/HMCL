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

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Parsed metadata and themes from a theme-pack manifest.
///
/// @param id          the stable package identifier
/// @param version     the package version string
/// @param name        the localized display name
/// @param authors     the package authors
/// @param description the optional localized package description
/// @param themes      selectable themes declared by the package
@NotNullByDefault
@JsonSerializable
@JsonAdapter(ThemePackManifest.Adapter.class)
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

    /// Package ID format that can be used directly as an installed theme-pack file name.
    private static final Pattern PACKAGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]+");

    /// Creates a theme-pack manifest.
    ///
    /// @param id          the stable package identifier
    /// @param version     the package version string
    /// @param name        the localized display name
    /// @param authors     the package authors
    /// @param description the optional localized package description
    /// @param themes      selectable themes declared by the package
    public ThemePackManifest {
        id = requirePackageId(id);
        version = requireNonBlank(version, "version");
        name = requireLocalizedText(name, "name");
        authors = List.copyOf(authors);
        if (description != null) {
            description = requireLocalizedText(description, "description");
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
        return JsonUtils.GSON.fromJson(object, ThemePackManifest.class);
    }

    public boolean isSimpleThemePack() {
        if (themes.size() == 1) {
            Theme theme = themes.get(0);
            return theme.id() == null && theme.name() == null;
        } else {
            return false;
        }
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

    /// Reads the authors list from a JSON object.
    static List<ThemePackAuthor> readAuthors(JsonObject object, String ownerName) {
        JsonElement element = object.get("authors");
        if (element == null) {
            return List.of();
        }
        if (!(element instanceof JsonArray array)) {
            LOG.warning("Ignored invalid " + ownerName + " authors: expected an array, got " + element);
            return List.of();
        }

        ArrayList<ThemePackAuthor> authors = new ArrayList<>(array.size());
        int index = 0;
        for (JsonElement item : array) {
            String field = ownerName + " " + "authors" + "[" + index + "]";
            if (item instanceof JsonObject authorObject) {
                try {
                    authors.add(new ThemePackAuthor(requireAuthorName(authorObject)));
                } catch (JsonParseException | IllegalArgumentException e) {
                    LOG.warning("Ignored invalid " + ownerName + " author `" + field + "`: " + e.getMessage(), e);
                }
            } else {
                LOG.warning("Ignored invalid " + ownerName + " author `" + field + "`: expected an object, got " + item);
            }
            index++;
        }
        return authors;
    }

    /// Reads an author display name.
    private static LocalizedText requireAuthorName(JsonObject object) {
        JsonElement element = object.get("name");
        if (element == null) {
            throw new JsonParseException("Theme-pack author is missing required localized text field: "
                    + "name");
        }
        return parseLocalizedText(element, "name");
    }

    /// Reads the required theme declaration.
    private static List<Theme> readThemes(JsonObject object) {
        boolean hasSingleTheme = object.has("theme");
        boolean hasMultipleThemes = object.has("themes");
        if (hasSingleTheme == hasMultipleThemes) {
            throw new JsonParseException("Theme-pack manifest must declare exactly one of theme or themes");
        }

        if (hasSingleTheme) {
            JsonElement element = object.get("theme");
            if (!(element instanceof JsonObject themeObject)) {
                throw new JsonParseException("Theme-pack theme must be an object");
            }
            return List.of(Theme.fromJson(themeObject, false));
        }

        JsonElement element = object.get("themes");
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
    private static String requireMemberString(JsonObject object, String fieldName) {
        @Nullable String value = readString(object, fieldName);
        if (value == null) {
            throw new JsonParseException("Theme-pack manifest is missing " + fieldName);
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
        String id = requireNonBlank(value, "id");
        if (!PACKAGE_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Theme-pack manifest ID cannot be used as a file name: " + value);
        }
        return id;
    }

    static final class Adapter implements JsonSerializer<@Nullable ThemePackManifest>,
            JsonDeserializer<@Nullable ThemePackManifest> {

        @Override
        public @Nullable ThemePackManifest deserialize(
                @Nullable JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json instanceof JsonNull) {
                return null;
            }

            if (!(json instanceof JsonObject object)) {
                throw new JsonParseException("Theme-pack manifest is not a JsonObject");
            }

            if (!JsonSchema.check(object, CURRENT_SCHEMA).readable()) {
                throw new JsonParseException("Unsupported theme-pack schema: " + object.get(JsonSchema.PROPERTY_SCHEMA));
            }

            String id = requireMemberString(object, "id");
            String version = requireMemberString(object, "version");

            LocalizedText name = context.deserialize(object.get("name"), LocalizedText.class);
            if (name == null) {
                throw new JsonParseException("Theme-pack manifest is missing name");
            }

            @Nullable LocalizedText description = context.deserialize(object.get("description"), LocalizedText.class);

            return new ThemePackManifest(
                    id,
                    version,
                    name,
                    readAuthors(object, "theme-pack"),
                    description,
                    readThemes(object));
        }

        @Override
        public JsonElement serialize(@Nullable ThemePackManifest src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }

            JsonObject object = new JsonObject();
            object.addProperty(JsonSchema.PROPERTY_SCHEMA, CURRENT_SCHEMA.url());
            object.addProperty("id", src.id);
            object.addProperty("version", src.version);
            object.add("name", src.name.toJsonElement());
            object.add("authors", ThemePackAuthor.toJson(src.authors));

            if (src.description != null) {
                object.add("description", src.description.toJsonElement());
            }

            if (src.isSimpleThemePack()) {
                object.add("theme", src.themes.get(0).toJsonObject());
            } else {
                JsonArray themeArray = new JsonArray();
                for (Theme theme : src.themes) {
                    themeArray.add(theme.toJsonObject());
                }
                object.add("themes", themeArray);
            }
            return object;
        }
    }
}
