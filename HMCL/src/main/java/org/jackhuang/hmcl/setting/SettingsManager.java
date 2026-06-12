/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns the process-wide configuration and detached workspace settings instances.
@NotNullByDefault
public final class SettingsManager {

    /// Prevents instantiation.
    private SettingsManager() {
    }

    /// The user settings path shared by all workspaces.
    public static final Path USER_SETTINGS_LOCATION = Metadata.HMCL_USER_HOME.resolve("user-settings.json");

    /// The user state path shared by all workspaces.
    public static final Path USER_STATE_LOCATION = Metadata.HMCL_USER_HOME.resolve("user-state.json");

    /// The current per-workspace config path.
    private static final Path SETTINGS_LOCATION = Metadata.HMCL_LOCAL_HOME.resolve("settings.json");

    /// The current per-workspace launcher state path.
    private static final Path STATE_LOCATION = Metadata.HMCL_LOCAL_HOME.resolve("launcher-state.json");

    /// The current per-workspace authlib-injector server list path.
    private static final Path AUTHLIB_INJECTOR_SERVERS_LOCATION =
            Metadata.HMCL_LOCAL_HOME.resolve("authlib-injector-servers.json");

    /// The current per-workspace game directories path.
    private static final Path LOCAL_GAME_DIRECTORIES_LOCATION =
            Metadata.HMCL_LOCAL_HOME.resolve("game-directories.json");

    /// The current user game directories path.
    private static final Path USER_GAME_DIRECTORIES_LOCATION =
            Metadata.HMCL_USER_HOME.resolve("user-game-directories.json");

    /// The current per-workspace game settings path.
    private static final Path GAME_SETTINGS_LOCATION =
            Metadata.HMCL_LOCAL_HOME.resolve("game-settings.json");

    /// The current per-workspace account storage path.
    private static final Path GAME_ACCOUNTS_LOCATION =
            Metadata.HMCL_LOCAL_HOME.resolve("game-accounts.json");

    /// The shared account storage path.
    private static final Path USER_GAME_ACCOUNTS_LOCATION =
            Metadata.HMCL_USER_HOME.resolve("user-game-accounts.json");

    /// The per-workspace game directory file helper.
    private static final JsonSettingFile<GameDirectories> LOCAL_GAME_DIRECTORIES_FILE = new JsonSettingFile<>(
            LOCAL_GAME_DIRECTORIES_LOCATION,
            "game directories",
            GameDirectories.class,
            GameDirectories.CURRENT_SCHEMA,
            GameDirectories::new);

    /// The user game directory file helper.
    private static final JsonSettingFile<GameDirectories> USER_GAME_DIRECTORIES_FILE = new JsonSettingFile<>(
            USER_GAME_DIRECTORIES_LOCATION,
            "user game directories",
            GameDirectories.class,
            GameDirectories.CURRENT_SCHEMA,
            GameDirectories::new);

    /// The detached game settings file helper.
    private static final JsonSettingFile<GameSettingsPresets> GAME_SETTINGS_FILE = new JsonSettingFile<>(
            GAME_SETTINGS_LOCATION,
            "game settings",
            GameSettingsPresets.class,
            GameSettingsPresets.CURRENT_SCHEMA,
            GameSettingsPresets::new);

    /// The detached account storage file helper.
    private static final JsonSettingFile<AccountStorages> GAME_ACCOUNTS_FILE = new JsonSettingFile<>(
            GAME_ACCOUNTS_LOCATION,
            "game accounts",
            AccountStorages.class,
            AccountStorages.CURRENT_SCHEMA,
            AccountStorages::new);

    /// The shared account storage file helper.
    private static final JsonSettingFile<AccountStorages> USER_GAME_ACCOUNTS_FILE = new JsonSettingFile<>(
            USER_GAME_ACCOUNTS_LOCATION,
            "user game accounts",
            AccountStorages.class,
            AccountStorages.CURRENT_SCHEMA,
            AccountStorages::new);

    /// The detached launcher state file helper.
    private static final JsonSettingFile<LauncherState> STATE_FILE = new JsonSettingFile<>(
            STATE_LOCATION,
            "launcher state",
            LauncherState.class,
            LauncherState.CURRENT_SCHEMA,
            LauncherState::new);

    /// The detached authlib-injector server list file helper.
    private static final JsonSettingFile<AuthlibInjectorServerList> AUTHLIB_INJECTOR_SERVERS_FILE = new JsonSettingFile<>(
            AUTHLIB_INJECTOR_SERVERS_LOCATION,
            "authlib-injector servers",
            AuthlibInjectorServerList.class,
            AuthlibInjectorServerList.CURRENT_SCHEMA,
            AuthlibInjectorServerList::createDefault);

    /// The user settings file helper.
    private static final JsonSettingFile<UserSettings> USER_SETTINGS_FILE = new JsonSettingFile<>(
            USER_SETTINGS_LOCATION,
            "user settings",
            UserSettings.class,
            UserSettings.CURRENT_SCHEMA,
            UserSettings::new);

    /// The user state file helper.
    private static final JsonSettingFile<UserState> USER_STATE_FILE = new JsonSettingFile<>(
            USER_STATE_LOCATION,
            "user state",
            UserState.class,
            UserState.CURRENT_SCHEMA,
            UserState::new);

    /// The loaded per-workspace launcher settings.
    private static @UnknownNullability LauncherSettings launcherSettings;

    /// The loaded user settings instance.
    private static @UnknownNullability UserSettings userSettingsInstance;

    /// The loaded user state instance.
    private static @UnknownNullability UserState userStateInstance;

    /// The loaded per-workspace game directory file.
    private static @UnknownNullability GameDirectories localGameDirectories;

    /// The loaded user game directory file.
    private static @UnknownNullability GameDirectories userGameDirectories;

    /// The merged runtime game directory list.
    private static @UnknownNullability ObservableList<Profile> gameDirectories;

    /// The loaded detached preset store.
    private static @UnknownNullability GameSettingsPresets gameSettingsPresets;

    /// The loaded detached launcher state store.
    private static @UnknownNullability LauncherState launcherState;

    /// The loaded detached authlib-injector server list store.
    private static @UnknownNullability AuthlibInjectorServerList authlibInjectorServers;

    /// The loaded detached account storage store.
    private static @UnknownNullability AccountStorages gameAccounts;

    /// The loaded shared account storage store.
    private static @UnknownNullability AccountStorages userGameAccounts;

    /// Whether this run appears to be using a new workspace.
    private static boolean newlyCreated;

    /// Whether root is reading a per-workspace config owned by another user.
    private static boolean ownerChanged = false;

    /// Whether launcher settings or state could not be safely overwritten because of an unsupported schema.
    private static boolean unsupportedVersion = false;

    /// Returns the loaded per-workspace launcher settings.
    public static LauncherSettings settings() {
        if (launcherSettings == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return launcherSettings;
    }

    /// Returns the loaded user settings.
    public static UserSettings userSettings() {
        if (userSettingsInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return userSettingsInstance;
    }

    /// Returns the loaded user state.
    public static UserState userState() {
        if (userStateInstance == null) {
            throw new IllegalStateException("User state hasn't been loaded");
        }
        return userStateInstance;
    }

    /// Returns the loaded per-workspace launcher state.
    public static LauncherState state() {
        if (launcherState == null) {
            throw new IllegalStateException("Launcher state hasn't been loaded");
        }
        return launcherState;
    }

    /// Returns the loaded per-workspace authlib-injector server list.
    public static AuthlibInjectorServerList authlibInjectorServers() {
        if (authlibInjectorServers == null) {
            throw new IllegalStateException("Authlib-injector servers haven't been loaded");
        }
        return authlibInjectorServers;
    }

    /// Returns the current per-workspace config directory path.
    public static Path localConfigDirectory() {
        return Metadata.HMCL_LOCAL_HOME;
    }

    /// Returns the current per-workspace settings path.
    public static Path settingsLocation() {
        return SETTINGS_LOCATION;
    }

    /// Returns the current per-workspace launcher state path.
    public static Path stateLocation() {
        return STATE_LOCATION;
    }

    /// Returns the current per-workspace authlib-injector server list path.
    public static Path authlibInjectorServersLocation() {
        return AUTHLIB_INJECTOR_SERVERS_LOCATION;
    }

    /// Returns the current per-workspace game directories path.
    public static Path gameDirectoriesLocation() {
        return LOCAL_GAME_DIRECTORIES_LOCATION;
    }

    /// Returns the user game directories path.
    public static Path userGameDirectoriesLocation() {
        return USER_GAME_DIRECTORIES_LOCATION;
    }

    /// Returns the current per-workspace game settings path.
    public static Path gameSettingsLocation() {
        return GAME_SETTINGS_LOCATION;
    }

    /// Returns the current per-workspace account storage path.
    public static Path gameAccountsLocation() {
        return GAME_ACCOUNTS_LOCATION;
    }

    /// Returns the shared account storage path.
    public static Path userGameAccountsLocation() {
        return USER_GAME_ACCOUNTS_LOCATION;
    }

    /// Returns the loaded detached preset store.
    public static GameSettingsPresets gameSettingsPresets() {
        if (gameSettingsPresets == null) {
            throw new IllegalStateException("Game settings presets haven't been loaded");
        }
        return gameSettingsPresets;
    }

    /// Returns the loaded detached account storage store.
    static AccountStorages gameAccounts() {
        if (gameAccounts == null) {
            throw new IllegalStateException("Game accounts haven't been loaded");
        }
        return gameAccounts;
    }

    /// Returns the loaded shared account storage store.
    static AccountStorages userGameAccounts() {
        if (userGameAccounts == null) {
            throw new IllegalStateException("User game accounts haven't been loaded");
        }
        return userGameAccounts;
    }

    /// Returns the per-workspace authlib-injector servers.
    public static ObservableList<AuthlibInjectorServer> getAuthlibInjectorServers() {
        return authlibInjectorServers().getServers();
    }

    /// Returns the per-workspace account storages.
    public static ObservableList<Map<Object, Object>> getAccountStorages() {
        return gameAccounts().getAccounts();
    }

    /// Returns the shared account storages.
    public static ObservableList<Map<Object, Object>> getUserAccountStorages() {
        return userGameAccounts().getAccounts();
    }

    /// Serializes the per-workspace account storage file content.
    public static String gameAccountsToJson() {
        return JsonUtils.GSON.toJson(gameAccounts(), AccountStorages.class);
    }

    /// Returns the merged game directories.
    public static ObservableList<Profile> getGameDirectories() {
        if (gameDirectories == null) {
            throw new IllegalStateException("Game directories haven't been loaded");
        }
        return gameDirectories;
    }

    /// Returns the reusable game setting presets.
    public static ObservableList<GameSettings.Preset> getGameSettings() {
        return gameSettingsPresets().getPresets();
    }

    /// Returns the game setting preset with the given ID.
    public static GameSettings.@Nullable Preset getGameSettings(@Nullable SettingID id) {
        return gameSettingsPresets().getPreset(id);
    }

    /// Returns the default game setting preset, creating one when needed.
    public static GameSettings.Preset getDefaultGameSettingsPresetOrCreate() {
        GameSettings.Preset setting = getGameSettings(settings().defaultGameSettingsPresetProperty().get());
        if (setting != null) {
            return setting;
        }

        if (!getGameSettings().isEmpty()) {
            setting = getGameSettings().get(0);
            settings().defaultGameSettingsPresetProperty().set(setting.idProperty().getValue());
            return setting;
        }

        setting = new GameSettings.Preset(gameSettingsPresets().newPresetId());
        setting.autoNameNumberProperty().setValue(1);
        getGameSettings().add(setting);
        settings().defaultGameSettingsPresetProperty().set(setting.idProperty().getValue());
        return setting;
    }

    /// Returns whether this run created a new per-workspace config.
    public static boolean isNewlyCreated() {
        return newlyCreated;
    }

    /// Returns whether root is reading a config owned by another user.
    public static boolean isOwnerChanged() {
        return ownerChanged;
    }

    /// Returns whether launcher settings or state could not be safely overwritten.
    public static boolean isUnsupportedVersion() {
        return unsupportedVersion;
    }

    /// Loads configs, installs save listeners, and applies process-wide settings.
    public static void init() throws IOException {
        if (launcherSettings != null) {
            throw new IllegalStateException("Configuration is already loaded");
        }

        checkLocalConfigOwner();
        newlyCreated = isNewWorkspace();

        LoadedLauncherSettings launcherSettingsResult = loadLauncherSettings();
        launcherSettings = launcherSettingsResult.settings();
        unsupportedVersion = !launcherSettings.isSavable();
        @Nullable LegacyConfigMigrator.LegacyConfigMigration pendingMigration = launcherSettingsResult.pendingMigration();
        LegacyConfigMigrator.DetachedSettings detachedSettings = pendingMigration != null
                ? pendingMigration.detachedSettings()
                : LegacyConfigMigrator.DetachedSettings.empty();

        @Nullable LegacyConfigMigrator.UserSettingsMigrationResult userSettingsMigrationResult =
                Files.exists(USER_SETTINGS_LOCATION) && Files.exists(USER_STATE_LOCATION)
                        ? null
                        : LegacyConfigMigrator.migrateLegacyUserSettings();
        loadUserSettings(userSettingsMigrationResult);
        loadUserState(userSettingsMigrationResult);
        if (userSettingsMigrationResult != null) {
            LegacyConfigMigrator.completeLegacyUserSettingsMigration(userSettingsMigrationResult);
        }

        Locale.setDefault(settings().languageProperty().get().getLocale());
        I18n.setLocale(launcherSettings.languageProperty().get());
        LOG.setLogRetention(userSettings().logRetentionProperty().get());
        loadGameDirectories(detachedSettings.gameDirectories());
        loadGameSettingsPresets(detachedSettings.gameSettingsPresets());
        unsupportedVersion |= loadLauncherState(detachedSettings.launcherState());
        loadAuthlibInjectorServers(detachedSettings.authlibInjectorServers());
        loadUserGameAccounts();
        loadGameAccounts(detachedSettings.accountStorages());

        if (Files.exists(Metadata.HMCL_LOCAL_HOME)) {
            checkWritable(Metadata.HMCL_LOCAL_HOME);
        }

        if (pendingMigration != null) {
            LOG.info("Migrating settings from " + pendingMigration.path() + " to " + SETTINGS_LOCATION);
            FileUtils.saveSafely(SETTINGS_LOCATION, pendingMigration.launcherSettings().toJson());
            LegacyConfigMigrator.completeLegacyConfigMigration(pendingMigration);
        }

        if (launcherSettings.isSavable()) {
            launcherSettings.addListener(source -> {
                if (launcherSettings.isBackupOnNextSave()) {
                    launcherSettings.setBackupOnNextSave(false);
                    SettingFileUtils.backupInvalidConfig(SETTINGS_LOCATION);
                }
                FileSaver.save(SETTINGS_LOCATION, launcherSettings.toJson());
            });
        }
    }

    /// Loads the current per-workspace settings or migrates a legacy config when needed.
    private static LoadedLauncherSettings loadLauncherSettings() throws IOException {
        if (Files.exists(SETTINGS_LOCATION)) {
            JsonObject jsonObject;
            try {
                jsonObject = JsonUtils.fromJsonFile(SETTINGS_LOCATION, JsonObject.class);
            } catch (Exception e) {
                LOG.warning("Failed to read settings file: " + SETTINGS_LOCATION, e);

                LauncherSettings settings = new LauncherSettings();
                settings.setBackupOnNextSave(true);
                return new LoadedLauncherSettings(settings, null);
            }

            if (jsonObject == null) {
                LOG.warning("Settings file is empty: " + SETTINGS_LOCATION);

                return new LoadedLauncherSettings(new LauncherSettings(), null);
            }

            JsonSchema.CompatibilityResult schemaResult =
                    JsonSchema.check(jsonObject, LauncherSettings.CURRENT_SCHEMA);
            switch (schemaResult.status()) {
                case MISSING -> LOG.warning("Missing schema in settings file: " + SETTINGS_LOCATION);
                case INVALID -> LOG.warning("Invalid schema in settings file: "
                        + SETTINGS_LOCATION + ", Actual: " + schemaResult.invalidValue());
                case UNPARSEABLE -> LOG.warning("Unparseable schema in settings file: "
                        + SETTINGS_LOCATION + ", Actual: " + schemaResult.actual());
                case UNEXPECTED_ID -> LOG.warning("Unexpected settings file schema. Expected: "
                        + LauncherSettings.CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case UNSUPPORTED_MAJOR, READ_ONLY_PRESERVE_SCHEMA -> LOG.warning("Unsupported settings file schema. Expected: "
                        + LauncherSettings.CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case READ_WRITE, READ_WRITE_PRESERVE_SCHEMA -> {
                }
            }
            if (!schemaResult.readable()) {
                LauncherSettings settings = new LauncherSettings();
                settings.setSavable(false);
                return new LoadedLauncherSettings(settings, null);
            }

            try {
                LauncherSettings settings = LauncherSettings.fromJson(jsonObject);
                if (settings == null) {
                    settings = new LauncherSettings();
                    settings.setSavable(false);
                    return new LoadedLauncherSettings(settings, null);
                }

                if (!schemaResult.preserveSchema() && !LauncherSettings.CURRENT_SCHEMA.equals(settings.schemaProperty().get())) {
                    settings.schemaProperty().set(LauncherSettings.CURRENT_SCHEMA);
                }

                settings.setSavable(schemaResult.allowSave());
                return new LoadedLauncherSettings(settings, null);
            } catch (JsonParseException e) {
                LOG.warning("Failed to parse settings file: " + SETTINGS_LOCATION, e);
                LauncherSettings settings = new LauncherSettings();
                settings.setBackupOnNextSave(true);
                return new LoadedLauncherSettings(settings, null);
            }
        } else {
            LegacyConfigMigrator.LegacyConfigMigration migration = LegacyConfigMigrator.migrateLegacyConfig();
            if (migration != null) {
                return new LoadedLauncherSettings(migration.launcherSettings(), migration);
            }
        }

        return new LoadedLauncherSettings(new LauncherSettings(), null);
    }

    /// Returns whether the current workspace already has any local configuration footprint.
    private static boolean isNewWorkspace() {
        return !(Files.exists(SETTINGS_LOCATION)
                || Files.exists(STATE_LOCATION)
                || Files.exists(AUTHLIB_INJECTOR_SERVERS_LOCATION)
                || Files.exists(LOCAL_GAME_DIRECTORIES_LOCATION)
                || Files.exists(GAME_SETTINGS_LOCATION)
                || Files.exists(GAME_ACCOUNTS_LOCATION)
                || LegacyConfigMigrator.hasLegacyConfig());
    }

    /// Loads game directories and installs the save listener.
    ///
    /// @param fallbackGameDirectories the fallback store used when the local game directory file does not exist
    private static void loadGameDirectories(
            @Nullable GameDirectories fallbackGameDirectories) throws IOException {
        if (gameDirectories != null) {
            throw new IllegalStateException("Game directories are already loaded");
        }

        boolean newlyCreatedLocal = !Files.exists(LOCAL_GAME_DIRECTORIES_LOCATION);
        boolean newlyCreatedUser = !Files.exists(USER_GAME_DIRECTORIES_LOCATION);
        JsonSettingFile.LoadResult<GameDirectories> userResult = USER_GAME_DIRECTORIES_FILE.load(null);
        JsonSettingFile.LoadResult<GameDirectories> localResult = LOCAL_GAME_DIRECTORIES_FILE.load(fallbackGameDirectories);

        userGameDirectories = userResult.value();
        userGameDirectories.setUserFile(true);
        localGameDirectories = localResult.value();
        localGameDirectories.setUserFile(false);
        gameDirectories = mergeGameDirectories(userGameDirectories, localGameDirectories);
        if (localGameDirectories.isSavable() || userGameDirectories.isSavable()) {
            gameDirectories.addListener((InvalidationListener) source -> saveGameDirectories());
        }

        if (newlyCreatedLocal && localGameDirectories.isSavable()) {
            saveLocalGameDirectories();
        }

        if (newlyCreatedUser && userGameDirectories.isSavable()) {
            saveUserGameDirectories();
        }
    }

    /// Merges user and per-workspace game directories into the runtime store.
    private static ObservableList<Profile> mergeGameDirectories(GameDirectories user, GameDirectories local) {
        ObservableList<Profile> merged = FXCollections.observableArrayList(profile -> new Observable[] { profile });
        for (Profile profile : user.getGameDirectories()) {
            addMergedGameDirectory(merged, profile);
        }
        for (Profile profile : local.getGameDirectories()) {
            addMergedGameDirectory(merged, profile);
        }
        return merged;
    }

    /// Adds one profile to the merged game directory store.
    private static void addMergedGameDirectory(ObservableList<Profile> merged, Profile profile) {
        Objects.requireNonNull(merged);
        Objects.requireNonNull(profile);

        SettingID id = profile.getId();
        merged.removeIf(existing -> existing.getId().equals(id));
        merged.add(profile);
    }

    /// Saves the merged game directory store into the writable backing files.
    private static void saveGameDirectories() {
        if (localGameDirectories.isSavable()) {
            saveLocalGameDirectories();
        }
        if (userGameDirectories.isSavable()) {
            saveUserGameDirectories();
        }
    }

    /// Saves per-workspace game directories.
    private static void saveLocalGameDirectories() {
        localGameDirectories = createScopedGameDirectories(false);
        LOCAL_GAME_DIRECTORIES_FILE.save(localGameDirectories);
    }

    /// Saves user game directories.
    private static void saveUserGameDirectories() {
        userGameDirectories = createScopedGameDirectories(true);
        USER_GAME_DIRECTORIES_FILE.save(userGameDirectories);
    }

    /// Creates a game directory file containing only profiles that belong to the target storage location.
    private static GameDirectories createScopedGameDirectories(boolean userGameDirectories) {
        GameDirectories result = new GameDirectories();
        result.setSavable(true);
        result.setUserFile(userGameDirectories);
        for (Profile profile : getGameDirectories()) {
            if (profile.shouldSaveToUserGameDirectory() == userGameDirectories) {
                result.getGameDirectories().add(profile);
            }
        }
        return result;
    }

    /// Loads game settings presets and installs the save listener.
    ///
    /// @param fallbackGameSettingsPresets the fallback store used when the preset file does not exist
    private static void loadGameSettingsPresets(
            @Nullable GameSettingsPresets fallbackGameSettingsPresets) throws IOException {
        if (gameSettingsPresets != null) {
            throw new IllegalStateException("Game settings presets are already loaded");
        }

        boolean newlyCreated = !Files.exists(GAME_SETTINGS_LOCATION);
        JsonSettingFile.LoadResult<GameSettingsPresets> result =
                GAME_SETTINGS_FILE.load(fallbackGameSettingsPresets);
        gameSettingsPresets = result.value();
        if (gameSettingsPresets.isSavable()) {
            GAME_SETTINGS_FILE.installAutoSave(gameSettingsPresets);
        }
        normalizeGameSettingsPresets();

        if (newlyCreated && gameSettingsPresets.isSavable()) {
            GAME_SETTINGS_FILE.save(gameSettingsPresets);
        }
    }

    /// Ensures there is a valid default game settings preset.
    private static void normalizeGameSettingsPresets() {
        if (getGameSettings().isEmpty()) {
            getDefaultGameSettingsPresetOrCreate();
        } else if (getGameSettings(settings().defaultGameSettingsPresetProperty().get()) == null) {
            settings().defaultGameSettingsPresetProperty().set(getGameSettings().get(0).idProperty().getValue());
        }
    }

    /// Loads launcher state and installs the save listener.
    ///
    /// @param fallbackLauncherState the fallback state used when the launcher state file does not exist
    /// @return whether the launcher state file could not be safely overwritten because of an unsupported schema
    private static boolean loadLauncherState(
            @Nullable LauncherState fallbackLauncherState) throws IOException {
        if (launcherState != null) {
            throw new IllegalStateException("Launcher state is already loaded");
        }

        boolean newlyCreated = !Files.exists(STATE_LOCATION);
        JsonSettingFile.LoadResult<LauncherState> result =
                STATE_FILE.load(fallbackLauncherState);
        launcherState = result.value();
        if (launcherState.isSavable()) {
            STATE_FILE.installAutoSave(launcherState);
        }

        if (newlyCreated && launcherState.isSavable()) {
            STATE_FILE.save(launcherState);
        }

        return result.unsupported();
    }

    /// Loads authlib-injector servers and installs the save listener.
    ///
    /// @param fallbackAuthlibInjectorServers the fallback list used when the server list file does not exist
    private static void loadAuthlibInjectorServers(
            @Nullable AuthlibInjectorServerList fallbackAuthlibInjectorServers) throws IOException {
        if (authlibInjectorServers != null) {
            throw new IllegalStateException("Authlib-injector servers are already loaded");
        }

        boolean newlyCreated = !Files.exists(AUTHLIB_INJECTOR_SERVERS_LOCATION);
        JsonSettingFile.LoadResult<AuthlibInjectorServerList> result =
                AUTHLIB_INJECTOR_SERVERS_FILE.load(fallbackAuthlibInjectorServers);
        authlibInjectorServers = result.value();
        if (authlibInjectorServers.isSavable()) {
            AUTHLIB_INJECTOR_SERVERS_FILE.installAutoSave(authlibInjectorServers);
        }

        if (newlyCreated && authlibInjectorServers.isSavable()) {
            AUTHLIB_INJECTOR_SERVERS_FILE.save(authlibInjectorServers);
        }
    }

    /// Loads shared account storages and installs the save listener.
    private static void loadUserGameAccounts() {
        if (userGameAccounts != null) {
            throw new IllegalStateException("User game accounts are already loaded");
        }

        boolean newlyCreated = !Files.exists(USER_GAME_ACCOUNTS_LOCATION);
        @Nullable LegacyConfigMigrator.UserAccountsMigrationResult migrationResult =
                newlyCreated ? LegacyConfigMigrator.migrateLegacyUserAccounts() : null;
        @Nullable AccountStorages migrated = migrationResult != null ? migrationResult.accountStorages() : null;
        try {
            JsonSettingFile.LoadResult<AccountStorages> result = USER_GAME_ACCOUNTS_FILE.load(migrated);
            userGameAccounts = result.value();
            if (userGameAccounts.isSavable()) {
                USER_GAME_ACCOUNTS_FILE.installAutoSave(userGameAccounts);
            }

            if (newlyCreated && userGameAccounts.isSavable()) {
                USER_GAME_ACCOUNTS_FILE.save(userGameAccounts);
                if (migrationResult != null) {
                    LegacyConfigMigrator.completeLegacyUserAccountsMigration(migrationResult);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to load user game accounts", e);
            userGameAccounts = migrated != null ? migrated : new AccountStorages();
        }
    }

    /// Loads account storages and installs the save listener.
    ///
    /// @param fallbackGameAccounts the fallback store used when the account storage file does not exist
    private static void loadGameAccounts(
            @Nullable AccountStorages fallbackGameAccounts) throws IOException {
        if (gameAccounts != null) {
            throw new IllegalStateException("Game accounts are already loaded");
        }

        boolean newlyCreated = !Files.exists(GAME_ACCOUNTS_LOCATION);
        JsonSettingFile.LoadResult<AccountStorages> result =
                GAME_ACCOUNTS_FILE.load(fallbackGameAccounts);
        gameAccounts = result.value();
        if (gameAccounts.isSavable()) {
            GAME_ACCOUNTS_FILE.installAutoSave(gameAccounts);
        }

        if (newlyCreated && gameAccounts.isSavable()) {
            GAME_ACCOUNTS_FILE.save(gameAccounts);
        }
    }

    /// Checks whether root is reading per-workspace config data owned by another user.
    private static void checkLocalConfigOwner() {
        checkOwner(Metadata.HMCL_LOCAL_HOME);
        checkOwner(SETTINGS_LOCATION);
        checkOwner(STATE_LOCATION);
        checkOwner(AUTHLIB_INJECTOR_SERVERS_LOCATION);
        checkOwner(LOCAL_GAME_DIRECTORIES_LOCATION);
        checkOwner(GAME_SETTINGS_LOCATION);
        checkOwner(GAME_ACCOUNTS_LOCATION);
    }

    /// Checks whether root is reading a config path owned by another user.
    private static void checkOwner(Path location) {
        if (!Files.exists(location)) {
            return;
        }

        try {
            if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS
                    && "root".equals(System.getProperty("user.name"))
                    && !"root".equals(Files.getOwner(location).getName())) {
                ownerChanged = true;
            }
        } catch (IOException e) {
            LOG.warning("Failed to get owner");
        }
    }

    /// Checks that the given config path is writable.
    private static void checkWritable(Path location) throws IOException {
        if (!Files.isWritable(location)) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    && location.getFileSystem() == FileSystems.getDefault()
                    && location.toFile().canWrite()) {
                LOG.warning("Launcher config path " + location + " is not writable, but it seems to be a Samba share or OpenJDK bug");
                // There are some serious problems with the implementation of Samba or OpenJDK
                throw new SambaException();
            } else {
                // the config cannot be saved
                // throw up the error now to prevent further data loss
                throw new IOException("Launcher config path " + location + " is not writable");
            }
        }
    }

    /// Loads user settings and installs the save listener.
    private static void loadUserSettings(
            @Nullable LegacyConfigMigrator.UserSettingsMigrationResult migrationResult) throws IOException {
        if (userSettingsInstance != null) {
            throw new IllegalStateException("User settings are already loaded");
        }

        boolean newlyCreated = !Files.exists(USER_SETTINGS_LOCATION);
        @Nullable UserSettings migratedUserSettings = newlyCreated && migrationResult != null
                ? migrationResult.userSettings()
                : null;
        JsonSettingFile.LoadResult<UserSettings> result = USER_SETTINGS_FILE.load(migratedUserSettings);
        userSettingsInstance = result.value();
        if (userSettingsInstance.isSavable()) {
            USER_SETTINGS_FILE.installAutoSave(userSettingsInstance);
        }

        if (newlyCreated && migratedUserSettings != null && userSettingsInstance.isSavable()) {
            USER_SETTINGS_FILE.save(userSettingsInstance);
        }
    }

    /// Loads user state and installs the save listener.
    private static void loadUserState(
            @Nullable LegacyConfigMigrator.UserSettingsMigrationResult migrationResult) throws IOException {
        if (userStateInstance != null) {
            throw new IllegalStateException("User state is already loaded");
        }

        boolean newlyCreated = !Files.exists(USER_STATE_LOCATION);
        @Nullable UserState migratedUserState = newlyCreated && migrationResult != null
                ? migrationResult.userState()
                : null;
        JsonSettingFile.LoadResult<UserState> result = USER_STATE_FILE.load(migratedUserState);
        userStateInstance = result.value();
        if (userStateInstance.isSavable()) {
            USER_STATE_FILE.installAutoSave(userStateInstance);
        }

        if (newlyCreated && userStateInstance.isSavable()) {
            USER_STATE_FILE.save(userStateInstance);
        }
    }

    /// Result of loading per-workspace launcher settings.
    ///
    /// @param settings the loaded launcher settings
    /// @param pendingMigration the pending legacy config migration, or `null` when no legacy config was migrated
    private record LoadedLauncherSettings(
            LauncherSettings settings,
            @Nullable LegacyConfigMigrator.LegacyConfigMigration pendingMigration) {
    }

}
