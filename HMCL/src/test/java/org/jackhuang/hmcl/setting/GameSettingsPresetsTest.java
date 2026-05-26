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
import org.jackhuang.hmcl.util.GUID;
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached game settings presets.
@NotNullByDefault
public final class GameSettingsPresetsTest {
    /// Tests that the default preset selection belongs to Config.
    @Test
    public void storesDefaultGameSettingsInConfig() {
        GUID id = GUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Config config = new Config();

        config.setDefaultGameSettings(id);
        JsonObject serialized = JsonParser.parseString(config.toJson()).getAsJsonObject();

        assertEquals(id.toString(), serialized.get(Config.DEFAULT_GAME_SETTINGS_MEMBER_NAME).getAsString());
    }

    /// Tests that preset files do not preserve the workspace-level default preset selection.
    @Test
    public void doesNotStoreDefaultGameSettingsInPresets() {
        JsonObject serialized = JsonParser.parseString("""
                {
                  "$format": {
                    "id": "hmcl.game-settings",
                    "version": "1.0"
                  },
                  "defaultGameSettings": "123e4567-e89b-12d3-a456-426614174000",
                  "gameSettings": []
                }
                """).getAsJsonObject();

        GameSettingsPresets presets = JsonUtils.GSON.fromJson(serialized, GameSettingsPresets.class);
        JsonObject rewritten = JsonParser.parseString(JsonUtils.GSON.toJson(presets, GameSettingsPresets.class))
                .getAsJsonObject();

        assertEquals(GameSettingsPresets.CURRENT_FORMAT,
                JsonFileFormat.readFromMember(rewritten, JsonFileFormat.DEFAULT_MEMBER_NAME));
        assertFalse(rewritten.has(Config.DEFAULT_GAME_SETTINGS_MEMBER_NAME));
    }
}
