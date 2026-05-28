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

import com.github.f4b6a3.uuid.alt.GUID;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.FileSaver;
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

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns the process-wide configuration and detached workspace settings instances.
@NotNullByDefault
public final class ConfigHolder {

    /// Prevents instantiation.
    private ConfigHolder() {
    }

    /// The global launcher config path shared by all workspaces.
    public static final Path GLOBAL_CONFIG_PATH = Metadata.HMCL_GLOBAL_DIRECTORY.resolve("config.json");

    /// The current per-workspace config path.
    private static final Path SETTINGS_LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("settings.json");

    /// The current per-workspace launcher state path.
    private static final Path STATE_LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("state.json");

    /// The current per-workspace authlib-injector server list path.
    private static final Path AUTHLIB_INJECTOR_SERVERS_LOCATION =
            Metadata.HMCL_CURRENT_DIRECTORY.resolve("authlib-injector-servers.json");

    /// The current per-workspace game directories path.
    private static final Path GAME_DIRECTORIES_LOCATION =
            Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-directories.json");

    /// The current per-workspace game settings path.
    private static final Path GAME_SETTINGS_LOCATION =
            Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-settings.json");

    /// The current per-workspace account storage path.
    private static final Path GAME_ACCOUNTS_LOCATION =
            Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-accounts.json");

    /// The detached game directory file helper.
    private static final JsonSettingFile<GameDirectories> GAME_DIRECTORIES_FILE = new JsonSettingFile<>(
            GAME_DIRECTORIES_LOCATION,
            "game directories",
            GameDirectories.class,
            GameDirectories.CURRENT_FORMAT,
            GameDirectories::new);

    /// The detached game settings file helper.
    private static final JsonSettingFile<GameSettingsPresets> GAME_SETTINGS_FILE = new JsonSettingFile<>(
            GAME_SETTINGS_LOCATION,
            "game settings",
            GameSettingsPresets.class,
            GameSettingsPresets.CURRENT_FORMAT,
            GameSettingsPresets::new);

    /// The detached account storage file helper.
    private static final JsonSettingFile<AccountStorages> GAME_ACCOUNTS_FILE = new JsonSettingFile<>(
            GAME_ACCOUNTS_LOCATION,
            "game accounts",
            AccountStorages.class,
            AccountStorages.CURRENT_FORMAT,
            AccountStorages::new);

    /// The detached launcher state file helper.
    private static final JsonSettingFile<LauncherState> STATE_FILE = new JsonSettingFile<>(
            STATE_LOCATION,
            "launcher state",
            LauncherState.class,
            LauncherState.CURRENT_FORMAT,
            LauncherState::new);

    /// The detached authlib-injector server list file helper.
    private static final JsonSettingFile<AuthlibInjectorServerList> AUTHLIB_INJECTOR_SERVERS_FILE = new JsonSettingFile<>(
            AUTHLIB_INJECTOR_SERVERS_LOCATION,
            "authlib-injector servers",
            AuthlibInjectorServerList.class,
            AuthlibInjectorServerList.CURRENT_FORMAT,
            AuthlibInjectorServerList::createDefault);

    /// The loaded per-workspace config instance.
    private static @UnknownNullability Config configInstance;

    /// The loaded user-global config instance.
    private static @UnknownNullability GlobalConfig globalConfigInstance;

    /// The loaded detached game directory store.
    private static @UnknownNullability GameDirectories gameDirectories;

    /// The loaded detached preset store.
    private static @UnknownNullability GameSettingsPresets gameSettingsPresets;

    /// The loaded detached launcher state store.
    private static @UnknownNullability LauncherState launcherState;

    /// The loaded detached authlib-injector server list store.
    private static @UnknownNullability AuthlibInjectorServerList authlibInjectorServers;

    /// The loaded detached account storage store.
    private static @UnknownNullability AccountStorages gameAccounts;

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

    /// Detached game directories migrated from a config file.
    private static @Nullable GameDirectories migratedGameDirectories;

    /// Detached game settings presets migrated from a legacy config file.
    private static @Nullable GameSettingsPresets migratedGameSettingsPresets;

    /// Detached launcher state migrated from a legacy config file.
    private static @Nullable LauncherState migratedLauncherState;

    /// Detached authlib-injector servers migrated from a legacy config file.
    private static @Nullable AuthlibInjectorServerList migratedAuthlibInjectorServers;

    /// Detached account storages migrated from a config file.
    private static @Nullable AccountStorages migratedGameAccounts;

    /// Returns the loaded per-workspace config.
    public static Config config() {
        if (configInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return configInstance;
    }

    /// Returns the loaded user-global config.
    public static GlobalConfig globalConfig() {
        if (globalConfigInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return globalConfigInstance;
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
        return GAME_DIRECTORIES_LOCATION;
    }

    /// Returns the current per-workspace game settings path.
    public static Path gameSettingsLocation() {
        return GAME_SETTINGS_LOCATION;
    }

    /// Returns the current per-workspace account storage path.
    public static Path gameAccountsLocation() {
        return GAME_ACCOUNTS_LOCATION;
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

    /// Returns the per-workspace authlib-injector servers.
    public static ObservableList<AuthlibInjectorServer> getAuthlibInjectorServers() {
        return authlibInjectorServers().getServers();
    }

    /// Returns the per-workspace account storages.
    public static ObservableList<Map<Object, Object>> getAccountStorages() {
        return gameAccounts().getAccounts();
    }

    /// Serializes the per-workspace account storage file content.
    public static String gameAccountsToJson() {
        return JsonUtils.GSON.toJson(gameAccounts(), AccountStorages.class);
    }

    /// Returns the per-workspace game directories.
    public static ObservableList<Profile> getGameDirectories() {
        return gameDirectories().getGameDirectories();
    }

    /// Returns the selected game directory ID property.
    public static ObjectProperty<@Nullable GUID> selectedGameDirectoryProperty() {
        return config().selectedGameDirectoryProperty();
    }

    /// Returns the selected game directory ID.
    public static @Nullable GUID getSelectedGameDirectory() {
        return config().getSelectedGameDirectory();
    }

    /// Sets the selected game directory ID.
    public static void setSelectedGameDirectory(@Nullable GUID selectedGameDirectory) {
        config().setSelectedGameDirectory(selectedGameDirectory);
    }

    /// Returns selected instance IDs keyed by game directory ID.
    public static ObservableMap<GUID, String> getSelectedInstance() {
        return config().getSelectedInstance();
    }

    /// Returns the selected instance ID for the given game directory ID.
    public static @Nullable String getSelectedInstance(@Nullable GUID gameDirectoryId) {
        return config().getSelectedInstance(gameDirectoryId);
    }

    /// Sets the selected instance ID for the given game directory ID.
    public static void setSelectedInstance(@Nullable GUID gameDirectoryId, @Nullable String selectedInstance) {
        config().setSelectedInstance(gameDirectoryId, selectedInstance);
    }

    /// Returns the reusable game setting presets.
    public static ObservableList<GameSettings.Preset> getGameSettings() {
        return gameSettingsPresets().getPresets();
    }

    /// Returns the default game setting preset ID property.
    public static ObjectProperty<@Nullable GUID> defaultGameSettingsPresetProperty() {
        return config().defaultGameSettingsPresetProperty();
    }

    /// Returns the default game setting preset ID.
    public static @Nullable GUID getDefaultGameSettingsPreset() {
        return config().getDefaultGameSettingsPreset();
    }

    /// Sets the default game setting preset ID.
    public static void setDefaultGameSettingsPreset(@Nullable GUID defaultGameSettingsPreset) {
        config().setDefaultGameSettingsPreset(defaultGameSettingsPreset);
    }

    /// Returns the game setting preset with the given ID.
    public static GameSettings.@Nullable Preset getGameSettings(@Nullable GUID id) {
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
        setting.nameProperty().setValue(i18n("message.default"));
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

        LOG.info("Config location: " + SETTINGS_LOCATION);

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

        globalConfigInstance = loadGlobalConfig();
        globalConfigInstance.addListener(source -> FileSaver.save(GLOBAL_CONFIG_PATH, globalConfigInstance.toJson()));

        Locale.setDefault(config().getLanguage().getLocale());
        I18n.setLocale(configInstance.getLanguage());
        LOG.setLogRetention(globalConfig().getLogRetention());
        loadGameDirectories(migratedGameDirectories, !unsupportedVersion);
        loadGameSettingsPresets(migratedGameSettingsPresets, !unsupportedVersion);
        loadLauncherState(migratedLauncherState, !unsupportedVersion);
        loadAuthlibInjectorServers(migratedAuthlibInjectorServers, !unsupportedVersion);
        loadGameAccounts(migratedGameAccounts, !unsupportedVersion);
        Settings.init();

        if (!unsupportedVersion && (newlyCreated || needSaveSettings)) {
            LOG.info((newlyCreated ? "Creating" : "Updating") + " config file " + SETTINGS_LOCATION);
            FileUtils.saveSafely(SETTINGS_LOCATION, configInstance.toJson());
        }

        checkWritable(SETTINGS_LOCATION);
    }

    /// Loads the current per-workspace config or migrates a legacy config when needed.
    private static Config loadConfig() throws IOException {
        if (Files.exists(SETTINGS_LOCATION)) {
            checkOwner(SETTINGS_LOCATION);

            JsonObject jsonObject;
            try {
                jsonObject = JsonUtils.fromJsonFile(SETTINGS_LOCATION, JsonObject.class);
            } catch (Exception e) {
                needBackupSettings = true;
                LOG.warning("Failed to read settings file: " + SETTINGS_LOCATION, e);
                return new Config();
            }

            if (jsonObject == null) {
                LOG.warning("Settings file is empty: " + SETTINGS_LOCATION);
                return new Config();
            }

            JsonFileFormatPolicy.Result format =
                    JsonFileFormatPolicy.check(SETTINGS_LOCATION, "settings file", jsonObject, Config.CURRENT_FORMAT);
            if (!format.allowSave()) {
                unsupportedVersion = true;
            }
            if (!format.readable()) {
                return new Config();
            }

            migratedGameAccounts = LegacyConfigMigrator.extractAccountStorages(jsonObject);
            if (migratedGameAccounts != null) {
                needSaveSettings = true;
            }

            try {
                Config settings = Config.fromJson(jsonObject);
                if (settings == null) {
                    return new Config();
                }

                if (!Config.CURRENT_FORMAT.equals(settings.getFormat())) {
                    settings.setFormat(Config.CURRENT_FORMAT);
                }

                return settings;
            } catch (JsonParseException e) {
                needBackupSettings = true;
                LOG.warning("Failed to parse settings file: " + SETTINGS_LOCATION, e);
                return new Config();
            }
        } else {
            LegacyConfigMigrator.MigrationResult migrationResult = LegacyConfigMigrator.migrateLegacyConfig();
            if (migrationResult != null) {
                LOG.info("Migrating settings from " + migrationResult.path() + " to " + SETTINGS_LOCATION);
                migratedGameDirectories = migrationResult.gameDirectories();
                migratedGameSettingsPresets = migrationResult.gameSettingsPresets();
                migratedLauncherState = migrationResult.launcherState();
                migratedAuthlibInjectorServers = migrationResult.authlibInjectorServers();
                migratedGameAccounts = migrationResult.accountStorages();
                FileUtils.saveSafely(SETTINGS_LOCATION, migrationResult.contentForMigration());
                return migrationResult.config();
            }
        }

        var newSettings = new Config();
        newlyCreated = true;
        return newSettings;
    }

    /// Loads game directories and installs the save listener.
    ///
    /// @param migratedGameDirectories the game directory store migrated from a config file
    /// @param allowSave whether the detached game directory file may be overwritten
    private static void loadGameDirectories(
            @Nullable GameDirectories migratedGameDirectories,
            boolean allowSave) throws IOException {
        if (gameDirectories != null) {
            throw new IllegalStateException("Game directories are already loaded");
        }

        LOG.info("Game directories location: " + GAME_DIRECTORIES_LOCATION);

        boolean newlyCreated = !Files.exists(GAME_DIRECTORIES_LOCATION);
        JsonSettingFile.LoadResult<GameDirectories> result = GAME_DIRECTORIES_FILE.load(migratedGameDirectories);
        gameDirectories = result.value();
        if (allowSave && result.allowSave()) {
            GAME_DIRECTORIES_FILE.installAutoSave(gameDirectories);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating game directories file " + GAME_DIRECTORIES_LOCATION);
            GAME_DIRECTORIES_FILE.save(gameDirectories);
        }
    }

    /// Loads game settings presets and installs the save listener.
    ///
    /// @param migratedGameSettingsPresets the preset store migrated from a legacy config file
    /// @param allowSave whether the detached preset file may be overwritten
    private static void loadGameSettingsPresets(
            @Nullable GameSettingsPresets migratedGameSettingsPresets,
            boolean allowSave) throws IOException {
        if (gameSettingsPresets != null) {
            throw new IllegalStateException("Game settings presets are already loaded");
        }

        LOG.info("Game settings location: " + GAME_SETTINGS_LOCATION);

        boolean newlyCreated = !Files.exists(GAME_SETTINGS_LOCATION);
        JsonSettingFile.LoadResult<GameSettingsPresets> result =
                GAME_SETTINGS_FILE.load(migratedGameSettingsPresets);
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
    /// @param migratedLauncherState the launcher state migrated from a legacy config file
    /// @param allowSave whether the detached launcher state file may be overwritten
    private static void loadLauncherState(
            @Nullable LauncherState migratedLauncherState,
            boolean allowSave) throws IOException {
        if (launcherState != null) {
            throw new IllegalStateException("Launcher state is already loaded");
        }

        LOG.info("Launcher state location: " + STATE_LOCATION);

        boolean newlyCreated = !Files.exists(STATE_LOCATION);
        JsonSettingFile.LoadResult<LauncherState> result =
                STATE_FILE.load(migratedLauncherState);
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
    /// @param migratedAuthlibInjectorServers the server list migrated from a legacy config file
    /// @param allowSave whether the detached server list file may be overwritten
    private static void loadAuthlibInjectorServers(
            @Nullable AuthlibInjectorServerList migratedAuthlibInjectorServers,
            boolean allowSave) throws IOException {
        if (authlibInjectorServers != null) {
            throw new IllegalStateException("Authlib-injector servers are already loaded");
        }

        LOG.info("Authlib-injector servers location: " + AUTHLIB_INJECTOR_SERVERS_LOCATION);

        boolean newlyCreated = !Files.exists(AUTHLIB_INJECTOR_SERVERS_LOCATION);
        JsonSettingFile.LoadResult<AuthlibInjectorServerList> result =
                AUTHLIB_INJECTOR_SERVERS_FILE.load(migratedAuthlibInjectorServers);
        authlibInjectorServers = result.value();
        if (allowSave && result.allowSave()) {
            AUTHLIB_INJECTOR_SERVERS_FILE.installAutoSave(authlibInjectorServers);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating authlib-injector servers file " + AUTHLIB_INJECTOR_SERVERS_LOCATION);
            AUTHLIB_INJECTOR_SERVERS_FILE.save(authlibInjectorServers);
        }
    }

    /// Loads account storages and installs the save listener.
    ///
    /// @param migratedGameAccounts the account storage store migrated from a config file
    /// @param allowSave whether the detached account storage file may be overwritten
    private static void loadGameAccounts(
            @Nullable AccountStorages migratedGameAccounts,
            boolean allowSave) throws IOException {
        if (gameAccounts != null) {
            throw new IllegalStateException("Game accounts are already loaded");
        }

        LOG.info("Game accounts location: " + GAME_ACCOUNTS_LOCATION);

        boolean newlyCreated = !Files.exists(GAME_ACCOUNTS_LOCATION);
        JsonSettingFile.LoadResult<AccountStorages> result =
                GAME_ACCOUNTS_FILE.load(migratedGameAccounts);
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
                LOG.warning("Config at " + location + " is not writable, but it seems to be a Samba share or OpenJDK bug");
                // There are some serious problems with the implementation of Samba or OpenJDK
                throw new SambaException();
            } else {
                // the config cannot be saved
                // throw up the error now to prevent further data loss
                throw new IOException("Config at " + location + " is not writable");
            }
        }
    }

    /// Loads the user-global config, creating an empty one when none can be read.
    private static GlobalConfig loadGlobalConfig() throws IOException {
        if (Files.exists(GLOBAL_CONFIG_PATH)) {
            try {
                String content = Files.readString(GLOBAL_CONFIG_PATH);
                GlobalConfig deserialized = GlobalConfig.fromJson(content);
                if (deserialized == null) {
                    LOG.info("Config is empty");
                } else {
                    return deserialized;
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed config.", e);
            }
        }

        LOG.info("Creating an empty global config");
        return new GlobalConfig();
    }

}
