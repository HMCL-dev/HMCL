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

/// Stable identifier for a persisted game settings preset.
///
/// @param uuid the UUID payload
@JsonAdapter(GameSettingsPresetID.Adapter.class)
@JsonSerializable
@NotNullByDefault
public record GameSettingsPresetID(UUID uuid) implements TypedID {
    /// The serialized game settings preset ID prefix.
    public static final String PREFIX = "game-settings-preset";

    /// The nil identifier value.
    public static final GameSettingsPresetID NIL = new GameSettingsPresetID(UUIDs.NIL);

    /// Creates a game settings preset ID.
    public GameSettingsPresetID {
        Objects.requireNonNull(uuid);
    }

    /// Parses a game settings preset ID from a prefixed UUID string.
    public static GameSettingsPresetID parse(String value) {
        return new GameSettingsPresetID(TypedID.parseUUID(PREFIX, value));
    }

    /// Generates a new time-ordered game settings preset ID.
    public static GameSettingsPresetID generate() {
        return new GameSettingsPresetID(UUIDs.generateV7());
    }

    /// Returns the prefixed game settings preset ID string.
    @Override
    public String toString() {
        return TypedID.format(PREFIX, uuid);
    }

    /// Gson adapter for [GameSettingsPresetID].
    public static final class Adapter extends TypedID.Adapter<GameSettingsPresetID> {
        /// Creates a game settings preset ID adapter.
        public Adapter() {
            super(PREFIX, GameSettingsPresetID::new);
        }
    }
}
