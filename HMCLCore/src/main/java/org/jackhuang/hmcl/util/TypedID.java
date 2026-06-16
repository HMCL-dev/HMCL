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
package org.jackhuang.hmcl.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.glavo.uuid.UUIDs;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/// Stable ID value whose serialized representation is scoped by a textual type prefix.
@NotNullByDefault
public interface TypedID {
    /// The separator between the type prefix and UUID payload.
    String SEPARATOR = ":";

    /// Returns the UUID payload.
    UUID uuid();

    /// Formats a typed ID string.
    ///
    /// @param prefix the required type prefix
    /// @param uuid the UUID payload
    /// @return the prefixed textual ID
    static String format(String prefix, UUID uuid) {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(uuid);
        return prefix + SEPARATOR + uuid;
    }

    /// Parses the UUID payload from a typed ID string.
    ///
    /// @param prefix the required type prefix
    /// @param value the serialized typed ID
    /// @return the UUID payload
    /// @throws IllegalArgumentException if the value does not have the required prefix or UUID payload
    static UUID parseUUID(String prefix, String value) {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(value);

        String expectedPrefix = prefix + SEPARATOR;
        if (!value.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Expected typed ID prefix '" + expectedPrefix + "'");
        }

        String uuid = value.substring(expectedPrefix.length());
        if (!isCanonicalUUID(uuid)) {
            throw new IllegalArgumentException("Expected canonical UUID payload");
        }

        return UUIDs.parse(uuid);
    }

    /// Returns whether the string is a canonical hyphenated UUID.
    ///
    /// @param uuid the UUID string to validate
    /// @return whether the string has the canonical UUID layout
    private static boolean isCanonicalUUID(String uuid) {
        if (uuid.length() != 36) {
            return false;
        }

        for (int i = 0; i < uuid.length(); i++) {
            char ch = uuid.charAt(i);
            if (isUUIDHyphenIndex(i)) {
                if (ch != '-') {
                    return false;
                }
            } else if (!isHexDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether a canonical UUID has a hyphen at the given index.
    ///
    /// @param index the UUID string index
    /// @return whether the index must contain a hyphen
    private static boolean isUUIDHyphenIndex(int index) {
        return index == 8 || index == 13 || index == 18 || index == 23;
    }

    /// Returns whether the character is an ASCII hexadecimal digit.
    ///
    /// @param ch the character to test
    /// @return whether the character is a hexadecimal digit
    private static boolean isHexDigit(char ch) {
        return ch >= '0' && ch <= '9'
                || ch >= 'a' && ch <= 'f'
                || ch >= 'A' && ch <= 'F';
    }

    /// Gson adapter for typed IDs with a fixed prefix.
    abstract class Adapter<T extends TypedID> extends TypeAdapter<@Nullable T> {
        /// The serialized ID prefix accepted by this adapter.
        private final String prefix;

        /// Factory creating typed IDs from parsed UUID payloads.
        private final Function<UUID, T> factory;

        /// Creates an adapter for typed IDs with the given prefix.
        ///
        /// @param prefix the serialized ID prefix
        /// @param factory creates the typed ID from the UUID payload
        protected Adapter(String prefix, Function<UUID, T> factory) {
            this.prefix = Objects.requireNonNull(prefix);
            this.factory = Objects.requireNonNull(factory);
        }

        /// Writes the typed ID as a prefixed UUID string, or JSON null.
        @Override
        public void write(JsonWriter out, @Nullable T value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(TypedID.format(prefix, value.uuid()));
            }
        }

        /// Reads the typed ID from a prefixed UUID string, or JSON null.
        @Override
        public @Nullable T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            return Objects.requireNonNull(factory.apply(TypedID.parseUUID(prefix, in.nextString())));
        }
    }
}
