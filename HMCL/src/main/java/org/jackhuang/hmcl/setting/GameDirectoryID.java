// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.gson.annotations.JsonAdapter;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.util.TypedID;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;
import java.util.UUID;

/// Stable identifier for a persisted game directory entry.
///
/// @param uuid the UUID payload
@JsonAdapter(GameDirectoryID.Adapter.class)
@JsonSerializable
@NotNullByDefault
public record GameDirectoryID(UUID uuid) implements TypedID {
    /// The serialized game directory ID prefix.
    public static final String PREFIX = "game-directory";

    /// The nil identifier value.
    public static final GameDirectoryID NIL = new GameDirectoryID(UUIDs.NIL);

    /// Creates a game directory ID.
    public GameDirectoryID {
        Objects.requireNonNull(uuid);
    }

    /// Parses a game directory ID from a prefixed UUID string.
    public static GameDirectoryID parse(String value) {
        return new GameDirectoryID(TypedID.parseUUID(PREFIX, value));
    }

    /// Generates a new time-ordered game directory ID.
    public static GameDirectoryID generate() {
        return new GameDirectoryID(UUIDs.generateV7());
    }

    /// Returns the prefixed game directory ID string.
    @Override
    public String toString() {
        return TypedID.format(PREFIX, uuid);
    }

    /// Gson adapter for [GameDirectoryID].
    public static final class Adapter extends TypedID.Adapter<GameDirectoryID> {
        /// Creates a game directory ID adapter.
        public Adapter() {
            super(PREFIX, GameDirectoryID::new);
        }
    }
}
