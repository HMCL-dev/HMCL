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
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A simple JSON-object condition used by theme-pack overrides.
///
/// A condition is an AND of all members. A string value requires equality, and
/// an array value requires the context value to match any listed value. Unknown
/// condition keys are accepted for forward compatibility, but they do not match
/// contexts produced by this implementation.
///
/// @param requirements normalized accepted values keyed by condition name
@NotNullByDefault
public record ThemeCondition(@Unmodifiable Map<String, @Unmodifiable Set<String>> requirements) {
    /// Condition key for the effective light or dark mode.
    static final String KEY_BRIGHTNESS = "brightness";

    /// Condition key for the configured brightness mode.
    static final String KEY_BRIGHTNESS_MODE = "brightnessMode";

    /// Condition key for the current operating system.
    static final String KEY_OS = "os";

    /// Condition key for the current UI language.
    static final String KEY_LANGUAGE = "language";

    /// Supported operating system condition values.
    private static final Set<String> SUPPORTED_OS_VALUES = Set.of("windows", "macos", "linux", "freebsd", "unknown");

    /// Creates a condition from normalized accepted values.
    ///
    /// @param requirements normalized accepted values keyed by condition name
    public ThemeCondition {
        Objects.requireNonNull(requirements);

        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : requirements.entrySet()) {
            String key = normalizeKey(entry.getKey());
            Set<String> values = entry.getValue();
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Theme condition field has no accepted values: " + key);
            }

            LinkedHashSet<String> valueCopy = new LinkedHashSet<>();
            for (String value : values) {
                valueCopy.add(normalizeValue(key, value));
            }
            copy.put(key, Collections.unmodifiableSet(valueCopy));
        }
        requirements = Collections.unmodifiableMap(copy);
    }

    /// Parses a theme condition from a JSON object.
    ///
    /// @param object the condition object
    /// @return the parsed condition
    /// @throws JsonParseException if the condition contains malformed values
    public static ThemeCondition fromJson(JsonObject object) throws JsonParseException {
        Objects.requireNonNull(object);

        LinkedHashMap<String, Set<String>> requirements = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = normalizeKey(entry.getKey());
            requirements.put(key, readAcceptedValues(key, entry.getValue()));
        }
        return new ThemeCondition(requirements);
    }

    /// Returns whether this condition matches the given resolution context.
    ///
    /// @param context the context to test
    /// @return `true` if every condition member matches the context
    public boolean matches(ThemeResolveContext context) {
        Objects.requireNonNull(context);

        for (Map.Entry<String, Set<String>> entry : requirements.entrySet()) {
            String value = context.conditionValue(entry.getKey());
            if (value == null || !entry.getValue().contains(value)) {
                return false;
            }
        }
        return true;
    }

    /// Converts this condition to its JSON representation.
    ///
    /// @return the JSON object representing this condition
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Set<String>> entry : requirements.entrySet()) {
            if (entry.getValue().size() == 1) {
                object.addProperty(entry.getKey(), entry.getValue().iterator().next());
            } else {
                JsonArray array = new JsonArray();
                for (String value : entry.getValue()) {
                    array.add(value);
                }
                object.add(entry.getKey(), array);
            }
        }
        return object;
    }

    /// Reads one condition field value.
    private static Set<String> readAcceptedValues(String key, JsonElement element) throws JsonParseException {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            values.add(normalizeValue(key, primitive.getAsString()));
        } else if (element instanceof JsonArray array) {
            if (array.isEmpty()) {
                throw new JsonParseException("Theme condition array is empty: " + key);
            }

            for (JsonElement item : array) {
                if (!(item instanceof JsonPrimitive primitive) || !primitive.isString()) {
                    throw new JsonParseException("Theme condition array must contain strings: " + key);
                }
                values.add(normalizeValue(key, primitive.getAsString()));
            }
        } else {
            throw new JsonParseException("Theme condition value must be a string or string array: " + key);
        }
        return values;
    }

    /// Normalizes and validates a condition key.
    private static String normalizeKey(String key) {
        Objects.requireNonNull(key);

        String normalized = key.trim();
        if (normalized.isEmpty()) {
            throw new JsonParseException("Theme condition key is blank");
        }
        return normalized;
    }

    /// Normalizes and validates one condition value.
    private static String normalizeValue(String key, String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new JsonParseException("Empty theme condition value for " + key);
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);

        return switch (key) {
            case KEY_BRIGHTNESS -> switch (normalized) {
                    case "light", "dark" -> normalized;
                    default -> throw new JsonParseException("Unsupported brightness condition value: " + value);
                };
            case KEY_BRIGHTNESS_MODE -> switch (normalized) {
                    case "auto", "light", "dark" -> normalized;
                    default -> throw new JsonParseException("Unsupported brightnessMode condition value: " + value);
                };
            case KEY_OS -> normalizeOperatingSystemValue(normalized, value);
            case KEY_LANGUAGE -> normalized;
            default -> trimmed;
        };
    }

    /// Normalizes an operating system condition value.
    private static String normalizeOperatingSystemValue(String normalized, String original) {
        String value = switch (normalized) {
            case "win", "windows" -> "windows";
            case "mac", "macos", "osx" -> "macos";
            case "linux" -> "linux";
            case "freebsd" -> "freebsd";
            case "unknown", "universal" -> "unknown";
            default -> normalized;
        };

        if (!SUPPORTED_OS_VALUES.contains(value)) {
            throw new JsonParseException("Unsupported os condition value: " + original);
        }
        return value;
    }

}
