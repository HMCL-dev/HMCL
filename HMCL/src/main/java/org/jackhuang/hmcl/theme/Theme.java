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
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// One selectable theme inside a theme pack.
///
/// The theme object's top-level appearance fields provide default values. Each
/// matching override is applied in array order to produce the resolved appearance.
///
/// @param id the stable theme identifier inside its pack, or `null` for an unnamed single-theme pack
/// @param name the localized display name, or `null` for an unnamed single-theme pack
/// @param authors authors of this specific theme
/// @param description the optional localized description
/// @param thumbnail the optional theme-pack relative thumbnail path
/// @param appearance the default appearance fields
/// @param overrides conditional appearance patches applied in declaration order
@NotNullByDefault
public record Theme(
        @Nullable String id,
        @Nullable LocalizedText name,
        @Unmodifiable List<ThemePackAuthor> authors,
        @Nullable LocalizedText description,
        @Nullable String thumbnail,
        ThemeAppearance appearance,
        @Unmodifiable List<ThemeOverride> overrides) {

    /// Creates a theme.
    ///
    /// @param id the stable theme identifier inside its pack, or `null` for an unnamed single-theme pack
    /// @param name the localized display name, or `null` for an unnamed single-theme pack
    /// @param authors authors of this specific theme
    /// @param description the optional localized description
    /// @param thumbnail the optional theme-pack relative thumbnail path
    /// @param appearance the default appearance fields
    /// @param overrides conditional appearance patches applied in declaration order
    public Theme {
        if (id != null) {
            id = requireNonBlank(id, "id");
        }
        if (name != null) {
            name = ThemePackManifest.requireLocalizedText(name, "name");
        }
        authors = List.copyOf(authors);
        if (description != null) {
            description = ThemePackManifest.requireLocalizedText(description, "description");
        }
        if (thumbnail != null) {
            thumbnail = requireNonBlank(thumbnail, "thumbnail");
        }
        Objects.requireNonNull(appearance);
        overrides = List.copyOf(overrides);
    }

    /// Parses a theme from JSON.
    ///
    /// @param object the theme JSON object
    /// @param requireIdentity whether the theme must declare an explicit ID and name
    /// @return the parsed theme
    /// @throws JsonParseException if required identity fields are missing or malformed
    static Theme fromJson(JsonObject object, boolean requireIdentity) throws JsonParseException {
        Objects.requireNonNull(object);

        @Nullable String id = readString(object, "id");
        if (id == null && requireIdentity) {
            throw new JsonParseException("Theme is missing the id");
        }
        @Nullable LocalizedText name = readLocalizedText(object, "name");
        if (name == null && requireIdentity) {
            throw new JsonParseException("Theme is missing required localized text field: " + "name");
        }
        ThemeAppearance appearance = ThemeAppearance.fromJson(object);

        return new Theme(
                id,
                name,
                ThemePackManifest.readAuthors(object, "theme"),
                readLocalizedText(object, "description"),
                readString(object, "thumbnail"),
                appearance,
                readOverrides(object));
    }

    /// Returns the theme display name in the current locale.
    ///
    /// @return the localized display name, or `null` when this theme has no matching display name
    public @Nullable String displayName() {
        return name != null ? name.getText(I18n.getLocale().getCandidateLocales()) : null;
    }

    /// Returns the theme description in the current locale.
    ///
    /// @return the localized description, or `null` when this theme has no matching description
    public @Nullable String displayDescription() {
        return description != null ? description.getText(I18n.getLocale().getCandidateLocales()) : null;
    }

    /// Resolves this theme against a context by applying matching overrides.
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

    /// Converts this theme to its JSON representation.
    ///
    /// @return the JSON object representing this theme
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        if (id != null) {
            object.addProperty("id", id);
        }
        if (name != null) {
            object.add("name", JsonUtils.GSON.toJsonTree(name, LocalizedText.class));
        }
        object.add("authors", ThemePackAuthor.toJson(authors));
        if (description != null) {
            object.add("description", JsonUtils.GSON.toJsonTree(description, LocalizedText.class));
        }
        if (thumbnail != null) {
            object.addProperty("thumbnail", thumbnail);
        }
        appearance.addToJsonObject(object);

        if (!overrides.isEmpty()) {
            JsonArray array = new JsonArray();
            for (ThemeOverride override : overrides) {
                array.add(override.toJsonObject());
            }
            object.add("overrides", array);
        }
        return object;
    }

    /// Reads all valid conditional override objects.
    private static List<ThemeOverride> readOverrides(JsonObject object) {
        JsonElement element = object.get("overrides");
        if (element == null) {
            return List.of();
        }
        if (!(element instanceof JsonArray array)) {
            LOG.warning("Ignored invalid theme overrides: expected an array, got " + element);
            return List.of();
        }

        ArrayList<ThemeOverride> overrides = new ArrayList<>(array.size());
        int index = 0;
        for (JsonElement item : array) {
            String field = "overrides" + "[" + index + "]";
            if (item instanceof JsonObject overrideObject) {
                try {
                    overrides.add(ThemeOverride.fromJson(overrideObject));
                } catch (JsonParseException | IllegalArgumentException e) {
                    LOG.warning("Ignored invalid theme override `" + field + "`: " + e.getMessage(), e);
                }
            } else {
                LOG.warning("Ignored invalid theme override `" + field + "`: expected an object, got " + item);
            }
            index++;
        }
        return overrides;
    }

    /// Reads an optional string member.
    private static @Nullable String readString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            LOG.warning("Ignored invalid theme field `" + field + "`: expected a string, got " + element);
            return null;
        }
        try {
            return requireNonBlank(primitive.getAsString(), field);
        } catch (IllegalArgumentException e) {
            LOG.warning("Ignored invalid theme field `" + field + "`: " + e.getMessage(), e);
            return null;
        }
    }

    /// Reads an optional localized text member.
    private static @Nullable LocalizedText readLocalizedText(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null) {
            return null;
        }
        try {
            return ThemePackManifest.parseLocalizedText(element, field);
        } catch (JsonParseException | IllegalArgumentException e) {
            LOG.warning("Ignored invalid theme field `" + field + "`: " + e.getMessage(), e);
            return null;
        }
    }

    /// Returns a non-blank string value.
    private static String requireNonBlank(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Theme field is blank: " + field);
        }
        return trimmed;
    }
}
