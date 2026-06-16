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
package org.jackhuang.hmcl.auth;

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

/// Stable identifier for a persisted account entry.
///
/// This ID identifies the launcher account record itself. It must not be confused with a Minecraft profile ID,
/// login name, server URL, or any other authentication detail that may be shared by more than one account record.
@JsonAdapter(AccountID.Adapter.class)
@JsonSerializable
@NotNullByDefault
public record AccountID(UUID uuid) {
    /// Creates an account ID.
    public AccountID {
        Objects.requireNonNull(uuid);
    }

    /// Parses an account ID from a UUID string.
    public static AccountID parse(String value) {
        return new AccountID(UUIDs.parse(value));
    }

    /// Generates a new time-ordered account ID.
    public static AccountID generate() {
        return new AccountID(UUIDs.generateV7());
    }

    /// Returns the canonical UUID string.
    @Override
    public String toString() {
        return uuid.toString();
    }

    /// Gson adapter for [AccountID].
    public static final class Adapter extends TypeAdapter<@Nullable AccountID> {
        /// Creates an account ID adapter.
        public Adapter() {
        }

        /// Writes the account ID as a canonical UUID string, or JSON null.
        @Override
        public void write(JsonWriter out, @Nullable AccountID value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        /// Reads an account ID from a UUID string, or JSON null.
        @Override
        public @Nullable AccountID read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            return parse(in.nextString());
        }
    }
}
