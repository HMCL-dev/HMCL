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

import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached game directory migration.
@NotNullByDefault
public final class GameDirectoriesTest {
    /// Tests extracting legacy configuration data into a detached game directory store.
    @Test
    public void extractsConfigurationsFromLegacyConfigJson() {
        SettingId id = LegacyConfigMigrator.getLegacyProfileId("Dev");
        JsonObject settings = JsonParser.parseString("""
                {
                  "configurations": {
                    "Dev": {
                      "gameDir": ".minecraft",
                      "useRelativePath": true
                    }
                  }
                }
                """).getAsJsonObject();

        GameDirectories gameDirectories = Objects.requireNonNull(LegacyConfigMigrator.extractGameDirectoriesFromConfigJson(settings));

        assertFalse(settings.has("configurations"));
        assertEquals(1, gameDirectories.getGameDirectories().size());
        assertEquals(id, gameDirectories.getGameDirectories().get(0).getId());
        assertEquals("Dev", Profiles.getProfileCustomName(gameDirectories.getGameDirectories().get(0)));
        assertEquals(".minecraft", gameDirectories.getGameDirectories().get(0).getPath().getPath());
        assertNull(gameDirectories.getGameDirectories().get(0).getLegacyGameSettings());
    }

    /// Tests extracting the migrated legacy game settings ID from a legacy profile.
    @Test
    public void extractsLegacyGameSettingsIdFromLegacyProfileGlobalSettings() {
        SettingId profileId = LegacyConfigMigrator.getLegacyProfileId("Dev");
        SettingId legacyGameSettings = LegacyConfigMigrator.getLegacyGameSettingsId("Dev");
        JsonObject settings = JsonParser.parseString("""
                {
                  "configurations": {
                    "Dev": {
                      "gameDir": ".minecraft",
                      "global": {
                        "maxMemory": 2048
                      }
                    }
                  }
                }
                """).getAsJsonObject();

        GameDirectories gameDirectories = Objects.requireNonNull(LegacyConfigMigrator.extractGameDirectoriesFromConfigJson(settings));

        Profile profile = gameDirectories.getGameDirectories().get(0);
        assertEquals(profileId, profile.getId());
        assertEquals(legacyGameSettings, profile.getLegacyGameSettings());
        assertNotEquals(profile.getId(), profile.getLegacyGameSettings());
    }

    /// Tests that built-in profiles do not store names after migration.
    @Test
    public void removesBuiltInProfileNamesDuringMigration() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "configurations": {
                    "Default": {
                      "gameDir": ".minecraft"
                    },
                    "Home": {
                      "gameDir": "/home/user/.minecraft"
                    },
                    "Dev": {
                      "gameDir": "versions/Dev"
                    }
                  }
                }
                """).getAsJsonObject();

        GameDirectories gameDirectories = Objects.requireNonNull(LegacyConfigMigrator.extractGameDirectoriesFromConfigJson(settings));

        SettingId defaultProfileId = LegacyConfigMigrator.getLegacyProfileId("Default");
        SettingId homeProfileId = LegacyConfigMigrator.getLegacyProfileId("Home");
        Profile defaultProfile = gameDirectories.getGameDirectories().stream()
                .filter(profile -> defaultProfileId.equals(profile.getId()))
                .findFirst()
                .orElseThrow();
        Profile homeProfile = gameDirectories.getGameDirectories().stream()
                .filter(profile -> homeProfileId.equals(profile.getId()))
                .findFirst()
                .orElseThrow();
        Profile devProfile = gameDirectories.getGameDirectories().stream()
                .filter(profile -> "Dev".equals(Profiles.getProfileCustomName(profile)))
                .findFirst()
                .orElseThrow();

        assertNull(defaultProfile.getName());
        assertNull(homeProfile.getName());
        assertEquals("Dev", Profiles.getProfileCustomName(devProfile));
    }

    /// Tests migrating upstream/main selected version fields into the main config.
    @Test
    public void migratesLegacySelectedVersionsFromConfigurations() {
        SettingId id = LegacyConfigMigrator.getLegacyProfileId("Dev");
        assertEquals(5, id.uuid().version());
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
        GameDirectories gameDirectories = Objects.requireNonNull(LegacyConfigMigrator.extractGameDirectoriesFromConfigJson(settings));
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedGameDirectory(settings));
        LauncherSettings config = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertFalse(settings.has("configurations"));
        assertEquals(id, config.selectedGameDirectoryProperty().get());
        assertEquals("1.20.1", config.getSelectedInstance(id));

        JsonObject serialized = JsonParser.parseString(config.toJson()).getAsJsonObject();
        assertEquals("1.20.1", serialized
                .getAsJsonObject(LauncherSettings.PROPERTY_SELECTED_INSTANCE)
                .get(id.toString())
                .getAsString());
    }

    /// Tests that profiles store their directory as a portable path.
    @Test
    public void storesProfilePath() {
        SettingId id = SettingId.parse("123e4567-e89b-12d3-a456-426614174000");
        Profile profile = new Profile(id, LocalizedText.plain("Dev"), PortablePath.of("versions\\Dev"));

        JsonObject serialized = JsonUtils.GSON.toJsonTree(profile, Profile.class).getAsJsonObject();
        Profile deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, Profile.class));

        assertEquals("versions/Dev", serialized.get("path").getAsString());
        assertEquals("Dev", serialized.get("name").getAsString());
        assertFalse(serialized.has("gameDir"));
        assertFalse(serialized.has("useRelativePath"));
        assertEquals("versions/Dev", deserialized.getPath().getPath());
        assertFalse(deserialized.getPath().isAbsolute());
    }

    /// Tests that localized profile names can be read and preserved as JSON objects.
    @Test
    public void readsLocalizedProfileName() {
        Profile profile = Objects.requireNonNull(JsonUtils.GSON.fromJson("""
                {
                  "id": "123e4567-e89b-12d3-a456-426614174000",
                  "name": {
                    "en": "Development",
                    "zh-Hans": "开发"
                  },
                  "path": "versions/Dev"
                }
                """, Profile.class));

        LocalizedText name = Objects.requireNonNull(profile.getName());
        JsonObject serialized = JsonUtils.GSON.toJsonTree(profile, Profile.class).getAsJsonObject();

        assertEquals("Development", name.getText(List.of(Locale.ENGLISH)));
        assertEquals("开发", name.getText(List.of(LocaleUtils.LOCALE_ZH_HANS)));
        assertEquals("Development", serialized.getAsJsonObject("name").get("en").getAsString());
        assertEquals("开发", serialized.getAsJsonObject("name").get("zh-Hans").getAsString());
    }

    /// Tests that profiles preserve migrated legacy game settings IDs.
    @Test
    public void storesLegacyGameSettingsId() {
        SettingId id = SettingId.parse("123e4567-e89b-12d3-a456-426614174000");
        SettingId legacyGameSettings = SettingId.parse("123e4567-e89b-12d3-a456-426614174001");
        Profile profile = new Profile(
                id,
                LocalizedText.plain("Dev"),
                PortablePath.of("versions\\Dev"),
                legacyGameSettings);

        JsonObject serialized = JsonUtils.GSON.toJsonTree(profile, Profile.class).getAsJsonObject();
        Profile deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, Profile.class));

        assertEquals(legacyGameSettings.toString(), serialized.get("legacyGameSettings").getAsString());
        assertEquals(legacyGameSettings, deserialized.getLegacyGameSettings());
    }

    /// Tests that unnamed profiles are displayed by ID and serialized without a name.
    @Test
    public void displaysUnnamedProfileAsId() {
        SettingId id = SettingId.parse("123e4567-e89b-12d3-a456-426614174000");
        Profile profile = new Profile(id, null, PortablePath.of("versions\\Dev"));

        JsonObject serialized = JsonUtils.GSON.toJsonTree(profile, Profile.class).getAsJsonObject();
        Profile deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, Profile.class));

        assertFalse(serialized.has("name"));
        assertNull(deserialized.getName());
        assertEquals(id.toString(), Profiles.getProfileDisplayName(profile));
    }

    /// Tests that an explicit name overrides built-in display names.
    @Test
    public void displaysExplicitNameBeforeBuiltInName() {
        SettingId id = SettingId.parse("123e4567-e89b-12d3-a456-426614174000");
        Profile profile = new Profile(id, LocalizedText.plain("Custom Default"), PortablePath.of(".minecraft"));

        assertEquals("Custom Default", Profiles.getProfileDisplayName(profile));
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
                  "$schema": "https://schemas.glavo.site/hmcl/game-directories/1.0.0",
                  "selectedGameDirectory": "123e4567-e89b-12d3-a456-426614174000",
                  "directories": []
                }
                """).getAsJsonObject();

        GameDirectories gameDirectories = JsonUtils.GSON.fromJson(serialized, GameDirectories.class);
        JsonObject rewritten = JsonParser.parseString(JsonUtils.GSON.toJson(gameDirectories, GameDirectories.class))
                .getAsJsonObject();

        assertEquals(GameDirectories.CURRENT_SCHEMA,
                JsonSchema.readFromMember(rewritten, JsonSchema.PROPERTY_SCHEMA));
        assertFalse(rewritten.has(LauncherSettings.PROPERTY_SELECTED_GAME_DIRECTORY));
        assertTrue(rewritten.has("directories"));
    }

    /// Tests that patch-version schemas are preserved together with unknown fields.
    @Test
    public void preservesPatchSchemaAndUnknownFields() throws IOException {
        Path tempDir = createJsonSettingFileTestDirectory("patch-schema");
        Path location = tempDir.resolve("game-directories.json");
        Files.writeString(location, """
                {
                  "$schema": "https://schemas.glavo.site/hmcl/game-directories/1.0.1",
                  "futureField": {
                    "enabled": true
                  },
                  "directories": []
                }
                """);

        JsonSettingFile<GameDirectories> file = new JsonSettingFile<>(
                location,
                "game directories",
                GameDirectories.class,
                GameDirectories.CURRENT_SCHEMA,
                GameDirectories::new);

        JsonSettingFile.LoadResult<GameDirectories> result = file.load(null);
        assertTrue(result.allowSave());
        assertEquals(new JsonSchema("https://schemas.glavo.site/hmcl/game-directories/1.0.1"),
                result.value().getSchema());

        JsonObject rewritten = JsonParser.parseString(JsonUtils.GSON.toJson(result.value(), GameDirectories.class))
                .getAsJsonObject();
        assertEquals("https://schemas.glavo.site/hmcl/game-directories/1.0.1",
                rewritten.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
        assertTrue(rewritten.getAsJsonObject("futureField").get("enabled").getAsBoolean());
    }

    /// Tests that malformed detached files are not allowed to be overwritten by fallback defaults.
    @Test
    public void rejectsSavingFallbackForMalformedDetachedFile() throws IOException {
        Path tempDir = createJsonSettingFileTestDirectory("malformed");
        Path location = tempDir.resolve("game-directories.json");
        Files.writeString(location, "{");

        JsonSettingFile<GameDirectories> file = new JsonSettingFile<>(
                location,
                "game directories",
                GameDirectories.class,
                GameDirectories.CURRENT_SCHEMA,
                GameDirectories::new);

        JsonSettingFile.LoadResult<GameDirectories> result = file.load(null);

        assertFalse(result.allowSave());
        assertEquals("{", Files.readString(location));
    }

    /// Creates a temporary directory under Gradle's build directory for JsonSettingFile tests.
    private static Path createJsonSettingFileTestDirectory(String prefix) throws IOException {
        Path root = Path.of("build", "tmp", "json-setting-file-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix + "-");
    }
}
