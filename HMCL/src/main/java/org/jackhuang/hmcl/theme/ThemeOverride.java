// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// A conditional appearance patch in a theme.
///
/// @param condition  the condition required for this override to apply
/// @param appearance the appearance fields applied when the condition matches
@NotNullByDefault
public record ThemeOverride(ThemeCondition condition, ThemeAppearance appearance) {
    /// JSON member name for an override condition.
    private static final String FIELD_CONDITION = "condition";

    /// Creates a conditional theme override.
    ///
    /// @param condition  the condition required for this override to apply
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
    /// @param element the override object
    /// @return the parsed override
    /// @throws JsonParseException if the override is malformed
    static @Nullable ThemeOverride fromJson(@Nullable JsonElement element) throws JsonParseException {
        if (element == null || element.isJsonNull())
            return null;

        if (!(element instanceof JsonObject object)) {
            throw new JsonParseException("Invalid theme override");
        }

        JsonElement conditionElement = object.get(FIELD_CONDITION);
        if (!(conditionElement instanceof JsonObject conditionObject)) {
            throw new JsonParseException("Theme override must define an object condition");
        }

        ThemeCondition condition = ThemeCondition.fromJson(conditionObject);
        ThemeAppearance appearance = ThemeAppearance.fromJson(object);
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
