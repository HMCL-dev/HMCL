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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for typed object identifiers.
@NotNullByDefault
public final class TypedIDTest {
    /// Tests parsing and string serialization for account IDs.
    @Test
    public void parsesAccountID() {
        AccountID id = AccountID.parse("account:123e4567-e89b-12d3-a456-426614174000");

        assertEquals("account:123e4567-e89b-12d3-a456-426614174000", id.toString());
    }

    /// Tests parsing and string serialization for game directory IDs.
    @Test
    public void parsesGameDirectoryID() {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");

        assertEquals("game-directory:123e4567-e89b-12d3-a456-426614174000", id.toString());
    }

    /// Tests parsing and string serialization for game settings preset IDs.
    @Test
    public void parsesGameSettingsPresetID() {
        GameSettingsPresetID id =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");

        assertEquals("game-settings-preset:123e4567-e89b-12d3-a456-426614174000", id.toString());
    }

    /// Tests that typed IDs reject values from another ID domain.
    @Test
    public void rejectsWrongPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000"));
        assertThrows(IllegalArgumentException.class,
                () -> GameDirectoryID.parse("account:123e4567-e89b-12d3-a456-426614174000"));
        assertThrows(IllegalArgumentException.class,
                () -> GameSettingsPresetID.parse("123e4567-e89b-12d3-a456-426614174000"));
    }

    /// Tests that typed IDs accept lenient UUID payloads while serializing canonically.
    @ParameterizedTest
    @CsvSource({
            "account:123e4567e89b12d3a456426614174000",
            "account:123E4567-E89B-12D3-A456-426614174000"
    })
    public void acceptsLenientUUIDPayloads(String value) {
        AccountID id = AccountID.parse(value);

        assertEquals("account:123e4567-e89b-12d3-a456-426614174000", id.toString());
    }

    /// Tests JSON serialization through the standard HMCL Gson instance.
    @Test
    public void serializesAsPrefixedString() {
        GameSettingsPresetID id =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");

        String serialized = LauncherSettings.SETTINGS_GSON.toJson(id, GameSettingsPresetID.class);
        GameSettingsPresetID deserialized = Objects.requireNonNull(
                LauncherSettings.SETTINGS_GSON.fromJson(serialized, GameSettingsPresetID.class));

        assertEquals("\"game-settings-preset:123e4567-e89b-12d3-a456-426614174000\"", serialized);
        assertEquals(id, deserialized);
    }

    /// Tests game directory IDs work as JSON object map keys in launcher settings.
    @Test
    public void serializesLauncherSettingsMapKeysAsPrefixedStrings() {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        LauncherSettings settings = new LauncherSettings();

        settings.setSelectedInstance(id, new GameInstanceID("1.20.1"));
        JsonObject serialized = JsonParser.parseString(settings.toJson()).getAsJsonObject();
        LauncherSettings deserialized = Objects.requireNonNull(LauncherSettings.fromJson(serialized));

        assertEquals("1.20.1", serialized
                .getAsJsonObject(LauncherSettings.PROPERTY_SELECTED_INSTANCE)
                .get(id.toString())
                .getAsString());
        assertEquals("1.20.1", deserialized.getSelectedInstance(id).toString());
    }
}
