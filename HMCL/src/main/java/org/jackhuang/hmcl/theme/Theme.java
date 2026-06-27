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
/// @param id          the stable theme identifier inside its pack, or `null` for an unnamed single-theme pack
/// @param name        the localized display name, or `null` for an unnamed single-theme pack
/// @param authors     authors of this specific theme
/// @param description the optional localized description
/// @param thumbnail   the optional theme-pack relative thumbnail path
/// @param appearance  the default appearance fields
/// @param overrides   conditional appearance patches applied in declaration order
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
    /// @param id          the stable theme identifier inside its pack, or `null` for an unnamed single-theme pack
    /// @param name        the localized display name, or `null` for an unnamed single-theme pack
    /// @param authors     authors of this specific theme
    /// @param description the optional localized description
    /// @param thumbnail   the optional theme-pack relative thumbnail path
    /// @param appearance  the default appearance fields
    /// @param overrides   conditional appearance patches applied in declaration order
    public Theme {
        if (id != null) {
            id = ThemePackManifest.requireThemeId(id);
        }
        if (name != null && name.mayBeEmpty()) {
            throw new IllegalArgumentException("Theme name is empty: " + name);
        }
        if (thumbnail != null) {
            thumbnail = ThemePackAsset.normalizeEntryName(thumbnail);
        }
        authors = List.copyOf(authors);
        Objects.requireNonNull(appearance);
        overrides = List.copyOf(overrides);
    }

    /// Parses a theme from JSON.
    ///
    /// @param object          the theme JSON object
    /// @param requireIdentity whether the theme must declare an explicit ID and name
    /// @return the parsed theme
    /// @throws JsonParseException if required identity fields are missing or malformed
    static Theme fromJson(JsonObject object, boolean requireIdentity) throws JsonParseException {
        Objects.requireNonNull(object);

        @Nullable String id = JsonUtils.getString(object, "id");
        if (id != null) {
            try {
                id = ThemePackManifest.requireThemeId(id);
            } catch (IllegalArgumentException e) {
                if (requireIdentity) {
                    throw new JsonParseException(e);
                }
                LOG.warning("Ignored invalid theme id: " + id, e);
                id = null;
            }
        }
        if (id == null && requireIdentity) {
            throw new JsonParseException("Theme is missing the id");
        }
        @Nullable LocalizedText name;
        try {
            name = LocalizedText.fromJson(object.get("name"));
            if (name != null && name.mayBeEmpty()) {
                throw new JsonParseException("Theme name is empty: " + name);
            }
        } catch (JsonParseException | IllegalArgumentException e) {
            if (requireIdentity) {
                throw e;
            }
            LOG.warning("Ignored invalid theme name", e);
            name = null;
        }
        if (name == null && requireIdentity) {
            throw new JsonParseException("Theme is missing required localized text field: " + "name");
        }

        List<ThemePackAuthor> authors;
        try {
            authors = ThemePackAuthor.parseAuthors(object.get("authors"));
        } catch (Exception e) {
            LOG.warning("Failed to parse authors", e);
            authors = List.of();
        }

        @Nullable LocalizedText description;

        try {
            description = LocalizedText.fromJson(object.get("description"));
            if (description != null) {
                description = ThemePackManifest.requireLocalizedText(description, "description");
            }
        } catch (Exception e) {
            LOG.warning("Failed to parse description", e);
            description = null;
        }

        ThemeAppearance appearance = ThemeAppearance.fromJson(object);

        JsonElement overridesJson = object.get("overrides");
        List<ThemeOverride> overrides;

        if (overridesJson == null || overridesJson.isJsonNull()) {
            overrides = List.of();
        } else if (overridesJson instanceof JsonArray array) {
            overrides = new ArrayList<>(array.size());

            for (JsonElement overrideJson : array) {
                try {
                    ThemeOverride override = ThemeOverride.fromJson(overrideJson);
                    if (override != null)
                        overrides.add(override);
                } catch (Exception e) {
                    LOG.warning("Invalid theme override", e);
                }
            }

        } else {
            LOG.warning("Invalid theme overrides: expected an array, got " + overridesJson);
            overrides = List.of();
        }

        @Nullable String thumbnail = JsonUtils.getString(object, "thumbnail");
        if (thumbnail != null) {
            try {
                thumbnail = ThemePackAsset.normalizeEntryName(thumbnail);
            } catch (IllegalArgumentException e) {
                LOG.warning("Ignored invalid theme thumbnail: " + thumbnail, e);
                thumbnail = null;
            }
        }

        return new Theme(id, name, authors, description,
                thumbnail,
                appearance, overrides);
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

}
