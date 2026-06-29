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
        assertEquals("Dev", GameDirectoryManager.getProfileCustomName(gameDirectories.getGameDirectories().get(0)));
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

        GameDirectoryID defaultProfileId = LegacyConfigMigrator.getLegacyProfileID("Default");
        GameDirectoryID homeProfileId = LegacyConfigMigrator.getLegacyProfileID("Home");
        GameDirectoryProfile defaultProfile = gameDirectories.getGameDirectories().stream()
                .filter(profile -> defaultProfileId.equals(profile.getId()))
                .findFirst()
                .orElseThrow();
        GameDirectoryProfile homeProfile = gameDirectories.getGameDirectories().stream()
                .filter(profile -> homeProfileId.equals(profile.getId()))
                .findFirst()
                .orElseThrow();
        GameDirectoryProfile devProfile = gameDirectories.getGameDirectories().stream()
                .filter(profile -> "Dev".equals(GameDirectoryManager.getProfileCustomName(profile)))
                .findFirst()
                .orElseThrow();

        assertNull(defaultProfile.getName());
        assertNull(homeProfile.getName());
        assertEquals("Dev", GameDirectoryManager.getProfileCustomName(devProfile));
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

    /// Tests that profiles store their directory as a portable path.
    @Test
    public void storesProfilePath() {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectoryProfile profile = new GameDirectoryProfile(id, LocalizedText.plain("Dev"), PortablePath.of("versions\\Dev"));

        JsonObject serialized = JsonUtils.GSON.toJsonTree(profile, GameDirectoryProfile.class).getAsJsonObject();
        GameDirectoryProfile deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, GameDirectoryProfile.class));

        assertEquals("versions/Dev", serialized.get("path").getAsString());
        assertEquals("Dev", serialized.get("name").getAsString());
        assertFalse(serialized.has("gameDir"));
        assertFalse(serialized.has("useRelativePath"));
        assertEquals("versions/Dev", deserialized.getPath().getPath());
        assertFalse(deserialized.getPath().isAbsolute());
    }

    /// Tests that profiles preserve migrated legacy game settings IDs.
    @Test
    public void storesLegacyGameSettingsId() {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyGameSettings =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
        GameDirectoryProfile profile = new GameDirectoryProfile(
                id,
                LocalizedText.plain("Dev"),
                PortablePath.of("versions\\Dev"),
                legacyGameSettings);

        JsonObject serialized = JsonUtils.GSON.toJsonTree(profile, GameDirectoryProfile.class).getAsJsonObject();
        GameDirectoryProfile deserialized = Objects.requireNonNull(JsonUtils.GSON.fromJson(serialized, GameDirectoryProfile.class));

        assertEquals(legacyGameSettings.toString(), serialized.get("legacyGameSettings").getAsString());
        assertEquals(legacyGameSettings, deserialized.getLegacyGameSettings());
    }

    /// Tests that the merged profile view is read-only and does not own the backing profile data.
    @Test
    public void keepsShadowedUserProfileInBackingStore() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectoryProfile userProfile = new GameDirectoryProfile(id, LocalizedText.plain("User"), PortablePath.of("user/Dev"));
        GameDirectoryProfile localProfile = new GameDirectoryProfile(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectoryProfile addedProfile = new GameDirectoryProfile(
                GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174001"),
                LocalizedText.plain("Added"),
                PortablePath.of("local/Added"));
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.getGameDirectories().add(userProfile);
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(localProfile);
        localDirectories.setUserFile(false);

        try (ProfileEnvironment ignored = new ProfileEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();
            ObservableList<GameDirectoryProfile> gameDirectories = GameDirectoryManager.getProfiles();

            assertEquals(List.of(localProfile), gameDirectories);
            assertThrows(UnsupportedOperationException.class, () -> gameDirectories.add(addedProfile));
            assertSame(localProfile, GameDirectoryManager.getSelectedProfile());
            assertEquals(localProfile.getId(), settings().selectedGameDirectoryProperty().get());
            assertEquals(List.of(userProfile), userDirectories.getGameDirectories());
            assertEquals(List.of(localProfile), localDirectories.getGameDirectories());

            GameDirectoryManager.removeProfile(localProfile);
            assertEquals(List.of(userProfile), GameDirectoryManager.getProfiles());
            assertSame(userProfile, GameDirectoryManager.getSelectedProfile());
            assertEquals(userProfile.getId(), settings().selectedGameDirectoryProperty().get());
            assertEquals(List.of(userProfile), userDirectories.getGameDirectories());
            assertTrue(localDirectories.getGameDirectories().isEmpty());

            GameDirectoryManager.addLocalProfile(addedProfile);
            assertEquals(List.of(addedProfile, userProfile), GameDirectoryManager.getProfiles());
            assertEquals(List.of(addedProfile), localDirectories.getGameDirectories());
            assertSame(userProfile, GameDirectoryManager.getSelectedProfile());

            GameDirectoryManager.setSelectedProfile(addedProfile);
            assertSame(addedProfile, GameDirectoryManager.getSelectedProfile());
            assertEquals(addedProfile.getId(), settings().selectedGameDirectoryProperty().get());
        }
    }

    /// Tests that editing a profile moves it to the store selected by its new path type.
    @Test
    public void movesProfileBetweenStoresWhenPathTypeChanges() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectoryProfile profile = new GameDirectoryProfile(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(profile);
        localDirectories.setUserFile(false);

        try (ProfileEnvironment ignored = new ProfileEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            PortablePath absolutePath = PortablePath.of("/workspace/Dev");
            GameDirectoryManager.updateProfile(profile, LocalizedText.plain("Moved"), absolutePath);

            assertTrue(localDirectories.getGameDirectories().isEmpty());
            assertEquals(List.of(profile), userDirectories.getGameDirectories());
            assertEquals(List.of(profile), GameDirectoryManager.getProfiles());
            assertSame(profile, GameDirectoryManager.getSelectedProfile());
            assertEquals(absolutePath.getPath(), profile.getPath().getPath());
            assertEquals(absolutePath.isAbsolute(), profile.getPath().isAbsolute());
            assertEquals("Moved", GameDirectoryManager.getProfileCustomName(profile));

            PortablePath relativePath = PortablePath.of("local/Dev");
            GameDirectoryManager.updateProfile(profile, LocalizedText.plain("Back"), relativePath);

            assertEquals(List.of(profile), localDirectories.getGameDirectories());
            assertTrue(userDirectories.getGameDirectories().isEmpty());
            assertEquals(List.of(profile), GameDirectoryManager.getProfiles());
            assertSame(profile, GameDirectoryManager.getSelectedProfile());
            assertEquals(relativePath.getPath(), profile.getPath().getPath());
            assertEquals(relativePath.isAbsolute(), profile.getPath().isAbsolute());
            assertEquals("Back", GameDirectoryManager.getProfileCustomName(profile));
        }
    }

    /// Tests that profile mutations reject writes to read-only source and target stores.
    @Test
    public void rejectsProfileMutationsWithReadOnlyStores() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectoryProfile profile = new GameDirectoryProfile(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.getGameDirectories().add(profile);
        localDirectories.setUserFile(false);

        try (ProfileEnvironment environment = new ProfileEnvironment(localDirectories, userDirectories)) {
            environment.setLocalGameDirectoriesAccess(SettingFileAccess.READ_ONLY);
            GameDirectoryManager.init();

            PortablePath newPath = PortablePath.of("local/Renamed");
            assertFalse(GameDirectoryManager.canUpdateProfile(profile, newPath));
            assertThrows(IllegalStateException.class,
                    () -> GameDirectoryManager.updateProfile(profile, LocalizedText.plain("Renamed"), newPath));
            assertFalse(GameDirectoryManager.canRemoveProfile(profile));
            assertThrows(IllegalStateException.class, () -> GameDirectoryManager.removeProfile(profile));
            assertEquals(List.of(profile), localDirectories.getGameDirectories());
            assertTrue(userDirectories.getGameDirectories().isEmpty());
            assertEquals("local/Dev", profile.getPath().getPath());
            assertFalse(profile.getPath().isAbsolute());
            assertEquals("Local", GameDirectoryManager.getProfileCustomName(profile));
        }

        GameDirectoryProfile targetProfile = new GameDirectoryProfile(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        GameDirectories targetUserDirectories = new GameDirectories();
        targetUserDirectories.setUserFile(true);
        GameDirectories targetLocalDirectories = new GameDirectories();
        targetLocalDirectories.getGameDirectories().add(targetProfile);
        targetLocalDirectories.setUserFile(false);

        try (ProfileEnvironment environment = new ProfileEnvironment(targetLocalDirectories, targetUserDirectories)) {
            environment.setUserGameDirectoriesAccess(SettingFileAccess.READ_ONLY);
            GameDirectoryManager.init();

            PortablePath absolutePath = PortablePath.of("/workspace/Dev");
            assertFalse(GameDirectoryManager.canUpdateProfile(targetProfile, absolutePath));
            assertThrows(IllegalStateException.class,
                    () -> GameDirectoryManager.updateProfile(targetProfile, LocalizedText.plain("Moved"), absolutePath));
            assertEquals(List.of(targetProfile), targetLocalDirectories.getGameDirectories());
            assertTrue(targetUserDirectories.getGameDirectories().isEmpty());
            assertEquals("local/Dev", targetProfile.getPath().getPath());
            assertFalse(targetProfile.getPath().isAbsolute());
            assertEquals("Local", GameDirectoryManager.getProfileCustomName(targetProfile));
        }
    }

    /// Tests that default profiles are created when both loaded stores are empty.
    @Test
    public void createsDefaultProfilesWhenMergedProfilesAreEmpty() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);

        try (ProfileEnvironment ignored = new ProfileEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            GameDirectoryProfile localProfile = assertSingleDefaultProfile(localDirectories, PortablePath.of(".minecraft"));
            GameDirectoryProfile userProfile = assertSingleDefaultProfile(
                    userDirectories,
                    PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY));
            assertEquals(2, GameDirectoryManager.getProfiles().size());
            assertTrue(GameDirectoryManager.getProfiles().contains(localProfile));
            assertTrue(GameDirectoryManager.getProfiles().contains(userProfile));
        }
    }

    /// Tests that the selected profile getter restores a valid selection instead of returning null.
    @Test
    public void selectedProfileGetterNeverReturnsNullWhenProfilesAreLoaded() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);

        try (ProfileEnvironment ignored = new ProfileEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            GameDirectoryProfile selected = GameDirectoryManager.getSelectedProfile();
            assertNotNull(selected);
            assertSame(selected, GameDirectoryManager.getSelectedProfile());
            assertTrue(GameDirectoryManager.getProfiles().contains(selected));
            assertEquals(selected.getId(), settings().selectedGameDirectoryProperty().get());
        }
    }

    /// Tests that a repository follows changes to its owning profile path.
    @Test
    public void repositoryDirectoryFollowsProfilePath() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);
        GameDirectoryProfile profile = new GameDirectoryProfile(
                GameDirectoryID.generate(),
                LocalizedText.plain("Local"),
                PortablePath.of("local/Dev"));
        localDirectories.getGameDirectories().add(profile);

        try (ProfileEnvironment ignored = new ProfileEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();

            HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
            assertSame(profile, repository.getProfile());
            assertEquals(profile.getPath().toPath(), repository.getBaseDirectory());

            PortablePath newPath = PortablePath.of("local/Renamed");
            profile.setPath(newPath);
            assertEquals(newPath.toPath(), repository.getBaseDirectory());
        }
    }

    /// Temporary static state override for profile tests.
    private static final class ProfileEnvironment implements AutoCloseable {
        /// The reflected SettingsManager local game directories field.
        private final Field localGameDirectoriesField;

        /// The reflected SettingsManager user game directories field.
        private final Field userGameDirectoriesField;

        /// The reflected SettingsManager launcher settings field.
        private final Field launcherSettingsField;

        /// The reflected SettingsManager local game directories access field.
        private final Field localGameDirectoriesAccessField;

        /// The reflected SettingsManager user game directories access field.
        private final Field userGameDirectoriesAccessField;

        /// The reflected GameDirectoryManager initialized field.
        private final Field initializedField;

        /// The reflected GameDirectoryManager selected profile property.
        private final ObjectProperty<GameDirectoryProfile> selectedProfile;

        /// The reflected GameDirectoryManager selected repository property.
        private final ObjectProperty<HMCLGameRepository> selectedRepository;

        /// The merged profile list used by GameDirectoryManager.
        private final ObservableList<GameDirectoryProfile> mergedProfiles;

        /// The repositories mapped by GameDirectoryManager.
        private final Map<GameDirectoryProfile, HMCLGameRepository> repositories;

        /// The previous local game directories instance.
        private final Object previousLocalGameDirectories;

        /// The previous user game directories instance.
        private final Object previousUserGameDirectories;

        /// The previous launcher settings instance.
        private final Object previousLauncherSettings;

        /// The previous local game directories access.
        private final SettingFileAccess previousLocalGameDirectoriesAccess;

        /// The previous user game directories access.
        private final SettingFileAccess previousUserGameDirectoriesAccess;

        /// The previous GameDirectoryManager initialization state.
        private final boolean previousInitialized;

        /// The previous selected profile.
        private final GameDirectoryProfile previousSelectedProfile;

        /// The previous selected repository.
        private final HMCLGameRepository previousSelectedRepository;

        /// The previous merged profiles.
        private final List<GameDirectoryProfile> previousMergedProfiles;

        /// The previous repository map entries.
        private final Map<GameDirectoryProfile, HMCLGameRepository> previousRepositories;

        /// Replaces profile-related static state with the given stores.
        private ProfileEnvironment(GameDirectories localDirectories, GameDirectories userDirectories)
                throws ReflectiveOperationException {
            localGameDirectoriesField = SettingsManager.class.getDeclaredField("localGameDirectories");
            userGameDirectoriesField = SettingsManager.class.getDeclaredField("userGameDirectories");
            launcherSettingsField = SettingsManager.class.getDeclaredField("launcherSettings");
            localGameDirectoriesAccessField = SettingsManager.class.getDeclaredField("localGameDirectoriesAccess");
            userGameDirectoriesAccessField = SettingsManager.class.getDeclaredField("userGameDirectoriesAccess");
            initializedField = GameDirectoryManager.class.getDeclaredField("initialized");
            Field selectedProfileField = GameDirectoryManager.class.getDeclaredField("selectedProfile");
            Field selectedRepositoryField = GameDirectoryManager.class.getDeclaredField("selectedRepository");
            Field mergedProfilesField = GameDirectoryManager.class.getDeclaredField("mergedProfiles");
            Field repositoriesField = GameDirectoryManager.class.getDeclaredField("repositories");
            localGameDirectoriesField.setAccessible(true);
            userGameDirectoriesField.setAccessible(true);
            launcherSettingsField.setAccessible(true);
            localGameDirectoriesAccessField.setAccessible(true);
            userGameDirectoriesAccessField.setAccessible(true);
            initializedField.setAccessible(true);
            selectedProfileField.setAccessible(true);
            selectedRepositoryField.setAccessible(true);
            mergedProfilesField.setAccessible(true);
            repositoriesField.setAccessible(true);

            previousLocalGameDirectories = localGameDirectoriesField.get(null);
            previousUserGameDirectories = userGameDirectoriesField.get(null);
            previousLauncherSettings = launcherSettingsField.get(null);
            previousLocalGameDirectoriesAccess = (SettingFileAccess) localGameDirectoriesAccessField.get(null);
            previousUserGameDirectoriesAccess = (SettingFileAccess) userGameDirectoriesAccessField.get(null);
            previousInitialized = initializedField.getBoolean(null);

            @SuppressWarnings("unchecked")
            ObjectProperty<GameDirectoryProfile> selectedProfile =
                    (ObjectProperty<GameDirectoryProfile>) selectedProfileField.get(null);
            this.selectedProfile = selectedProfile;
            previousSelectedProfile = selectedProfile.get();

            @SuppressWarnings("unchecked")
            ObjectProperty<HMCLGameRepository> selectedRepository =
                    (ObjectProperty<HMCLGameRepository>) selectedRepositoryField.get(null);
            this.selectedRepository = selectedRepository;
            previousSelectedRepository = selectedRepository.get();

            @SuppressWarnings("unchecked")
            ObservableList<GameDirectoryProfile> mergedProfiles =
                    (ObservableList<GameDirectoryProfile>) mergedProfilesField.get(null);
            this.mergedProfiles = mergedProfiles;
            previousMergedProfiles = List.copyOf(mergedProfiles);

            @SuppressWarnings("unchecked")
            Map<GameDirectoryProfile, HMCLGameRepository> repositories =
                    (Map<GameDirectoryProfile, HMCLGameRepository>) repositoriesField.get(null);
            this.repositories = repositories;
            previousRepositories = Map.copyOf(repositories);

            localGameDirectoriesField.set(null, localDirectories);
            userGameDirectoriesField.set(null, userDirectories);
            launcherSettingsField.set(null, new LauncherSettings());
            localGameDirectoriesAccessField.set(null, SettingFileAccess.READ_WRITE);
            userGameDirectoriesAccessField.set(null, SettingFileAccess.READ_WRITE);
            initializedField.setBoolean(null, false);
            mergedProfiles.clear();
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
            if (previousSelectedProfile != null) {
                selectedProfile.set(previousSelectedProfile);
            }
            selectedRepository.set(previousSelectedRepository);
            mergedProfiles.setAll(previousMergedProfiles);
            repositories.clear();
            repositories.putAll(previousRepositories);
            localGameDirectoriesField.set(null, previousLocalGameDirectories);
            userGameDirectoriesField.set(null, previousUserGameDirectories);
            launcherSettingsField.set(null, previousLauncherSettings);
            localGameDirectoriesAccessField.set(null, previousLocalGameDirectoriesAccess);
            userGameDirectoriesAccessField.set(null, previousUserGameDirectoriesAccess);
            initializedField.setBoolean(null, previousInitialized);
        }
    }

    /// Returns the only default profile in the given store, asserting its path.
    private static GameDirectoryProfile assertSingleDefaultProfile(GameDirectories gameDirectories, PortablePath path) {
        assertEquals(1, gameDirectories.getGameDirectories().size());
        GameDirectoryProfile profile = gameDirectories.getGameDirectories().get(0);
        assertEquals(path.isAbsolute(), profile.getPath().isAbsolute());
        assertEquals(path.getPath(), profile.getPath().getPath());
        return profile;
    }

    /// Tests that profiles must be deserialized with a non-nil ID.
    @Test
    public void rejectsNilProfileId() {
        assertThrows(JsonParseException.class, () -> JsonUtils.GSON.fromJson("""
                {
                  "id": "game-directory:00000000-0000-0000-0000-000000000000",
                  "name": "Dev",
                  "path": "versions/Dev"
                }
                """, GameDirectoryProfile.class));
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
