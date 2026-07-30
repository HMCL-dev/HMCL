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

import com.google.gson.annotations.JsonAdapter;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.util.TypedID;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;
import java.util.UUID;

/// Stable ID for a persisted account entry.
///
/// This ID identifies the launcher account record itself. It must not be confused with a Minecraft profile ID,
/// login name, server URL, or any other authentication detail that may be shared by more than one account record.
///
/// @param uuid the UUID payload
@JsonAdapter(AccountID.Adapter.class)
@JsonSerializable
@NotNullByDefault
public record AccountID(UUID uuid) implements TypedID {
    /// The serialized account ID prefix.
    public static final String PREFIX = "account";

    /// Creates an account ID.
    public AccountID {
        Objects.requireNonNull(uuid);
    }

    /// Parses an account ID from a prefixed UUID string.
    public static AccountID parse(String value) {
        return new AccountID(TypedID.parseUUID(PREFIX, value));
    }

    /// Generates a new time-ordered account ID.
    public static AccountID generate() {
        return new AccountID(UUIDs.generateV7());
    }

    /// Returns the prefixed account ID string.
    @Override
    public String toString() {
        return TypedID.format(PREFIX, uuid);
    }

    /// Gson adapter for [AccountID].
    public static final class Adapter extends TypedID.Adapter<AccountID> {
        /// Creates an account ID adapter.
        public Adapter() {
            super(PREFIX, AccountID::new);
        }
    }
}
