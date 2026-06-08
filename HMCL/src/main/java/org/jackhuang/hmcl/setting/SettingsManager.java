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
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.gson.JsonUtils.mapTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns the process-wide configuration and detached workspace settings instances.
@NotNullByDefault
public final class SettingsManager {

    /// Prevents instantiation.
    private SettingsManager() {
    }

    /// The user settings path shared by all workspaces.
    public static final Path USER_SETTINGS_LOCATION = Metadata.HMCL_USER_HOME.resolve("user-settings.json");

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

    /// The current shared game directories path.
    private static final Path GLOBAL_GAME_DIRECTORIES_LOCATION =
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

    /// The legacy shared account storage path.
    private static final Path LEGACY_USER_ACCOUNTS_LOCATION =
            Metadata.HMCL_USER_HOME.resolve("accounts.json");

    /// The per-workspace game directory file helper.
    private static final JsonSettingFile<GameDirectories> LOCAL_GAME_DIRECTORIES_FILE = new JsonSettingFile<>(
            LOCAL_GAME_DIRECTORIES_LOCATION,
            "game directories",
            GameDirectories.class,
            GameDirectories.CURRENT_SCHEMA,
            GameDirectories::new);

    /// The shared game directory file helper.
    private static final JsonSettingFile<GameDirectories> GLOBAL_GAME_DIRECTORIES_FILE = new JsonSettingFile<>(
            GLOBAL_GAME_DIRECTORIES_LOCATION,
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

    /// The loaded per-workspace config instance.
    private static @UnknownNullability LauncherSettings configInstance;

    /// The loaded user settings instance.
    private static @UnknownNullability UserSettings userSettingsInstance;

    /// The loaded detached game directory store.
    private static @UnknownNullability GameDirectories gameDirectories;

    /// Original storage location and path for loaded game directories.
    private static final Map<SettingId, GameDirectorySource> gameDirectorySources = new HashMap<>();

    /// Whether the per-workspace game directories file may be overwritten.
    private static boolean allowSaveLocalGameDirectories = false;

    /// Whether the shared game directories file may be overwritten.
    private static boolean allowSaveGlobalGameDirectories = false;

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

    /// Whether no current or legacy per-workspace config could be loaded.
    private static boolean newlyCreated;

    /// Whether root is reading a per-workspace config owned by another user.
    private static boolean ownerChanged = false;

    /// Whether a legacy config was newer than this build can safely overwrite.
    private static boolean unsupportedVersion = false;

    /// Whether the per-workspace config file on disk is invalid and must be backed up
    /// before being overwritten by the first successful save.
    private static boolean needBackupSettings = false;

    /// Whether the per-workspace config should be saved after extracting detached data.
    private static boolean needSaveSettings = false;

    /// Detached settings used as fallbacks when detached settings files do not exist yet.
    private static LegacyConfigMigrator.DetachedSettings detachedSettingsFallback =
            LegacyConfigMigrator.DetachedSettings.empty();

    /// Returns the loaded per-workspace launcher settings.
    public static LauncherSettings settings() {
        if (configInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return configInstance;
    }

    /// Returns the loaded user settings.
    public static UserSettings userSettings() {
        if (userSettingsInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return userSettingsInstance;
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

    /// Returns the current per-workspace config path.
    public static Path configLocation() {
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

    /// Returns the shared game directories path.
    public static Path globalGameDirectoriesLocation() {
        return GLOBAL_GAME_DIRECTORIES_LOCATION;
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

    /// Returns the loaded detached game directory store.
    public static GameDirectories gameDirectories() {
        if (gameDirectories == null) {
            throw new IllegalStateException("Game directories haven't been loaded");
        }
        return gameDirectories;
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
        return gameDirectories().getGameDirectories();
    }

    /// Returns the selected game directory ID property.
    public static ObjectProperty<@Nullable SettingId> selectedGameDirectoryProperty() {
        return settings().selectedGameDirectoryProperty();
    }

    /// Returns the selected game directory ID.
    public static @Nullable SettingId getSelectedGameDirectory() {
        return settings().selectedGameDirectoryProperty().get();
    }

    /// Sets the selected game directory ID.
    public static void setSelectedGameDirectory(@Nullable SettingId selectedGameDirectory) {
        settings().selectedGameDirectoryProperty().set(selectedGameDirectory);
    }

    /// Returns selected instance IDs keyed by game directory ID.
    public static ObservableMap<SettingId, String> getSelectedInstance() {
        return settings().getSelectedInstance();
    }

    /// Returns the selected instance ID for the given game directory ID.
    public static @Nullable String getSelectedInstance(@Nullable SettingId gameDirectoryId) {
        return settings().getSelectedInstance(gameDirectoryId);
    }

    /// Sets the selected instance ID for the given game directory ID.
    public static void setSelectedInstance(@Nullable SettingId gameDirectoryId, @Nullable String selectedInstance) {
        settings().setSelectedInstance(gameDirectoryId, selectedInstance);
    }

    /// Returns the reusable game setting presets.
    public static ObservableList<GameSettings.Preset> getGameSettings() {
        return gameSettingsPresets().getPresets();
    }

    /// Returns the default game setting preset ID property.
    public static ObjectProperty<@Nullable SettingId> defaultGameSettingsPresetProperty() {
        return settings().defaultGameSettingsPresetProperty();
    }

    /// Returns the default game setting preset ID.
    public static @Nullable SettingId getDefaultGameSettingsPreset() {
        return settings().defaultGameSettingsPresetProperty().get();
    }

    /// Sets the default game setting preset ID.
    public static void setDefaultGameSettingsPreset(@Nullable SettingId defaultGameSettingsPreset) {
        settings().defaultGameSettingsPresetProperty().set(defaultGameSettingsPreset);
    }

    /// Returns the game setting preset with the given ID.
    public static GameSettings.@Nullable Preset getGameSettings(@Nullable SettingId id) {
        return gameSettingsPresets().getPreset(id);
    }

    /// Returns the default game setting preset, creating one when needed.
    public static GameSettings.Preset getDefaultGameSettingsPresetOrCreate() {
        GameSettings.Preset setting = getGameSettings(getDefaultGameSettingsPreset());
        if (setting != null) {
            return setting;
        }

        if (!getGameSettings().isEmpty()) {
            setting = getGameSettings().get(0);
            setDefaultGameSettingsPreset(setting.idProperty().getValue());
            return setting;
        }

        setting = new GameSettings.Preset(gameSettingsPresets().newPresetId());
        setting.autoNameNumberProperty().setValue(1);
        getGameSettings().add(setting);
        setDefaultGameSettingsPreset(setting.idProperty().getValue());
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

    /// Returns whether the loaded legacy config should not be overwritten.
    public static boolean isUnsupportedVersion() {
        return unsupportedVersion;
    }

    /// Loads configs, installs save listeners, and applies process-wide settings.
    public static void init() throws IOException {
        if (configInstance != null) {
            throw new IllegalStateException("Configuration is already loaded");
        }

        LOG.info("Launcher settings location: " + SETTINGS_LOCATION);

        configInstance = loadConfig();
        if (!unsupportedVersion) {
            configInstance.addListener(source -> {
                // Back up the invalid on-disk file the first time we are about to overwrite it.
                if (needBackupSettings) {
                    needBackupSettings = false;
                    backupInvalidConfig(SETTINGS_LOCATION);
                }
                FileSaver.save(SETTINGS_LOCATION, configInstance.toJson());
            });
        }

        loadUserSettings();

        Locale.setDefault(settings().languageProperty().get().getLocale());
        I18n.setLocale(configInstance.languageProperty().get());
        LOG.setLogRetention(userSettings().logRetentionProperty().get());
        loadGameDirectories(detachedSettingsFallback.gameDirectories(), !unsupportedVersion);
        loadGameSettingsPresets(detachedSettingsFallback.gameSettingsPresets(), !unsupportedVersion);
        loadLauncherState(detachedSettingsFallback.launcherState(), !unsupportedVersion);
        loadAuthlibInjectorServers(detachedSettingsFallback.authlibInjectorServers(), !unsupportedVersion);
        loadUserGameAccounts(!unsupportedVersion);
        loadGameAccounts(detachedSettingsFallback.accountStorages(), !unsupportedVersion);

        if (!unsupportedVersion && (newlyCreated || needSaveSettings)) {
            LOG.info((newlyCreated ? "Creating" : "Updating") + " config file " + SETTINGS_LOCATION);
            FileUtils.saveSafely(SETTINGS_LOCATION, configInstance.toJson());
        }

        if (!unsupportedVersion || Files.exists(SETTINGS_LOCATION)) {
            checkWritable(SETTINGS_LOCATION);
        }
    }

    /// Loads the current per-workspace config or migrates a legacy config when needed.
    private static LauncherSettings loadConfig() throws IOException {
        if (Files.exists(SETTINGS_LOCATION)) {
            checkOwner(SETTINGS_LOCATION);

            JsonObject jsonObject;
            try {
                jsonObject = JsonUtils.fromJsonFile(SETTINGS_LOCATION, JsonObject.class);
            } catch (Exception e) {
                needBackupSettings = true;
                LOG.warning("Failed to read settings file: " + SETTINGS_LOCATION, e);
                return new LauncherSettings();
            }

            if (jsonObject == null) {
                LOG.warning("Settings file is empty: " + SETTINGS_LOCATION);
                return new LauncherSettings();
            }

            JsonSchemaPolicy.Result schema =
                    JsonSchemaPolicy.check(SETTINGS_LOCATION, "settings file", jsonObject, LauncherSettings.CURRENT_SCHEMA);
            if (!schema.allowSave()) {
                unsupportedVersion = true;
            }
            if (!schema.readable()) {
                return new LauncherSettings();
            }

            LegacyConfigMigrator.CurrentSettingsMigration migration =
                    LegacyConfigMigrator.migrateCurrentSettings(jsonObject);
            detachedSettingsFallback = migration.detachedSettings();
            if (migration.changed()) {
                needSaveSettings = true;
            }

            try {
                LauncherSettings settings = LauncherSettings.fromJson(jsonObject);
                if (settings == null) {
                    return new LauncherSettings();
                }

                if (!schema.preserveSchema() && !LauncherSettings.CURRENT_SCHEMA.equals(settings.schemaProperty().get())) {
                    settings.schemaProperty().set(LauncherSettings.CURRENT_SCHEMA);
                }

                return settings;
            } catch (JsonParseException e) {
                needBackupSettings = true;
                LOG.warning("Failed to parse settings file: " + SETTINGS_LOCATION, e);
                return new LauncherSettings();
            }
        } else {
            LegacyConfigMigrator.MigrationResult migrationResult;
            try {
                migrationResult = LegacyConfigMigrator.migrateLegacyConfig();
            } catch (LegacyConfigMigrator.UnsupportedLegacyConfigVersionException e) {
                unsupportedVersion = true;
                LOG.warning("Legacy config file is newer than this launcher supports.", e);
                return new LauncherSettings();
            }
            if (migrationResult != null) {
                LOG.info("Migrating settings from " + migrationResult.path() + " to " + SETTINGS_LOCATION);
                detachedSettingsFallback = migrationResult.detachedSettings();
                FileUtils.saveSafely(SETTINGS_LOCATION, migrationResult.contentForMigration());
                return migrationResult.launcherSettings();
            }
        }

        var newSettings = new LauncherSettings();
        newlyCreated = true;
        return newSettings;
    }

    /// Loads game directories and installs the save listener.
    ///
    /// @param fallbackGameDirectories the fallback store used when the local game directory file does not exist
    /// @param allowSave               whether the detached game directory file may be overwritten
    private static void loadGameDirectories(
            @Nullable GameDirectories fallbackGameDirectories,
            boolean allowSave) throws IOException {
        if (gameDirectories != null) {
            throw new IllegalStateException("Game directories are already loaded");
        }

        LOG.info("Game directories location: " + LOCAL_GAME_DIRECTORIES_LOCATION);
        LOG.info("User game directories location: " + GLOBAL_GAME_DIRECTORIES_LOCATION);

        boolean newlyCreatedLocal = !Files.exists(LOCAL_GAME_DIRECTORIES_LOCATION);
        boolean newlyCreatedGlobal = !Files.exists(GLOBAL_GAME_DIRECTORIES_LOCATION);
        JsonSettingFile.LoadResult<GameDirectories> globalResult = GLOBAL_GAME_DIRECTORIES_FILE.load(null);
        JsonSettingFile.LoadResult<GameDirectories> localResult = LOCAL_GAME_DIRECTORIES_FILE.load(fallbackGameDirectories);

        gameDirectories = mergeGameDirectories(globalResult.value(), localResult.value());
        allowSaveLocalGameDirectories = allowSave && localResult.allowSave();
        allowSaveGlobalGameDirectories = allowSave && globalResult.allowSave();
        if (allowSaveLocalGameDirectories || allowSaveGlobalGameDirectories) {
            gameDirectories.addListener(source -> saveGameDirectories());
        }

        if (newlyCreatedLocal && allowSaveLocalGameDirectories) {
            LOG.info("Creating game directories file " + LOCAL_GAME_DIRECTORIES_LOCATION);
            saveLocalGameDirectories();
        }

        if (newlyCreatedGlobal && allowSaveGlobalGameDirectories) {
            LOG.info("Creating user game directories file " + GLOBAL_GAME_DIRECTORIES_LOCATION);
            saveGlobalGameDirectories();
        }
    }

    /// Merges shared and per-workspace game directories into the runtime store.
    private static GameDirectories mergeGameDirectories(GameDirectories global, GameDirectories local) {
        GameDirectories merged = new GameDirectories();
        gameDirectorySources.clear();
        for (Profile profile : global.getGameDirectories()) {
            addMergedGameDirectory(merged, profile, GameDirectoryScope.GLOBAL);
        }
        for (Profile profile : local.getGameDirectories()) {
            addMergedGameDirectory(merged, profile, GameDirectoryScope.LOCAL);
        }
        return merged;
    }

    /// Adds one profile to the merged game directory store.
    private static void addMergedGameDirectory(GameDirectories merged, Profile profile, GameDirectoryScope source) {
        Objects.requireNonNull(merged);
        Objects.requireNonNull(profile);
        Objects.requireNonNull(source);

        SettingId id = profile.getId();
        merged.getGameDirectories().removeIf(existing -> existing.getId().equals(id));
        merged.getGameDirectories().add(profile);
        PortablePath path = profile.getPath();
        gameDirectorySources.put(id, new GameDirectorySource(source, path.getPath()));
    }

    /// Saves the merged game directory store into the writable backing files.
    private static void saveGameDirectories() {
        if (allowSaveLocalGameDirectories) {
            saveLocalGameDirectories();
        }
        if (allowSaveGlobalGameDirectories) {
            saveGlobalGameDirectories();
        }
        updateGameDirectorySources();
    }

    /// Saves per-workspace game directories.
    private static void saveLocalGameDirectories() {
        LOCAL_GAME_DIRECTORIES_FILE.save(createScopedGameDirectories(GameDirectoryScope.LOCAL));
    }

    /// Saves shared game directories.
    private static void saveGlobalGameDirectories() {
        GLOBAL_GAME_DIRECTORIES_FILE.save(createScopedGameDirectories(GameDirectoryScope.GLOBAL));
    }

    /// Creates a game directory store containing only profiles that belong to the given scope.
    private static GameDirectories createScopedGameDirectories(GameDirectoryScope scope) {
        GameDirectories result = new GameDirectories();
        for (Profile profile : gameDirectories().getGameDirectories()) {
            if (getGameDirectoryScope(profile) == scope) {
                result.getGameDirectories().add(profile);
            }
        }
        return result;
    }

    /// Updates source tracking after saving the current merged directory store.
    private static void updateGameDirectorySources() {
        Map<SettingId, GameDirectorySource> updated = new HashMap<>();
        for (Profile profile : gameDirectories().getGameDirectories()) {
            PortablePath path = profile.getPath();
            updated.put(profile.getId(), new GameDirectorySource(getGameDirectoryScope(profile), path.getPath()));
        }
        gameDirectorySources.clear();
        gameDirectorySources.putAll(updated);
    }

    /// Returns the target storage scope for a profile.
    private static GameDirectoryScope getGameDirectoryScope(Profile profile) {
        PortablePath path = profile.getPath();
        @Nullable GameDirectorySource source = gameDirectorySources.get(profile.getId());
        if (source != null && Objects.equals(source.path(), path.getPath())) {
            return source.scope();
        }

        return path.isAbsolute() ? GameDirectoryScope.GLOBAL : GameDirectoryScope.LOCAL;
    }

    /// Loads game settings presets and installs the save listener.
    ///
    /// @param fallbackGameSettingsPresets the fallback store used when the preset file does not exist
    /// @param allowSave                   whether the detached preset file may be overwritten
    private static void loadGameSettingsPresets(
            @Nullable GameSettingsPresets fallbackGameSettingsPresets,
            boolean allowSave) throws IOException {
        if (gameSettingsPresets != null) {
            throw new IllegalStateException("Game settings presets are already loaded");
        }

        LOG.info("Game settings location: " + GAME_SETTINGS_LOCATION);

        boolean newlyCreated = !Files.exists(GAME_SETTINGS_LOCATION);
        JsonSettingFile.LoadResult<GameSettingsPresets> result =
                GAME_SETTINGS_FILE.load(fallbackGameSettingsPresets);
        gameSettingsPresets = result.value();
        if (allowSave && result.allowSave()) {
            GAME_SETTINGS_FILE.installAutoSave(gameSettingsPresets);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating game settings file " + GAME_SETTINGS_LOCATION);
            GAME_SETTINGS_FILE.save(gameSettingsPresets);
        }
    }

    /// Loads launcher state and installs the save listener.
    ///
    /// @param fallbackLauncherState the fallback state used when the launcher state file does not exist
    /// @param allowSave             whether the detached launcher state file may be overwritten
    private static void loadLauncherState(
            @Nullable LauncherState fallbackLauncherState,
            boolean allowSave) throws IOException {
        if (launcherState != null) {
            throw new IllegalStateException("Launcher state is already loaded");
        }

        LOG.info("Launcher state location: " + STATE_LOCATION);

        boolean newlyCreated = !Files.exists(STATE_LOCATION);
        JsonSettingFile.LoadResult<LauncherState> result =
                STATE_FILE.load(fallbackLauncherState);
        launcherState = result.value();
        if (allowSave && result.allowSave()) {
            STATE_FILE.installAutoSave(launcherState);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating launcher state file " + STATE_LOCATION);
            STATE_FILE.save(launcherState);
        }
    }

    /// Loads authlib-injector servers and installs the save listener.
    ///
    /// @param fallbackAuthlibInjectorServers the fallback list used when the server list file does not exist
    /// @param allowSave                      whether the detached server list file may be overwritten
    private static void loadAuthlibInjectorServers(
            @Nullable AuthlibInjectorServerList fallbackAuthlibInjectorServers,
            boolean allowSave) throws IOException {
        if (authlibInjectorServers != null) {
            throw new IllegalStateException("Authlib-injector servers are already loaded");
        }

        LOG.info("Authlib-injector servers location: " + AUTHLIB_INJECTOR_SERVERS_LOCATION);

        boolean newlyCreated = !Files.exists(AUTHLIB_INJECTOR_SERVERS_LOCATION);
        JsonSettingFile.LoadResult<AuthlibInjectorServerList> result =
                AUTHLIB_INJECTOR_SERVERS_FILE.load(fallbackAuthlibInjectorServers);
        authlibInjectorServers = result.value();
        if (allowSave && result.allowSave()) {
            AUTHLIB_INJECTOR_SERVERS_FILE.installAutoSave(authlibInjectorServers);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating authlib-injector servers file " + AUTHLIB_INJECTOR_SERVERS_LOCATION);
            AUTHLIB_INJECTOR_SERVERS_FILE.save(authlibInjectorServers);
        }
    }

    /// Loads shared account storages and installs the save listener.
    ///
    /// @param allowSave whether the shared account storage file may be overwritten
    private static void loadUserGameAccounts(boolean allowSave) {
        if (userGameAccounts != null) {
            throw new IllegalStateException("User game accounts are already loaded");
        }

        LOG.info("User game accounts location: " + USER_GAME_ACCOUNTS_LOCATION);

        boolean newlyCreated = !Files.exists(USER_GAME_ACCOUNTS_LOCATION);
        @Nullable AccountStorages migrated = newlyCreated ? loadLegacyUserGameAccounts() : null;
        try {
            JsonSettingFile.LoadResult<AccountStorages> result = USER_GAME_ACCOUNTS_FILE.load(migrated);
            userGameAccounts = result.value();
            if (allowSave && result.allowSave()) {
                USER_GAME_ACCOUNTS_FILE.installAutoSave(userGameAccounts);
            }

            if (newlyCreated && allowSave && result.allowSave()) {
                LOG.info("Creating user game accounts file " + USER_GAME_ACCOUNTS_LOCATION);
                USER_GAME_ACCOUNTS_FILE.save(userGameAccounts);
            }
        } catch (IOException e) {
            LOG.warning("Failed to load user game accounts", e);
            userGameAccounts = migrated != null ? migrated : new AccountStorages();
            if (allowSave) {
                USER_GAME_ACCOUNTS_FILE.installAutoSave(userGameAccounts);
            }
        }
    }

    /// Loads the legacy shared account storage file for first-time detached account migration.
    private static @Nullable AccountStorages loadLegacyUserGameAccounts() {
        if (!Files.exists(LEGACY_USER_ACCOUNTS_LOCATION)) {
            return null;
        }

        try {
            List<Map<Object, Object>> accounts = JsonUtils.fromJsonFile(
                    LauncherSettings.SETTINGS_GSON,
                    LEGACY_USER_ACCOUNTS_LOCATION,
                    listTypeOf(mapTypeOf(Object.class, Object.class))
            );
            if (accounts == null) {
                return null;
            }

            LOG.info("Migrating user accounts from " + LEGACY_USER_ACCOUNTS_LOCATION
                    + " to " + USER_GAME_ACCOUNTS_LOCATION);
            return AccountStorages.fromAccounts(accounts);
        } catch (Throwable e) {
            LOG.warning("Failed to load legacy user accounts", e);
            return null;
        }
    }

    /// Loads account storages and installs the save listener.
    ///
    /// @param fallbackGameAccounts the fallback store used when the account storage file does not exist
    /// @param allowSave            whether the detached account storage file may be overwritten
    private static void loadGameAccounts(
            @Nullable AccountStorages fallbackGameAccounts,
            boolean allowSave) throws IOException {
        if (gameAccounts != null) {
            throw new IllegalStateException("Game accounts are already loaded");
        }

        LOG.info("Game accounts location: " + GAME_ACCOUNTS_LOCATION);

        boolean newlyCreated = !Files.exists(GAME_ACCOUNTS_LOCATION);
        JsonSettingFile.LoadResult<AccountStorages> result =
                GAME_ACCOUNTS_FILE.load(fallbackGameAccounts);
        gameAccounts = result.value();
        if (allowSave && result.allowSave()) {
            GAME_ACCOUNTS_FILE.installAutoSave(gameAccounts);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating game accounts file " + GAME_ACCOUNTS_LOCATION);
            GAME_ACCOUNTS_FILE.save(gameAccounts);
        }
    }

    /// Moves an invalid config file to a numbered backup path (e.g. {@code settings.json.1},
    /// {@code settings.json.2}, …) so the original data is preserved for diagnosis.
    /// This is called synchronously from the save listener, immediately before the first
    /// successful write overwrites the invalid file.
    /// Does nothing and logs a warning when the move fails.
    ///
    /// @param location the invalid config file to back up
    private static void backupInvalidConfig(Path location) {
        try {
            // Find the first unused backup index: settings.json.1, settings.json.2, …
            Path backup = null;
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                Path candidate = location.resolveSibling(location.getFileName() + "." + i);
                if (!Files.exists(candidate)) {
                    backup = candidate;
                    break;
                }
            }
            if (backup == null) {
                LOG.warning("Could not find an available backup path for " + location);
                return;
            }
            LOG.info("Backed up invalid config to " + backup);
            Files.move(location, backup);
        } catch (IOException e) {
            LOG.warning("Failed to back up invalid config " + location, e);
        }
    }

    /// Checks whether root is reading a config file owned by another user.
    private static void checkOwner(Path location) {
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

    /// Checks that the given config file is writable.
    private static void checkWritable(Path location) throws IOException {
        if (!Files.isWritable(location)) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    && location.getFileSystem() == FileSystems.getDefault()
                    && location.toFile().canWrite()) {
                LOG.warning("Launcher settings at " + location + " is not writable, but it seems to be a Samba share or OpenJDK bug");
                // There are some serious problems with the implementation of Samba or OpenJDK
                throw new SambaException();
            } else {
                // the config cannot be saved
                // throw up the error now to prevent further data loss
                throw new IOException("Launcher settings at " + location + " is not writable");
            }
        }
    }

    /// Loads user settings and installs the save listener.
    private static void loadUserSettings() throws IOException {
        if (userSettingsInstance != null) {
            throw new IllegalStateException("User settings are already loaded");
        }

        LOG.info("User settings location: " + USER_SETTINGS_LOCATION);

        boolean newlyCreated = !Files.exists(USER_SETTINGS_LOCATION);
        @Nullable UserSettings migratedUserSettings = newlyCreated
                ? LegacyConfigMigrator.migrateLegacyUserSettings(USER_SETTINGS_LOCATION)
                : null;
        JsonSettingFile.LoadResult<UserSettings> result = USER_SETTINGS_FILE.load(migratedUserSettings);
        userSettingsInstance = result.value();
        if (result.allowSave()) {
            USER_SETTINGS_FILE.installAutoSave(userSettingsInstance);
        }

        if (newlyCreated && result.allowSave()) {
            LOG.info("Creating user settings file " + USER_SETTINGS_LOCATION);
            USER_SETTINGS_FILE.save(userSettingsInstance);
        }
    }

    /// Storage scope for a game directory entry.
    private enum GameDirectoryScope {
        /// Stored in `HMCL_LOCAL_HOME/game-directories.json`.
        LOCAL,

        /// Stored in `HMCL_USER_HOME/user-game-directories.json`.
        GLOBAL
    }

    /// Original storage metadata for a game directory entry.
    ///
    /// @param scope the file where this entry belongs while its path remains unchanged
    /// @param path  the original serialized path string
    private record GameDirectorySource(GameDirectoryScope scope, String path) {
    }

}
