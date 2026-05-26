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

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/// Immutable 128-bit globally unique identifier.
///
/// This type mirrors the behavior of [UUID], while its Gson representation is the
/// standard 36-character UUID string form returned by [#toString()].
///
/// @param uuid the backing UUID value
/// @author Glavo
@JsonAdapter(GUID.Adapter.class)
@JsonSerializable
@NotNullByDefault
public record GUID(UUID uuid) implements Comparable<GUID> {
    /// Creates a GUID backed by the given UUID.
    ///
    /// @param uuid the backing UUID value
    public GUID {
        Objects.requireNonNull(uuid);
    }

    /// Creates a randomly generated GUID.
    ///
    /// @return a randomly generated GUID
    public static GUID random() {
        return new GUID(UUID.randomUUID());
    }

    /// Parses a GUID from the standard UUID string syntax accepted by [UUID#fromString(String)].
    ///
    /// @param name the string to parse
    /// @return the parsed GUID
    /// @throws IllegalArgumentException if `name` is not a valid UUID string
    public static GUID fromString(String name) {
        return new GUID(UUID.fromString(name));
    }

    /// Converts a [UUID] into a GUID with the same bit layout.
    ///
    /// @param uuid the UUID to convert
    /// @return a GUID with the same bits as `uuid`
    public static GUID fromUUID(UUID uuid) {
        return new GUID(uuid);
    }

    /// Returns the version number associated with this GUID.
    ///
    /// @return the version number
    public int version() {
        return uuid.version();
    }

    /// Compares two GUID values using the same signed bit ordering as [UUID#compareTo(UUID)].
    ///
    /// @param value the GUID to compare with this GUID
    /// @return a negative integer, zero, or a positive integer as this GUID is less than,
    ///         equal to, or greater than `value`
    @Override
    public int compareTo(GUID value) {
        return uuid.compareTo(value.uuid);
    }

    /// Returns the standard 36-character UUID string representation.
    ///
    /// @return the standard UUID string representation
    @Override
    public String toString() {
        return uuid.toString();
    }

    /// Gson adapter that serializes GUID values as standard UUID strings.
    @NotNullByDefault
    public static final class Adapter extends TypeAdapter<@Nullable GUID> {
        /// Writes a GUID as a standard UUID string, or JSON null when the value is null.
        @Override
        public void write(JsonWriter out, @Nullable GUID value) throws IOException {
            out.value(value != null ? value.toString() : null);
        }

        /// Reads a GUID from a standard UUID string or JSON null.
        @Override
        public @Nullable GUID read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            String value = in.nextString();
            try {
                return GUID.fromString(value);
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("GUID malformed: " + value, e);
            }
        }
    }
}
