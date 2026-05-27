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

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached game directory migration.
@NotNullByDefault
public final class GameDirectoriesTest {
    /// Tests extracting current in-settings profile data into a detached game directory store.
    @Test
    public void extractsProfilesFromConfigJson() {
        GUID id = GUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        JsonObject settings = JsonParser.parseString("""
                {
                  "last": "Dev",
                  "profiles": [
                    {
                      "id": "123e4567-e89b-12d3-a456-426614174000",
                      "name": "Dev",
                      "gameDir": ".minecraft",
                      "selectedMinecraftVersion": "1.20.1",
                      "useRelativePath": true
                    }
                  ]
                }
                """).getAsJsonObject();

        GameDirectories gameDirectories = Objects.requireNonNull(GameDirectories.extractFromConfigJson(settings));
        assertTrue(Config.migrateLegacySelectedGameDirectory(settings, gameDirectories));
        Config config = Objects.requireNonNull(Config.fromJson(settings));

        assertFalse(settings.has("last"));
        assertFalse(settings.has("profiles"));
        assertEquals(id, config.getSelectedGameDirectory());
        assertEquals(id.toString(), JsonParser.parseString(config.toJson())
                .getAsJsonObject()
                .get(Config.SELECTED_GAME_DIRECTORY_MEMBER_NAME)
                .getAsString());
        assertEquals(1, gameDirectories.getGameDirectories().size());
        assertEquals(id, gameDirectories.getGameDirectories().get(0).getId());
        assertEquals("Dev", gameDirectories.getGameDirectories().get(0).getName());
    }

    /// Tests that game directory files do not preserve the workspace-level selected directory.
    @Test
    public void doesNotStoreSelectedGameDirectoryInGameDirectories() {
        JsonObject serialized = JsonParser.parseString("""
                {
                  "$format": {
                    "id": "hmcl.game-directories",
                    "version": "1.0"
                  },
                  "selectedGameDirectory": "123e4567-e89b-12d3-a456-426614174000",
                  "gameDirectories": []
                }
                """).getAsJsonObject();

        GameDirectories gameDirectories = JsonUtils.GSON.fromJson(serialized, GameDirectories.class);
        JsonObject rewritten = JsonParser.parseString(JsonUtils.GSON.toJson(gameDirectories, GameDirectories.class))
                .getAsJsonObject();

        assertEquals(GameDirectories.CURRENT_FORMAT,
                JsonFileFormat.readFromMember(rewritten, JsonFileFormat.DEFAULT_MEMBER_NAME));
        assertFalse(rewritten.has(Config.SELECTED_GAME_DIRECTORY_MEMBER_NAME));
        assertTrue(rewritten.has("gameDirectories"));
    }
}
