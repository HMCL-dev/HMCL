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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached game directory migration.
@NotNullByDefault
public final class GameDirectoriesTest {
    /// Tests extracting legacy configuration data into a detached game directory store.
    @Test
    public void extractsConfigurationsFromLegacyConfigJson() {
        GameDirectoryID id = LegacyConfigMigrator.getLegacyProfileID("Dev");
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
        assertEquals("Dev", GameDirectoryManager.getGameDirectoryCustomName(gameDirectories.getGameDirectories().get(0)));
        assertEquals(".minecraft", gameDirectories.getGameDirectories().get(0).getPath().getPath());
        assertNull(gameDirectories.getGameDirectories().get(0).getLegacyGameSettings());
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

        GameDirectoryID defaultGameDirectoryId = GameDirectoryManager.DEFAULT_GAME_DIRECTORY_ID;
        GameDirectoryID homeGameDirectoryId = GameDirectoryManager.HOME_GAME_DIRECTORY_ID;
        GameDirectory defaultGameDirectory = gameDirectories.getGameDirectories().stream()
                .filter(gameDirectory -> defaultGameDirectoryId.equals(gameDirectory.getId()))
                .findFirst()
                .orElseThrow();
        GameDirectory homeGameDirectory = gameDirectories.getGameDirectories().stream()
                .filter(gameDirectory -> homeGameDirectoryId.equals(gameDirectory.getId()))
                .findFirst()
                .orElseThrow();
        GameDirectory devGameDirectory = gameDirectories.getGameDirectories().stream()
                .filter(gameDirectory -> "Dev".equals(GameDirectoryManager.getGameDirectoryCustomName(gameDirectory)))
                .findFirst()
                .orElseThrow();

        assertNull(defaultGameDirectory.getName());
        assertNull(homeGameDirectory.getName());
        assertEquals("Dev", GameDirectoryManager.getGameDirectoryCustomName(devGameDirectory));
    }

    /// Tests migrating legacy selected version fields into the main config.
    @Test
    public void migratesLegacySelectedVersionsFromConfigurations() {
        GameDirectoryID id = LegacyConfigMigrator.getLegacyProfileID("Dev");
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
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedGameDirectory(settings));
        GameDirectories gameDirectories = Objects.requireNonNull(LegacyConfigMigrator.extractGameDirectoriesFromConfigJson(settings));
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

    /// Tests that an unknown legacy selected profile name is not migrated into an invalid ID.
    @Test
    public void ignoresUnknownLegacySelectedGameDirectory() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "last": "Missing",
                  "configurations": {
                    "Dev": {
                      "gameDir": ".minecraft"
                    }
                  }
                }
                """).getAsJsonObject();

        assertTrue(LegacyConfigMigrator.migrateLegacySelectedGameDirectory(settings));
        LauncherSettings config = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertFalse(settings.has("last"));
        assertFalse(settings.has(LauncherSettings.PROPERTY_SELECTED_GAME_DIRECTORY));
        assertNull(config.selectedGameDirectoryProperty().get());
    }

    /// Tests that game directories store their path as a portable path.
    @Test
    public void storesGameDirectoryPath() {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectory gameDirectory = new GameDirectory(id, LocalizedText.plain("Dev"), PortablePath.of("versions\\Dev"));

        JsonObject serialized = JsonUtils.GSON.toJsonTree(gameDirectory, GameDirectory.class).getAsJsonObject();
        GameDirectory deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, GameDirectory.class));

        assertEquals("versions/Dev", serialized.get("path").getAsString());
        assertEquals("Dev", serialized.get("name").getAsString());
        assertFalse(serialized.has("gameDir"));
        assertFalse(serialized.has("useRelativePath"));
        assertEquals("versions/Dev", deserialized.getPath().getPath());
        assertFalse(deserialized.getPath().isAbsolute());
    }

    /// Tests that game directories preserve migrated legacy game settings IDs.
    @Test
    public void storesLegacyGameSettingsId() {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyGameSettings =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
        GameDirectory gameDirectory = new GameDirectory(
                id,
                LocalizedText.plain("Dev"),
                PortablePath.of("versions\\Dev"),
                legacyGameSettings);

        JsonObject serialized = JsonUtils.GSON.toJsonTree(gameDirectory, GameDirectory.class).getAsJsonObject();
        GameDirectory deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, GameDirectory.class));

        assertEquals(legacyGameSettings.toString(), serialized.get("legacyGameSettings").getAsString());
        assertEquals(legacyGameSettings, deserialized.getLegacyGameSettings());
    }

    /// Tests that the merged game directory view is read-only and does not own the backing store data.
    @Test
    public void keepsShadowedUserGameDirectoryInBackingStore() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectory userGameDirectory = new GameDirectory(id, LocalizedText.plain("User"), PortablePath.of("user/Dev"));
        GameDirectory localGameDirectory = new GameDirectory(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectory addedGameDirectory = new GameDirectory(
                GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174001"),
                LocalizedText.plain("Added"),
                PortablePath.of("local/Added"));
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.getGameDirectories().add(userGameDirectory);
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(localGameDirectory);
        localDirectories.setUserFile(false);

        try (GameDirectoryEnvironment ignored = new GameDirectoryEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();
            ObservableList<GameDirectory> gameDirectories = GameDirectoryManager.getGameDirectories();

            assertEquals(List.of(localGameDirectory), gameDirectories);
            assertThrows(UnsupportedOperationException.class, () -> gameDirectories.add(addedGameDirectory));
            assertSame(localGameDirectory, GameDirectoryManager.getSelectedGameDirectory());
            assertEquals(localGameDirectory.getId(), settings().selectedGameDirectoryProperty().get());
            assertEquals(List.of(userGameDirectory), userDirectories.getGameDirectories());
            assertEquals(List.of(localGameDirectory), localDirectories.getGameDirectories());

            GameDirectoryManager.removeGameDirectory(localGameDirectory);
            assertEquals(List.of(userGameDirectory), GameDirectoryManager.getGameDirectories());
            assertSame(userGameDirectory, GameDirectoryManager.getSelectedGameDirectory());
            assertEquals(userGameDirectory.getId(), settings().selectedGameDirectoryProperty().get());
            assertEquals(List.of(userGameDirectory), userDirectories.getGameDirectories());
            assertTrue(localDirectories.getGameDirectories().isEmpty());

            GameDirectoryManager.addLocalGameDirectory(addedGameDirectory);
            assertEquals(List.of(addedGameDirectory, userGameDirectory), GameDirectoryManager.getGameDirectories());
            assertEquals(List.of(addedGameDirectory), localDirectories.getGameDirectories());
            assertSame(userGameDirectory, GameDirectoryManager.getSelectedGameDirectory());

            GameDirectoryManager.setSelectedGameDirectory(addedGameDirectory);
            assertSame(addedGameDirectory, GameDirectoryManager.getSelectedGameDirectory());
            assertEquals(addedGameDirectory.getId(), settings().selectedGameDirectoryProperty().get());
        }
    }

    /// Tests that editing a game directory moves it to the store selected by its new path type.
    @Test
    public void movesGameDirectoryBetweenStoresWhenPathTypeChanges() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectory gameDirectory = new GameDirectory(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        localDirectories.setUserFile(false);

        try (GameDirectoryEnvironment ignored = new GameDirectoryEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            PortablePath absolutePath = PortablePath.of("/workspace/Dev");
            GameDirectoryManager.updateGameDirectory(gameDirectory, LocalizedText.plain("Moved"), absolutePath);

            assertTrue(localDirectories.getGameDirectories().isEmpty());
            assertEquals(List.of(gameDirectory), userDirectories.getGameDirectories());
            assertEquals(List.of(gameDirectory), GameDirectoryManager.getGameDirectories());
            assertSame(gameDirectory, GameDirectoryManager.getSelectedGameDirectory());
            assertEquals(absolutePath.getPath(), gameDirectory.getPath().getPath());
            assertEquals(absolutePath.isAbsolute(), gameDirectory.getPath().isAbsolute());
            assertEquals("Moved", GameDirectoryManager.getGameDirectoryCustomName(gameDirectory));

            PortablePath relativePath = PortablePath.of("local/Dev");
            GameDirectoryManager.updateGameDirectory(gameDirectory, LocalizedText.plain("Back"), relativePath);

            assertEquals(List.of(gameDirectory), localDirectories.getGameDirectories());
            assertTrue(userDirectories.getGameDirectories().isEmpty());
            assertEquals(List.of(gameDirectory), GameDirectoryManager.getGameDirectories());
            assertSame(gameDirectory, GameDirectoryManager.getSelectedGameDirectory());
            assertEquals(relativePath.getPath(), gameDirectory.getPath().getPath());
            assertEquals(relativePath.isAbsolute(), gameDirectory.getPath().isAbsolute());
            assertEquals("Back", GameDirectoryManager.getGameDirectoryCustomName(gameDirectory));
        }
    }

    /// Tests that game directory mutations reject writes to read-only source and target stores.
    @Test
    public void rejectsGameDirectoryMutationsWithReadOnlyStores() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectory gameDirectory = new GameDirectory(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        localDirectories.setUserFile(false);

        try (GameDirectoryEnvironment environment = new GameDirectoryEnvironment(localDirectories, userDirectories)) {
            environment.setLocalGameDirectoriesAccess(SettingFileAccess.READ_ONLY);
            GameDirectoryManager.init();

            PortablePath newPath = PortablePath.of("local/Renamed");
            assertFalse(GameDirectoryManager.canUpdateGameDirectory(gameDirectory, newPath));
            assertThrows(IllegalStateException.class,
                    () -> GameDirectoryManager.updateGameDirectory(gameDirectory, LocalizedText.plain("Renamed"), newPath));
            assertFalse(GameDirectoryManager.canRemoveGameDirectory(gameDirectory));
            assertThrows(IllegalStateException.class, () -> GameDirectoryManager.removeGameDirectory(gameDirectory));
            assertEquals(List.of(gameDirectory), localDirectories.getGameDirectories());
            assertTrue(userDirectories.getGameDirectories().isEmpty());
            assertEquals("local/Dev", gameDirectory.getPath().getPath());
            assertFalse(gameDirectory.getPath().isAbsolute());
            assertEquals("Local", GameDirectoryManager.getGameDirectoryCustomName(gameDirectory));
        }

        GameDirectory targetGameDirectory = new GameDirectory(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectories targetUserDirectories = new GameDirectories();
        targetUserDirectories.setUserFile(true);
        GameDirectories targetLocalDirectories = new GameDirectories();
        targetLocalDirectories.getGameDirectories().add(targetGameDirectory);
        targetLocalDirectories.setUserFile(false);

        try (GameDirectoryEnvironment environment = new GameDirectoryEnvironment(targetLocalDirectories, targetUserDirectories)) {
            environment.setUserGameDirectoriesAccess(SettingFileAccess.READ_ONLY);
            GameDirectoryManager.init();

            PortablePath absolutePath = PortablePath.of("/workspace/Dev");
            assertFalse(GameDirectoryManager.canUpdateGameDirectory(targetGameDirectory, absolutePath));
            assertThrows(IllegalStateException.class,
                    () -> GameDirectoryManager.updateGameDirectory(targetGameDirectory, LocalizedText.plain("Moved"), absolutePath));
            assertEquals(List.of(targetGameDirectory), targetLocalDirectories.getGameDirectories());
            assertTrue(targetUserDirectories.getGameDirectories().isEmpty());
            assertEquals("local/Dev", targetGameDirectory.getPath().getPath());
            assertFalse(targetGameDirectory.getPath().isAbsolute());
            assertEquals("Local", GameDirectoryManager.getGameDirectoryCustomName(targetGameDirectory));
        }
    }

    /// Tests that default game directories are created when both loaded stores are empty.
    @Test
    public void createsDefaultGameDirectoriesWhenMergedGameDirectoriesAreEmpty() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);

        try (GameDirectoryEnvironment ignored = new GameDirectoryEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            GameDirectory localGameDirectory = assertSingleDefaultGameDirectory(localDirectories, PortablePath.of(".minecraft"));
            GameDirectory userGameDirectory = assertSingleDefaultGameDirectory(
                    userDirectories,
                    PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY));
            assertEquals(GameDirectoryManager.DEFAULT_GAME_DIRECTORY_ID, localGameDirectory.getId());
            assertEquals(GameDirectoryManager.HOME_GAME_DIRECTORY_ID, userGameDirectory.getId());
            assertEquals(2, GameDirectoryManager.getGameDirectories().size());
            assertTrue(GameDirectoryManager.getGameDirectories().contains(localGameDirectory));
            assertTrue(GameDirectoryManager.getGameDirectories().contains(userGameDirectory));
        }
    }

    /// Tests that newly created stores use the stable IDs for their built-in game directories.
    @Test
    public void createsBuiltInGameDirectoriesWithStableIdsForNewStores() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        userDirectories.setNewlyCreated(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);
        localDirectories.setNewlyCreated(true);

        try (GameDirectoryEnvironment ignored = new GameDirectoryEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            GameDirectory localGameDirectory = assertSingleDefaultGameDirectory(localDirectories, PortablePath.of(".minecraft"));
            GameDirectory userGameDirectory = assertSingleDefaultGameDirectory(
                    userDirectories,
                    PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY));
            assertEquals(GameDirectoryManager.DEFAULT_GAME_DIRECTORY_ID, localGameDirectory.getId());
            assertEquals(GameDirectoryManager.HOME_GAME_DIRECTORY_ID, userGameDirectory.getId());
        }
    }

    /// Tests that the selected game directory getter restores a valid selection instead of returning null.
    @Test
    public void selectedGameDirectoryGetterNeverReturnsNullWhenGameDirectoriesAreLoaded() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);

        try (GameDirectoryEnvironment ignored = new GameDirectoryEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            GameDirectory selected = GameDirectoryManager.getSelectedGameDirectory();
            assertNotNull(selected);
            assertSame(selected, GameDirectoryManager.getSelectedGameDirectory());
            assertTrue(GameDirectoryManager.getGameDirectories().contains(selected));
            assertEquals(selected.getId(), settings().selectedGameDirectoryProperty().get());
        }
    }

    /// Tests that a repository follows changes to its game directory path.
    @Test
    public void repositoryDirectoryFollowsGameDirectoryPath() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);
        GameDirectory gameDirectory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Local"),
                PortablePath.of("local/Dev"));
        localDirectories.getGameDirectories().add(gameDirectory);

        try (GameDirectoryEnvironment ignored = new GameDirectoryEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
            assertSame(gameDirectory, repository.getGameDirectory());
            assertEquals(gameDirectory.getPath().toPath(), repository.getBaseDirectory());

            PortablePath newPath = PortablePath.of("local/Renamed");
            gameDirectory.setPath(newPath);
            assertEquals(newPath.toPath(), repository.getBaseDirectory());
        }
    }

    /// Tests that new isolated installing instances resolve content directories under the version root before metadata is saved.
    @Test
    public void newIsolatedInstallingInstanceUsesVersionRootBeforeVersionExists(@TempDir Path tempDirectory)
            throws ReflectiveOperationException {
        GameSettingsPresetID defaultPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174002");
        GameSettings.Preset defaultPreset = new GameSettings.Preset(defaultPresetId);
        defaultPreset.defaultIsolationTypeProperty().setValue(DefaultIsolationType.MODDED);
        GameSettingsPresets presets = new GameSettingsPresets();
        presets.getPresets().setAll(defaultPreset);

        GameDirectory gameDirectory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Dev"),
                PortablePath.of(tempDirectory.toString()));
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        GameDirectories userDirectories = new GameDirectories();

        try (GameDirectoryEnvironment ignored =
                     new GameDirectoryEnvironment(localDirectories, userDirectories, presets)) {
            settings().defaultGameSettingsPresetProperty().set(defaultPresetId);
            HMCLGameRepository repository = new HMCLGameRepository(gameDirectory);
            String id = "1.21.11-fabric";

            assertFalse(repository.hasVersion(id));
            assertEquals(repository.getBaseDirectory(), repository.getRunDirectory(id));

            repository.applyDefaultIsolationSettingForNewInstance(id, true);

            assertEquals(repository.getVersionRoot(id), repository.getRunDirectory(id));
            assertEquals(repository.getVersionRoot(id).resolve("mods"), repository.getModsDirectory(id));

            assertTrue(repository.removeVersionFromDisk(id));
            assertEquals(repository.getBaseDirectory(), repository.getRunDirectory(id));
        }
    }

    /// Tests that instance settings without an explicit parent use the default preset.
    @Test
    public void nullInstanceParentUsesDefaultPresetInsteadOfLegacyGameDirectoryPreset()
            throws ReflectiveOperationException {
        GameSettingsPresetID defaultPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
        GameSettings.Preset defaultPreset = new GameSettings.Preset(defaultPresetId);
        GameSettings.Preset legacyPreset = new GameSettings.Preset(legacyPresetId);
        GameSettingsPresets presets = new GameSettingsPresets();
        presets.getPresets().setAll(defaultPreset, legacyPreset);

        GameDirectory gameDirectory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Dev"),
                PortablePath.of("local/Dev"),
                legacyPresetId);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        GameDirectories userDirectories = new GameDirectories();

        try (GameDirectoryEnvironment ignored =
                     new GameDirectoryEnvironment(localDirectories, userDirectories, presets)) {
            settings().defaultGameSettingsPresetProperty().set(defaultPresetId);
            HMCLGameRepository repository = new HMCLGameRepository(gameDirectory);

            assertSame(defaultPreset, repository.getParentGameSettings(new GameSettings.Instance()));
        }
    }

    /// Tests that legacy per-version settings migration stores the game directory preset as an explicit parent.
    @Test
    public void legacyInstanceSettingsMigrationStoresLegacyGameDirectoryPresetAsParent(@TempDir Path tempDirectory)
            throws IOException, ReflectiveOperationException {
        GameSettingsPresetID defaultPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
        GameSettingsPresets presets = new GameSettingsPresets();
        presets.getPresets().setAll(
                new GameSettings.Preset(defaultPresetId),
                new GameSettings.Preset(legacyPresetId));

        GameDirectory gameDirectory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Dev"),
                PortablePath.of(tempDirectory.toString()),
                legacyPresetId);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        GameDirectories userDirectories = new GameDirectories();

        try (GameDirectoryEnvironment ignored =
                     new GameDirectoryEnvironment(localDirectories, userDirectories, presets)) {
            settings().defaultGameSettingsPresetProperty().set(defaultPresetId);
            HMCLGameRepository repository = new HMCLGameRepository(gameDirectory);
            Path versionRoot = repository.getVersionRoot("1.20.1");
            Files.createDirectories(versionRoot);
            Files.writeString(versionRoot.resolve("hmclversion.cfg"), """
                    {
                      "usesGlobal": true
                    }
                    """);

            GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettings("1.20.1"));

            assertEquals(legacyPresetId, setting.parentProperty().getValue());
        }
    }

    /// Tests that startup migration leaves legacy per-version settings for lazy migration.
    @Test
    public void startupMigrationSkipsLegacyInstanceSettingsFile(@TempDir Path tempDirectory)
            throws IOException, ReflectiveOperationException {
        GameSettingsPresetID defaultPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
        GameSettingsPresets presets = new GameSettingsPresets();
        presets.getPresets().setAll(
                new GameSettings.Preset(defaultPresetId),
                new GameSettings.Preset(legacyPresetId));

        GameDirectory gameDirectory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Dev"),
                PortablePath.of(tempDirectory.toString()),
                legacyPresetId);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        GameDirectories userDirectories = new GameDirectories();

        try (GameDirectoryEnvironment ignored =
                     new GameDirectoryEnvironment(localDirectories, userDirectories, presets)) {
            settings().defaultGameSettingsPresetProperty().set(defaultPresetId);
            HMCLGameRepository repository = new HMCLGameRepository(gameDirectory);
            writeVersionJson(repository, "1.20.1");
            Path versionRoot = repository.getVersionRoot("1.20.1");
            Files.writeString(versionRoot.resolve(LegacyGameSettingsMigrator.LEGACY_INSTANCE_SETTINGS_FILENAME), """
                    {
                      "usesGlobal": true
                    }
                    """);

            LegacyConfigMigrator.migrateLegacyInstanceGameSettings(localDirectories, presets);

            assertFalse(Files.exists(repository.getInstanceConfigDirectory("1.20.1")
                    .resolve(LegacyGameSettingsMigrator.INSTANCE_GAME_SETTINGS_FILENAME)));
            GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettings("1.20.1"));
            assertEquals(legacyPresetId, setting.parentProperty().getValue());
        }
    }

    /// Tests that existing versions without instance settings store the legacy game directory parent.
    @Test
    public void absentInstanceSettingsStoreLegacyGameDirectoryPresetAsParent(@TempDir Path tempDirectory)
            throws IOException, ReflectiveOperationException {
        GameSettingsPresetID defaultPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
        GameSettings.Preset legacyPreset = new GameSettings.Preset(legacyPresetId);
        legacyPreset.defaultIsolationTypeProperty().setValue(DefaultIsolationType.ALWAYS);
        GameSettingsPresets presets = new GameSettingsPresets();
        presets.getPresets().setAll(
                new GameSettings.Preset(defaultPresetId),
                legacyPreset);

        GameDirectory gameDirectory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Dev"),
                PortablePath.of(tempDirectory.toString()),
                legacyPresetId);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        GameDirectories userDirectories = new GameDirectories();

        try (GameDirectoryEnvironment ignored =
                     new GameDirectoryEnvironment(localDirectories, userDirectories, presets)) {
            settings().defaultGameSettingsPresetProperty().set(defaultPresetId);
            HMCLGameRepository repository = new HMCLGameRepository(gameDirectory);
            writeVersionJson(repository, "1.20.1");
            LegacyConfigMigrator.migrateLegacyInstanceGameSettings(localDirectories, presets);

            GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettings("1.20.1"));

            assertEquals(legacyPresetId, setting.parentProperty().getValue());
            assertTrue(setting.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY));
        }
    }

    /// Tests that versions created after migration do not inherit the legacy game directory parent.
    @Test
    public void newInstanceAfterMigrationDoesNotUseLegacyGameDirectoryParent(@TempDir Path tempDirectory)
            throws IOException, ReflectiveOperationException {
        GameSettingsPresetID defaultPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyPresetId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
        GameSettings.Preset defaultPreset = new GameSettings.Preset(defaultPresetId);
        defaultPreset.defaultIsolationTypeProperty().setValue(DefaultIsolationType.NEVER);
        GameSettings.Preset legacyPreset = new GameSettings.Preset(legacyPresetId);
        legacyPreset.defaultIsolationTypeProperty().setValue(DefaultIsolationType.ALWAYS);
        GameSettingsPresets presets = new GameSettingsPresets();
        presets.getPresets().setAll(defaultPreset, legacyPreset);

        GameDirectory gameDirectory = new GameDirectory(
                GameDirectoryID.generate(),
                LocalizedText.plain("Dev"),
                PortablePath.of(tempDirectory.toString()),
                legacyPresetId);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(gameDirectory);
        GameDirectories userDirectories = new GameDirectories();

        try (GameDirectoryEnvironment ignored =
                     new GameDirectoryEnvironment(localDirectories, userDirectories, presets)) {
            settings().defaultGameSettingsPresetProperty().set(defaultPresetId);
            HMCLGameRepository repository = new HMCLGameRepository(gameDirectory);
            LegacyConfigMigrator.migrateLegacyInstanceGameSettings(localDirectories, presets);
            writeVersionJson(repository, "1.20.1");

            assertNull(repository.getInstanceGameSettings("1.20.1"));
        }
    }

    /// Temporary static state override for game directory tests.
    private static final class GameDirectoryEnvironment implements AutoCloseable {
        /// The reflected SettingsManager local game directories field.
        private final Field localGameDirectoriesField;

        /// The reflected SettingsManager user game directories field.
        private final Field userGameDirectoriesField;

        /// The reflected SettingsManager launcher settings field.
        private final Field launcherSettingsField;

        /// The reflected SettingsManager game settings presets field.
        private final Field gameSettingsPresetsField;

        /// The reflected SettingsManager local game directories access field.
        private final Field localGameDirectoriesAccessField;

        /// The reflected SettingsManager user game directories access field.
        private final Field userGameDirectoriesAccessField;

        /// The reflected GameDirectoryManager initialized field.
        private final Field initializedField;

        /// The reflected GameDirectoryManager selected game directory property.
        private final ObjectProperty<GameDirectory> selectedGameDirectory;

        /// The reflected GameDirectoryManager selected repository property.
        private final ObjectProperty<HMCLGameRepository> selectedRepository;

        /// The merged game directory list used by GameDirectoryManager.
        private final ObservableList<GameDirectory> mergedGameDirectories;

        /// The repositories mapped by GameDirectoryManager.
        private final Map<GameDirectory, HMCLGameRepository> repositories;

        /// The previous local game directories instance.
        private final Object previousLocalGameDirectories;

        /// The previous user game directories instance.
        private final Object previousUserGameDirectories;

        /// The previous launcher settings instance.
        private final Object previousLauncherSettings;

        /// The previous game settings presets instance.
        private final Object previousGameSettingsPresets;

        /// The previous local game directories access.
        private final SettingFileAccess previousLocalGameDirectoriesAccess;

        /// The previous user game directories access.
        private final SettingFileAccess previousUserGameDirectoriesAccess;

        /// The previous GameDirectoryManager initialization state.
        private final boolean previousInitialized;

        /// The previous selected game directory.
        private final GameDirectory previousSelectedGameDirectory;

        /// The previous selected repository.
        private final HMCLGameRepository previousSelectedRepository;

        /// The previous merged game directories.
        private final List<GameDirectory> previousMergedGameDirectories;

        /// The previous repository map entries.
        private final Map<GameDirectory, HMCLGameRepository> previousRepositories;

        /// Replaces game-directory-related static state with the given stores and an empty preset store.
        private GameDirectoryEnvironment(GameDirectories localDirectories, GameDirectories userDirectories)
                throws ReflectiveOperationException {
            this(localDirectories, userDirectories, new GameSettingsPresets());
        }

        /// Replaces game-directory-related static state with the given stores.
        private GameDirectoryEnvironment(
                GameDirectories localDirectories,
                GameDirectories userDirectories,
                GameSettingsPresets gameSettingsPresets)
                throws ReflectiveOperationException {
            localGameDirectoriesField = SettingsManager.class.getDeclaredField("localGameDirectories");
            userGameDirectoriesField = SettingsManager.class.getDeclaredField("userGameDirectories");
            launcherSettingsField = SettingsManager.class.getDeclaredField("launcherSettings");
            gameSettingsPresetsField = SettingsManager.class.getDeclaredField("gameSettingsPresets");
            localGameDirectoriesAccessField = SettingsManager.class.getDeclaredField("localGameDirectoriesAccess");
            userGameDirectoriesAccessField = SettingsManager.class.getDeclaredField("userGameDirectoriesAccess");
            initializedField = GameDirectoryManager.class.getDeclaredField("initialized");
            Field selectedGameDirectoryField = GameDirectoryManager.class.getDeclaredField("selectedGameDirectory");
            Field selectedRepositoryField = GameDirectoryManager.class.getDeclaredField("selectedRepository");
            Field mergedGameDirectoriesField = GameDirectoryManager.class.getDeclaredField("mergedGameDirectories");
            Field repositoriesField = GameDirectoryManager.class.getDeclaredField("repositories");
            localGameDirectoriesField.setAccessible(true);
            userGameDirectoriesField.setAccessible(true);
            launcherSettingsField.setAccessible(true);
            gameSettingsPresetsField.setAccessible(true);
            localGameDirectoriesAccessField.setAccessible(true);
            userGameDirectoriesAccessField.setAccessible(true);
            initializedField.setAccessible(true);
            selectedGameDirectoryField.setAccessible(true);
            selectedRepositoryField.setAccessible(true);
            mergedGameDirectoriesField.setAccessible(true);
            repositoriesField.setAccessible(true);

            previousLocalGameDirectories = localGameDirectoriesField.get(null);
            previousUserGameDirectories = userGameDirectoriesField.get(null);
            previousLauncherSettings = launcherSettingsField.get(null);
            previousGameSettingsPresets = gameSettingsPresetsField.get(null);
            previousLocalGameDirectoriesAccess = (SettingFileAccess) localGameDirectoriesAccessField.get(null);
            previousUserGameDirectoriesAccess = (SettingFileAccess) userGameDirectoriesAccessField.get(null);
            previousInitialized = initializedField.getBoolean(null);

            @SuppressWarnings("unchecked")
            ObjectProperty<GameDirectory> selectedGameDirectory =
                    (ObjectProperty<GameDirectory>) selectedGameDirectoryField.get(null);
            this.selectedGameDirectory = selectedGameDirectory;
            previousSelectedGameDirectory = selectedGameDirectory.get();

            @SuppressWarnings("unchecked")
            ObjectProperty<HMCLGameRepository> selectedRepository =
                    (ObjectProperty<HMCLGameRepository>) selectedRepositoryField.get(null);
            this.selectedRepository = selectedRepository;
            previousSelectedRepository = selectedRepository.get();

            @SuppressWarnings("unchecked")
            ObservableList<GameDirectory> mergedGameDirectories =
                    (ObservableList<GameDirectory>) mergedGameDirectoriesField.get(null);
            this.mergedGameDirectories = mergedGameDirectories;
            previousMergedGameDirectories = List.copyOf(mergedGameDirectories);

            @SuppressWarnings("unchecked")
            Map<GameDirectory, HMCLGameRepository> repositories =
                    (Map<GameDirectory, HMCLGameRepository>) repositoriesField.get(null);
            this.repositories = repositories;
            previousRepositories = Map.copyOf(repositories);

            localGameDirectoriesField.set(null, localDirectories);
            userGameDirectoriesField.set(null, userDirectories);
            launcherSettingsField.set(null, new LauncherSettings());
            gameSettingsPresetsField.set(null, gameSettingsPresets);
            localGameDirectoriesAccessField.set(null, SettingFileAccess.READ_WRITE);
            userGameDirectoriesAccessField.set(null, SettingFileAccess.READ_WRITE);
            initializedField.setBoolean(null, false);
            mergedGameDirectories.clear();
            repositories.clear();
            selectedRepository.set(null);
        }

        /// Sets the local game directories access used by [SettingsManager].
        private void setLocalGameDirectoriesAccess(SettingFileAccess access) throws IllegalAccessException {
            localGameDirectoriesAccessField.set(null, access);
        }

        /// Sets the user game directories access used by [SettingsManager].
        private void setUserGameDirectoriesAccess(SettingFileAccess access) throws IllegalAccessException {
            userGameDirectoriesAccessField.set(null, access);
        }

        /// Restores the previous static state.
        @Override
        public void close() throws ReflectiveOperationException {
            if (previousSelectedGameDirectory != null) {
                selectedGameDirectory.set(previousSelectedGameDirectory);
            }
            selectedRepository.set(previousSelectedRepository);
            mergedGameDirectories.setAll(previousMergedGameDirectories);
            repositories.clear();
            repositories.putAll(previousRepositories);
            localGameDirectoriesField.set(null, previousLocalGameDirectories);
            userGameDirectoriesField.set(null, previousUserGameDirectories);
            launcherSettingsField.set(null, previousLauncherSettings);
            gameSettingsPresetsField.set(null, previousGameSettingsPresets);
            localGameDirectoriesAccessField.set(null, previousLocalGameDirectoriesAccess);
            userGameDirectoriesAccessField.set(null, previousUserGameDirectoriesAccess);
            initializedField.setBoolean(null, previousInitialized);
        }
    }

    /// Writes a minimal valid version json for repository refresh tests.
    private static void writeVersionJson(HMCLGameRepository repository, String id) throws IOException {
        Path versionRoot = repository.getVersionRoot(id);
        Files.createDirectories(versionRoot);
        Files.writeString(versionRoot.resolve(id + ".json"), """
                {
                  "id": "%s"
                }
                """.formatted(id));
    }

    /// Returns the only default game directory in the given store, asserting its path.
    private static GameDirectory assertSingleDefaultGameDirectory(GameDirectories gameDirectories, PortablePath path) {
        assertEquals(1, gameDirectories.getGameDirectories().size());
        GameDirectory gameDirectory = gameDirectories.getGameDirectories().get(0);
        assertEquals(path.isAbsolute(), gameDirectory.getPath().isAbsolute());
        assertEquals(path.getPath(), gameDirectory.getPath().getPath());
        return gameDirectory;
    }

    /// Tests that game directories must be deserialized with a non-nil ID.
    @Test
    public void rejectsNilGameDirectoryId() {
        assertThrows(JsonParseException.class, () -> JsonUtils.GSON.fromJson("""
                {
                  "id": "game-directory:00000000-0000-0000-0000-000000000000",
                  "name": "Dev",
                  "path": "versions/Dev"
                }
                """, GameDirectory.class));
    }

    /// Tests that newer minor-version schemas are reported as unsupported.
    @Test
    public void reportsNewerMinorSchemaAsUnsupported() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDir = createJsonSettingFileTestDirectory(fileSystem, "newer-minor-schema");
            Path location = tempDir.resolve("game-directories.json");
            Files.writeString(location, """
                    {
                      "$schema": "https://schemas.glavo.site/hmcl/game-directories/1.1.0",
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

            assertEquals(SettingFileAccess.READ_ONLY, result.access());
            assertFalse(result.value().isSavable());
            assertFalse(result.value().isBackupOnNextSave());
            assertEquals(new JsonSchema("https://schemas.glavo.site/hmcl/game-directories/1.1.0"),
                    result.value().getSchema());
        }
    }

    /// Tests that malformed detached files are backed up before fallback defaults overwrite them.
    @Test
    public void backsUpMalformedDetachedFileBeforeSavingFallback() throws IOException, InterruptedException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDir = createJsonSettingFileTestDirectory(fileSystem, "malformed");
            Path location = tempDir.resolve("game-directories.json");
            Files.writeString(location, "{");

            JsonSettingFile<GameDirectories> file = new JsonSettingFile<>(
                    location,
                    "game directories",
                    GameDirectories.class,
                    GameDirectories.CURRENT_SCHEMA,
                    GameDirectories::new);

            JsonSettingFile.LoadResult<GameDirectories> result = file.load(null);

            assertTrue(result.value().isSavable());
            assertTrue(result.value().isBackupOnNextSave());
            assertEquals(SettingFileAccess.READ_WRITE, result.access());
            file.save(result.value());
            FileSaver.waitForAllSaves();

            Path backup = location.resolveSibling("game-directories.json.1");
            assertEquals("{", Files.readString(backup));
            assertFalse(result.value().isBackupOnNextSave());
        }
    }

    /// Creates a temporary directory in an in-memory file system for JsonSettingFile tests.
    private static Path createJsonSettingFileTestDirectory(FileSystem fileSystem, String prefix) throws IOException {
        Path root = fileSystem.getPath("/json-setting-file-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix + "-");
    }
}
