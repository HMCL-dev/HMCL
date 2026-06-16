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
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileSystem;
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
        assertEquals("Dev", Profiles.getProfileCustomName(gameDirectories.getGameDirectories().get(0)));
        assertEquals(".minecraft", gameDirectories.getGameDirectories().get(0).getPath().getPath());
        assertNull(gameDirectories.getGameDirectories().get(0).getLegacyGameSettings());
    }

    /// Tests extracting the migrated legacy game settings ID from a legacy profile.
    @Test
    public void extractsLegacyGameSettingsIdFromLegacyProfileGlobalSettings() {
        GameDirectoryID profileId = LegacyConfigMigrator.getLegacyProfileID("Dev");
        GameSettingsPresetID legacyGameSettings = LegacyConfigMigrator.getLegacyGameSettingsID("Dev");
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

        GameDirectoryID defaultProfileId = LegacyConfigMigrator.getLegacyProfileID("Default");
        GameDirectoryID homeProfileId = LegacyConfigMigrator.getLegacyProfileID("Home");
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
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
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
                  "id": "game-directory:123e4567-e89b-12d3-a456-426614174000",
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
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameSettingsPresetID legacyGameSettings =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174001");
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
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
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
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        Profile profile = new Profile(id, LocalizedText.plain("Custom Default"), PortablePath.of(".minecraft"));

        assertEquals("Custom Default", Profiles.getProfileDisplayName(profile));
    }

    /// Tests that the merged profile view is read-only and does not own the backing profile data.
    @Test
    public void keepsShadowedUserProfileInBackingStore() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        Profile userProfile = new Profile(id, LocalizedText.plain("User"), PortablePath.of("user/Dev"));
        Profile localProfile = new Profile(id, LocalizedText.plain("Local"), PortablePath.of("local/Dev"));
        Profile addedProfile = new Profile(
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
            Profiles.init();
            ObservableList<Profile> gameDirectories = Profiles.getProfiles();

            assertEquals(List.of(localProfile), gameDirectories);
            assertThrows(UnsupportedOperationException.class, () -> gameDirectories.add(addedProfile));
            assertSame(localProfile, Profiles.getSelectedProfile());
            assertEquals(localProfile.getId(), SettingsManager.settings().selectedGameDirectoryProperty().get());
            assertEquals(List.of(userProfile), userDirectories.getGameDirectories());
            assertEquals(List.of(localProfile), localDirectories.getGameDirectories());

            Profiles.removeProfile(localProfile);
            assertEquals(List.of(userProfile), Profiles.getProfiles());
            assertSame(userProfile, Profiles.getSelectedProfile());
            assertEquals(userProfile.getId(), SettingsManager.settings().selectedGameDirectoryProperty().get());
            assertEquals(List.of(userProfile), userDirectories.getGameDirectories());
            assertTrue(localDirectories.getGameDirectories().isEmpty());

            Profiles.addLocalProfile(addedProfile);
            assertEquals(List.of(addedProfile, userProfile), Profiles.getProfiles());
            assertEquals(List.of(addedProfile), localDirectories.getGameDirectories());
            assertSame(userProfile, Profiles.getSelectedProfile());

            Profiles.setSelectedProfile(addedProfile);
            assertSame(addedProfile, Profiles.getSelectedProfile());
            assertEquals(addedProfile.getId(), SettingsManager.settings().selectedGameDirectoryProperty().get());
        }
    }

    /// Tests that default profiles are created while the game directory stores are loaded.
    @Test
    public void createsDefaultProfilesWhenLoadingEmptyStores() throws ReflectiveOperationException {
        GameDirectories userDirectories = new GameDirectories();
        userDirectories.setUserFile(true);
        GameDirectories localDirectories = new GameDirectories();
        localDirectories.setUserFile(false);
        userDirectories.setNewlyCreated(true);
        localDirectories.setNewlyCreated(true);

        try (ProfileEnvironment ignored = new ProfileEnvironment(localDirectories, userDirectories)) {
            Profiles.init();

            Profile localProfile = assertSingleDefaultProfile(localDirectories, PortablePath.of(".minecraft"));
            Profile userProfile = assertSingleDefaultProfile(
                    userDirectories,
                    PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY));
            assertEquals(2, Profiles.getProfiles().size());
            assertTrue(Profiles.getProfiles().contains(localProfile));
            assertTrue(Profiles.getProfiles().contains(userProfile));
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
            Profiles.init();

            Profile localProfile = assertSingleDefaultProfile(localDirectories, PortablePath.of(".minecraft"));
            Profile userProfile = assertSingleDefaultProfile(
                    userDirectories,
                    PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY));
            assertEquals(2, Profiles.getProfiles().size());
            assertTrue(Profiles.getProfiles().contains(localProfile));
            assertTrue(Profiles.getProfiles().contains(userProfile));
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
            Profiles.init();

            Profile selected = Profiles.getSelectedProfile();
            assertNotNull(selected);
            assertSame(selected, Profiles.getSelectedProfile());
            assertTrue(Profiles.getProfiles().contains(selected));
            assertEquals(selected.getId(), SettingsManager.settings().selectedGameDirectoryProperty().get());
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

        /// The reflected Profiles initialized field.
        private final Field initializedField;

        /// The reflected Profiles selected profile property.
        private final ObjectProperty<Profile> selectedProfile;

        /// The merged profile list used by Profiles.
        private final ObservableList<Profile> mergedProfiles;

        /// The previous local game directories instance.
        private final Object previousLocalGameDirectories;

        /// The previous user game directories instance.
        private final Object previousUserGameDirectories;

        /// The previous launcher settings instance.
        private final Object previousLauncherSettings;

        /// The previous Profiles initialization state.
        private final boolean previousInitialized;

        /// The previous selected profile.
        private final Profile previousSelectedProfile;

        /// The previous merged profiles.
        private final List<Profile> previousMergedProfiles;

        /// Replaces profile-related static state with the given stores.
        private ProfileEnvironment(GameDirectories localDirectories, GameDirectories userDirectories)
                throws ReflectiveOperationException {
            localGameDirectoriesField = SettingsManager.class.getDeclaredField("localGameDirectories");
            userGameDirectoriesField = SettingsManager.class.getDeclaredField("userGameDirectories");
            launcherSettingsField = SettingsManager.class.getDeclaredField("launcherSettings");
            initializedField = Profiles.class.getDeclaredField("initialized");
            Field selectedProfileField = Profiles.class.getDeclaredField("selectedProfile");
            Field mergedProfilesField = Profiles.class.getDeclaredField("mergedProfiles");
            localGameDirectoriesField.setAccessible(true);
            userGameDirectoriesField.setAccessible(true);
            launcherSettingsField.setAccessible(true);
            initializedField.setAccessible(true);
            selectedProfileField.setAccessible(true);
            mergedProfilesField.setAccessible(true);

            previousLocalGameDirectories = localGameDirectoriesField.get(null);
            previousUserGameDirectories = userGameDirectoriesField.get(null);
            previousLauncherSettings = launcherSettingsField.get(null);
            previousInitialized = initializedField.getBoolean(null);

            @SuppressWarnings("unchecked")
            ObjectProperty<Profile> selectedProfile =
                    (ObjectProperty<Profile>) selectedProfileField.get(null);
            this.selectedProfile = selectedProfile;
            previousSelectedProfile = selectedProfile.get();

            @SuppressWarnings("unchecked")
            ObservableList<Profile> mergedProfiles =
                    (ObservableList<Profile>) mergedProfilesField.get(null);
            this.mergedProfiles = mergedProfiles;
            previousMergedProfiles = List.copyOf(mergedProfiles);

            localGameDirectoriesField.set(null, localDirectories);
            userGameDirectoriesField.set(null, userDirectories);
            launcherSettingsField.set(null, new LauncherSettings());
            initializedField.setBoolean(null, false);
            mergedProfiles.clear();
        }

        /// Restores the previous static state.
        @Override
        public void close() throws ReflectiveOperationException {
            if (previousSelectedProfile != null) {
                selectedProfile.set(previousSelectedProfile);
            }
            mergedProfiles.setAll(previousMergedProfiles);
            localGameDirectoriesField.set(null, previousLocalGameDirectories);
            userGameDirectoriesField.set(null, previousUserGameDirectories);
            launcherSettingsField.set(null, previousLauncherSettings);
            initializedField.setBoolean(null, previousInitialized);
        }
    }

    /// Returns the only default profile in the given store, asserting its path.
    private static Profile assertSingleDefaultProfile(GameDirectories gameDirectories, PortablePath path) {
        assertEquals(1, gameDirectories.getGameDirectories().size());
        Profile profile = gameDirectories.getGameDirectories().get(0);
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
                """, Profile.class));
    }

    /// Tests that patch-version schemas are preserved together with unknown fields.
    @Test
    public void preservesPatchSchemaAndUnknownFields() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDir = createJsonSettingFileTestDirectory(fileSystem, "patch-schema");
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
            assertTrue(result.value().isSavable());
            assertEquals(SettingFileAccess.READ_WRITE, result.access());
            assertEquals(new JsonSchema("https://schemas.glavo.site/hmcl/game-directories/1.0.1"),
                    result.value().getSchema());

            JsonObject rewritten = JsonParser.parseString(JsonUtils.GSON.toJson(result.value(), GameDirectories.class))
                    .getAsJsonObject();
            assertEquals("https://schemas.glavo.site/hmcl/game-directories/1.0.1",
                    rewritten.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertTrue(rewritten.getAsJsonObject("futureField").get("enabled").getAsBoolean());
        }
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
