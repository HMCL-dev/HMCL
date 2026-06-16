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
