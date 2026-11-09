// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
