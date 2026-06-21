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
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;
import java.util.Set;

/// A conditional appearance patch in a theme.
///
/// @param condition the condition required for this override to apply
/// @param appearance the appearance fields applied when the condition matches
@NotNullByDefault
public record ThemeOverride(ThemeCondition condition, ThemeAppearance appearance) {
    /// JSON member name for an override condition.
    private static final String FIELD_CONDITION = "condition";

    /// Non-appearance fields accepted by an override object.
    private static final Set<String> IGNORED_FIELDS = Set.of(FIELD_CONDITION);

    /// Creates a conditional theme override.
    ///
    /// @param condition the condition required for this override to apply
    /// @param appearance the appearance fields applied when the condition matches
    public ThemeOverride {
        Objects.requireNonNull(condition);
        Objects.requireNonNull(appearance);
        if (appearance.isEmpty()) {
            throw new IllegalArgumentException("Theme override does not define any appearance fields");
        }
    }

    /// Parses a theme override from JSON.
    ///
    /// @param object the override object
    /// @return the parsed override
    /// @throws JsonParseException if the override is malformed
    static ThemeOverride fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        JsonElement conditionElement = object.get(FIELD_CONDITION);
        if (!(conditionElement instanceof JsonObject conditionObject)) {
            throw new JsonParseException("Theme override must define an object condition");
        }

        ThemeCondition condition = ThemeCondition.fromJson(conditionObject);
        ThemeAppearance appearance = ThemeAppearance.fromJson(object, IGNORED_FIELDS, "override");
        if (appearance.isEmpty()) {
            throw new JsonParseException("Theme override does not define any appearance fields");
        }
        return new ThemeOverride(condition, appearance);
    }

    /// Returns whether this override matches the given resolution context.
    ///
    /// @param context the context to test
    /// @return `true` when the override should be applied
    public boolean matches(ThemeResolveContext context) {
        return condition.matches(context);
    }

    /// Converts this override to its JSON representation.
    ///
    /// @return the JSON object representing this override
    public JsonObject toJsonObject() {
        JsonObject object = appearance.toJsonObject();
        object.add(FIELD_CONDITION, condition.toJsonObject());
        return object;
    }
}
