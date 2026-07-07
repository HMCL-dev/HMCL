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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/// Serializes enum constants as lower-case enum names and reads enum names case-insensitively.
///
/// Unknown values are deserialized as `null` for compatibility with legacy code that treated
/// unsupported enum values as absent.
///
/// TODO: Annotate enum types that should be serialized as lower-case names to use this adapter.
@NotNullByDefault
public final class LowerCaseEnumTypeAdapter<E extends Enum<E>> extends TypeAdapter<@Nullable E> {

    /// Gson factory that applies [LowerCaseEnumTypeAdapter] to enum raw types.
    public static final TypeAdapterFactory FACTORY = new TypeAdapterFactory() {
        /// Creates a lower-case enum adapter for enum types, or returns `null` for non-enum types.
        ///
        /// @param gson the Gson instance requesting the adapter
        /// @param type the target type token
        /// @return an enum adapter for enum raw types, or `null` to let Gson continue factory lookup
        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public <T> @Nullable TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> rawType = type.getRawType();
            return rawType.isEnum() ? new LowerCaseEnumTypeAdapter(rawType) : null;
        }
    };

    /// Maps lower-case enum names to their corresponding constants for lookup during deserialization.
    private final @Unmodifiable Map<String, E> lowercaseToConstant;

    /// Creates an adapter for the supplied enum class.
    ///
    /// @param enumClass the enum class whose constants are accepted by this adapter
    private LowerCaseEnumTypeAdapter(Class<E> enumClass) {
        var lowercaseToConstant = new HashMap<String, E>();
        for (E constant : enumClass.getEnumConstants()) {
            lowercaseToConstant.put(constant.name().toLowerCase(), constant);
        }
        this.lowercaseToConstant = Map.copyOf(lowercaseToConstant);
    }

    /// Reads a nullable enum value from the next JSON token.
    ///
    /// `null` JSON tokens are returned as `null`; string tokens are matched against enum names
    /// case-insensitively. Unknown names are also returned as `null` for compatibility.
    ///
    /// @param in the JSON reader positioned at the enum value
    /// @return the enum constant represented by the token, or `null` for JSON null or unknown names
    /// @throws IOException if the reader cannot read the next token
    @Override
    public @Nullable E read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String name = in.nextString();
        return lowercaseToConstant.get(name.toLowerCase(Locale.ROOT));
    }

    /// Writes a nullable enum value to JSON.
    ///
    /// Non-null values are written as lower-case enum names; `null` values are written as JSON null.
    ///
    /// @param out   the JSON writer receiving the value
    /// @param value the enum constant to write, or `null`
    /// @throws IOException if the writer cannot write the value
    @Override
    public void write(JsonWriter out, @Nullable E value) throws IOException {
        out.value(value != null ? value.name().toLowerCase(Locale.ROOT) : null);
    }

    /// Converts a JSON primitive into an enum constant by matching enum names case-insensitively.
    ///
    /// @param clazz the enum class to search
    /// @param json  the JSON primitive containing the enum name
    /// @return the enum constant represented by `json`, or `null` when the primitive is not a string
    /// or names no enum constant
    public static <E extends Enum<E>> @Nullable E fromJson(Class<E> clazz, JsonElement json) {
        return json instanceof JsonPrimitive primitive && primitive.isString()
                ? fromJson(clazz, json.getAsString())
                : null;

    }

    /// Converts a JSON primitive into an enum constant by matching enum names case-insensitively.
    ///
    /// @param clazz the enum class to search
    /// @param name  the JSON string value
    /// @return the enum constant represented by `json`, or `null` when the primitive is not a string
    /// or names no enum constant
    public static <E extends Enum<E>> @Nullable E fromJson(Class<E> clazz, String name) {
        for (E constant : clazz.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name))
                return constant;
        }

        return null;
    }

    /// Converts an enum constant into its lower-case JSON representation.
    ///
    /// @param value the enum constant to convert, or `null`
    /// @return a lower-case string primitive for non-null values, or [JsonNull#INSTANCE] for `null`
    public static <E extends Enum<E>> JsonElement toJsonTree(@Nullable E value) {
        return value != null ? new JsonPrimitive(value.name().toLowerCase(Locale.ROOT)) : JsonNull.INSTANCE;
    }
}
