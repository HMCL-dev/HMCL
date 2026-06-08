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
package org.jackhuang.hmcl.setting;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/// Stable identifier for profile and game setting preset objects.
///
/// This type wrap a [UUID] so profile and game setting references cannot be
/// accidentally mixed with unrelated UUID values.
@JsonAdapter(SettingId.Adapter.class)
@JsonSerializable
@NotNullByDefault
public record SettingId(UUID uuid) {
    /// The nil identifier value.
    public static final SettingId NIL = new SettingId(UUIDs.NIL);

    /// The URL namespace used for deterministic legacy setting IDs.
    public static final SettingId NAMESPACE_URL = new SettingId(UUIDs.NAMESPACE_URL);

    /// Creates a setting ID.
    public SettingId {
        Objects.requireNonNull(uuid);
    }

    /// Parses a setting ID from a UUID string.
    public static SettingId parse(String value) {
        return new SettingId(UUIDs.parse(value));
    }

    /// Generates a new time-ordered setting ID.
    public static SettingId generate() {
        return new SettingId(UUIDs.generateV7());
    }

    /// Generates a deterministic setting ID in the given namespace.
    public static SettingId v5(SettingId namespace, String name) {
        return new SettingId(UUIDs.generateV5(namespace.uuid, name));
    }

    /// Returns the canonical UUID string.
    @Override
    public String toString() {
        return uuid.toString();
    }

    /// Gson adapter for [SettingId].
    public static final class Adapter extends TypeAdapter<@Nullable SettingId> {
        /// Creates a setting ID adapter.
        public Adapter() {
        }

        /// Writes the setting ID as a canonical UUID string, or JSON null.
        @Override
        public void write(JsonWriter out, @Nullable SettingId value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        /// Reads a setting ID from a UUID string, or JSON null.
        @Override
        public @Nullable SettingId read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            return parse(in.nextString());
        }
    }
}
