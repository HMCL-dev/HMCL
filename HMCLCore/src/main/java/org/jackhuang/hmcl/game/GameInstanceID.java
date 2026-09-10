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
package org.jackhuang.hmcl.game;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/// Identifies one game instance and forms the instance's directory name in repository layouts.
///
/// An id must be a non-blank path segment. Directory separators and the special `.` and `..`
/// segments are rejected so layout operations cannot escape or alias the instances directory.
///
/// @param id the validated instance id
@NotNullByDefault
@JsonAdapter(GameInstanceID.Adapter.class)
@JsonSerializable
public record GameInstanceID(String id) implements Comparable<GameInstanceID> {

    /// Returns whether `id` is a non-blank instance path segment.
    ///
    /// @param id the candidate id
    /// @return whether the id satisfies the repository-independent safety requirements
    public static boolean isValid(String id) {
        return !id.isBlank()
                && !id.equals(".")
                && !id.equals("..")
                && !id.contains("/")
                && !id.contains("\\");
    }

    /// Creates a validated instance id.
    ///
    /// @throws IllegalArgumentException if `id` is not valid
    public GameInstanceID {
        if (!isValid(id)) {
            throw new IllegalArgumentException("Invalid game instance id: " + id);
        }
    }

    /// {@inheritDoc}
    @Override
    public int compareTo(GameInstanceID that) {
        return this.id.compareTo(that.id);
    }

    /// Returns the instance id string.
    ///
    /// @return the value supplied to the constructor
    @Override
    public String toString() {
        return id;
    }

    /// Serializes nullable instance ids as JSON strings.
    static final class Adapter extends TypeAdapter<@Nullable GameInstanceID> {

        /// {@inheritDoc}
        @Override
        public @Nullable GameInstanceID read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            return new GameInstanceID(in.nextString());
        }

        /// {@inheritDoc}
        @Override
        public void write(JsonWriter out, @Nullable GameInstanceID value) throws IOException {
            out.value(value != null ? value.id() : null);
        }
    }
}
