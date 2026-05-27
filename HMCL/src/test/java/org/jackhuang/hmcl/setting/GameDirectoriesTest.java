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

import com.github.f4b6a3.uuid.alt.GUID;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.PortablePath;
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
        GUID id = new GUID("123e4567-e89b-12d3-a456-426614174000");
        JsonObject settings = JsonParser.parseString("""
                {
                  "last": "Dev",
                  "profiles": [
                    {
                      "id": "123e4567-e89b-12d3-a456-426614174000",
                      "name": "Dev",
                      "gameDir": ".minecraft",
                      "useRelativePath": true
                    }
                  ]
                }
                """).getAsJsonObject();

        GameDirectories gameDirectories = Objects.requireNonNull(GameDirectories.extractFromConfigJson(settings));
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedGameDirectory(settings, gameDirectories));
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
        assertEquals(".minecraft", gameDirectories.getGameDirectories().get(0).getPath().getPath());
    }

    /// Tests migrating upstream/main selected version fields into the main config.
    @Test
    public void migratesLegacySelectedVersionsFromConfigurations() {
        GUID id = LegacyGameSettingsMigrator.getLegacyProfileId("Dev");
        JsonObject settings = JsonParser.parseString("""
                {
                  "last": "Dev",
                  "configurations": {
                    "Dev": {
                      "gameDir": ".minecraft",
                      "selectedMinecraftVersion": "1.20.1"
                    }
                  }
                }
                """).getAsJsonObject();

        assertTrue(LegacyConfigMigrator.migrateLegacySelectedVersions(settings));
        GameDirectories gameDirectories = Objects.requireNonNull(GameDirectories.extractFromConfigJson(settings));
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedGameDirectory(settings, gameDirectories));
        Config config = Objects.requireNonNull(Config.fromJson(settings));

        assertFalse(settings.has("configurations"));
        assertEquals(id, config.getSelectedGameDirectory());
        assertEquals("1.20.1", config.getSelectedInstance(id));

        JsonObject serialized = JsonParser.parseString(config.toJson()).getAsJsonObject();
        assertEquals("1.20.1", serialized
                .getAsJsonObject(Config.SELECTED_INSTANCE_MEMBER_NAME)
                .get(id.toString())
                .getAsString());
    }

    /// Tests that profiles store their directory as a portable path.
    @Test
    public void storesProfilePath() {
        GUID id = new GUID("123e4567-e89b-12d3-a456-426614174000");
        Profile profile = new Profile(id, "Dev", PortablePath.of("versions\\Dev"));

        JsonObject serialized = JsonUtils.GSON.toJsonTree(profile, Profile.class).getAsJsonObject();
        Profile deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, Profile.class));

        assertEquals("versions/Dev", serialized.get("path").getAsString());
        assertFalse(serialized.has("gameDir"));
        assertFalse(serialized.has("useRelativePath"));
        assertEquals("versions/Dev", deserialized.getPath().getPath());
        assertFalse(deserialized.getPath().isAbsolute());
    }

    /// Tests that profiles must be deserialized with a non-nil ID.
    @Test
    public void rejectsNilProfileId() {
        assertThrows(JsonParseException.class, () -> JsonUtils.GSON.fromJson("""
                {
                  "id": "00000000-0000-0000-0000-000000000000",
                  "name": "Dev",
                  "path": "versions/Dev"
                }
                """, Profile.class));
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
