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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
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

    /// The local directory storing per-workspace configuration files.
    private static final Path LOCAL_CONFIG_FILES_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("config");

    /// The local directory storing per-workspace state files.
    private static final Path LOCAL_STATE_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("state");

    /// The local directory storing per-workspace cache files.
    private static final Path LOCAL_CACHE_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("cache");

    /// The local directory storing per-workspace private data files.
    private static final Path LOCAL_PRIVATE_DIRECTORY = Metadata.HMCL_LOCAL_HOME.resolve("private");

    /// The user directory storing shared configuration files.
    private static final Path USER_CONFIG_FILES_DIRECTORY = Metadata.HMCL_USER_HOME.resolve("config");

    /// The user directory storing shared state files.
    private static final Path USER_STATE_DIRECTORY = Metadata.HMCL_USER_HOME.resolve("state");

    /// The user directory storing shared private data files.
    private static final Path USER_PRIVATE_DIRECTORY = Metadata.HMCL_USER_HOME.resolve("private");

    /// The legacy settings path used as a migration input.
    private static final Path LEGACY_SETTINGS_LOCATION = Metadata.HMCL_LOCAL_HOME.resolve("settings.json");

    /// The user settings path shared by all workspaces.
    public static final Path USER_SETTINGS_LOCATION =
            USER_CONFIG_FILES_DIRECTORY.resolve("user-settings.json");

    /// The user state path shared by all workspaces.
    public static final Path USER_STATE_LOCATION =
            USER_STATE_DIRECTORY.resolve("user-state.json");

    /// The current per-workspace launcher settings path.
    private static final Path SETTINGS_LOCATION =
            LOCAL_CONFIG_FILES_DIRECTORY.resolve("launcher-settings.json");

    /// The current per-workspace launcher state path.
    private static final Path STATE_LOCATION =
            LOCAL_STATE_DIRECTORY.resolve("launcher-state.json");

    /// The current per-workspace authlib-injector server list path.
    private static final Path AUTHLIB_INJECTOR_SERVERS_LOCATION =
            LOCAL_CONFIG_FILES_DIRECTORY.resolve("authlib-injector-servers.json");

    /// The current per-workspace authlib-injector server metadata cache path.
    private static final Path AUTHLIB_INJECTOR_SERVER_METADATA_CACHE_LOCATION =
            LOCAL_CACHE_DIRECTORY.resolve("authlib-injector-server-metadata.json");

    /// The current per-workspace game directories path.
    private static final Path LOCAL_GAME_DIRECTORIES_LOCATION =
            LOCAL_CONFIG_FILES_DIRECTORY.resolve("game-directories.json");

    /// The current user game directories path.
    private static final Path USER_GAME_DIRECTORIES_LOCATION =
            USER_CONFIG_FILES_DIRECTORY.resolve("user-game-directories.json");

    /// The current per-workspace game settings path.
    private static final Path GAME_SETTINGS_LOCATION =
            LOCAL_CONFIG_FILES_DIRECTORY.resolve("game-settings.json");

    /// The current per-workspace account metadata path.
    private static final Path GAME_ACCOUNTS_LOCATION =
            LOCAL_CONFIG_FILES_DIRECTORY.resolve("accounts.json");

    /// The shared account metadata path.
    private static final Path USER_GAME_ACCOUNTS_LOCATION =
            USER_CONFIG_FILES_DIRECTORY.resolve("user-accounts.json");

    /// The current per-workspace account private data path.
    private static final Path GAME_ACCOUNT_PRIVATE_DATA_LOCATION =
            LOCAL_PRIVATE_DIRECTORY.resolve("account-private-data.json");

    /// The shared account private data path.
    private static final Path USER_GAME_ACCOUNT_PRIVATE_DATA_LOCATION =
            USER_PRIVATE_DIRECTORY.resolve("user-account-private-data.json");

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

    /// The detached account metadata file helper.
    private static final JsonSettingFile<AccountMetadataStore> GAME_ACCOUNTS_FILE = new JsonSettingFile<>(
            GAME_ACCOUNTS_LOCATION,
            "accounts",
            AccountMetadataStore.class,
            AccountMetadataStore.CURRENT_SCHEMA,
            AccountMetadataStore::new);

    /// The shared account metadata file helper.
    private static final JsonSettingFile<AccountMetadataStore> USER_GAME_ACCOUNTS_FILE = new JsonSettingFile<>(
            USER_GAME_ACCOUNTS_LOCATION,
            "user accounts",
            AccountMetadataStore.class,
            AccountMetadataStore.CURRENT_SCHEMA,
            AccountMetadataStore::new);

    /// The detached account private data file helper.
    private static final JsonSettingFile<AccountPrivateData> GAME_ACCOUNT_PRIVATE_DATA_FILE = new JsonSettingFile<>(
            GAME_ACCOUNT_PRIVATE_DATA_LOCATION,
            "game account private data",
            AccountPrivateData.class,
            AccountPrivateData.CURRENT_SCHEMA,
            AccountPrivateData::new);

    /// The shared account private data file helper.
    private static final JsonSettingFile<AccountPrivateData> USER_GAME_ACCOUNT_PRIVATE_DATA_FILE = new JsonSettingFile<>(
            USER_GAME_ACCOUNT_PRIVATE_DATA_LOCATION,
            "user game account private data",
            AccountPrivateData.class,
            AccountPrivateData.CURRENT_SCHEMA,
            AccountPrivateData::new);

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

    /// The authlib-injector server metadata cache file helper.
    private static final JsonSettingFile<AuthlibInjectorServerMetadataCache> AUTHLIB_INJECTOR_SERVER_METADATA_CACHE_FILE =
            new JsonSettingFile<>(
                    AUTHLIB_INJECTOR_SERVER_METADATA_CACHE_LOCATION,
                    "authlib-injector server metadata cache",
                    AuthlibInjectorServerMetadataCache.class,
                    AuthlibInjectorServerMetadataCache.CURRENT_SCHEMA,
                    AuthlibInjectorServerMetadataCache::new);

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

    /// The loaded detached preset store.
    private static @UnknownNullability GameSettingsPresets gameSettingsPresets;

    /// The loaded detached launcher state store.
    private static @UnknownNullability LauncherState launcherState;

    /// The loaded detached authlib-injector server list store.
    private static @UnknownNullability AuthlibInjectorServerList authlibInjectorServers;

    /// The loaded authlib-injector server metadata cache.
    private static @UnknownNullability AuthlibInjectorServerMetadataCache authlibInjectorServerMetadataCache;

    /// Metadata cache listeners installed on loaded authlib-injector servers.
    private static final Map<AuthlibInjectorServer, InvalidationListener> authlibInjectorServerMetadataListeners =
            new IdentityHashMap<>();

    /// The loaded detached account metadata store.
    private static @UnknownNullability AccountMetadataStore gameAccounts;

    /// The loaded shared account metadata store.
    private static @UnknownNullability AccountMetadataStore userGameAccounts;

    /// The loaded detached account private data store.
    private static @UnknownNullability AccountPrivateData gameAccountPrivateData;

    /// The loaded shared account private data store.
    private static @UnknownNullability AccountPrivateData userGameAccountPrivateData;

    /// Whether this run appears to be using a new workspace.
    private static boolean newlyCreated;

    /// Whether root is reading a per-workspace config owned by another user.
    private static boolean ownerChanged = false;

    /// Access status for `config/launcher-settings.json`.
    private static SettingFileAccess launcherSettingsAccess = SettingFileAccess.READ_WRITE;

    /// Access status for `state/launcher-state.json`.
    private static SettingFileAccess launcherStateAccess = SettingFileAccess.READ_WRITE;

    /// Access status for `config/authlib-injector-servers.json`.
    private static SettingFileAccess authlibInjectorServersAccess = SettingFileAccess.READ_WRITE;

    /// Access status for local `config/game-directories.json`.
    private static SettingFileAccess localGameDirectoriesAccess = SettingFileAccess.READ_WRITE;

    /// Access status for user `config/user-game-directories.json`.
    private static SettingFileAccess userGameDirectoriesAccess = SettingFileAccess.READ_WRITE;

    /// Access status for `config/game-settings.json`.
    private static SettingFileAccess gameSettingsAccess = SettingFileAccess.READ_WRITE;

    /// Access status for local `config/accounts.json`.
    private static SettingFileAccess gameAccountsAccess = SettingFileAccess.READ_WRITE;

    /// Access status for user `config/user-accounts.json`.
    private static SettingFileAccess userGameAccountsAccess = SettingFileAccess.READ_WRITE;

    /// Access status for local `private/account-private-data.json`.
    private static SettingFileAccess gameAccountPrivateDataAccess = SettingFileAccess.READ_WRITE;

    /// Access status for user `private/user-account-private-data.json`.
    private static SettingFileAccess userGameAccountPrivateDataAccess = SettingFileAccess.READ_WRITE;

    /// Access status for `config/user-settings.json`.
    private static SettingFileAccess userSettingsAccess = SettingFileAccess.READ_WRITE;

    /// Access status for `state/user-state.json`.
    private static SettingFileAccess userStateAccess = SettingFileAccess.READ_WRITE;

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

    /// Returns the loaded per-workspace authlib-injector server metadata cache.
    private static AuthlibInjectorServerMetadataCache authlibInjectorServerMetadataCache() {
        if (authlibInjectorServerMetadataCache == null) {
            throw new IllegalStateException("Authlib-injector server metadata cache hasn't been loaded");
        }
        return authlibInjectorServerMetadataCache;
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

    /// Returns the current per-workspace account metadata path.
    public static Path gameAccountsLocation() {
        return GAME_ACCOUNTS_LOCATION;
    }

    /// Returns the shared account metadata path.
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

    /// Returns the loaded detached account metadata store.
    static AccountMetadataStore gameAccounts() {
        if (gameAccounts == null) {
            throw new IllegalStateException("Game accounts haven't been loaded");
        }
        return gameAccounts;
    }

    /// Returns the loaded shared account metadata store.
    static AccountMetadataStore userGameAccounts() {
        if (userGameAccounts == null) {
            throw new IllegalStateException("User game accounts haven't been loaded");
        }
        return userGameAccounts;
    }

    /// Returns the loaded detached account private data store.
    private static AccountPrivateData gameAccountPrivateData() {
        if (gameAccountPrivateData == null) {
            throw new IllegalStateException("Game account private data hasn't been loaded");
        }
        return gameAccountPrivateData;
    }

    /// Returns the loaded shared account private data store.
    private static AccountPrivateData userGameAccountPrivateData() {
        if (userGameAccountPrivateData == null) {
            throw new IllegalStateException("User game account private data hasn't been loaded");
        }
        return userGameAccountPrivateData;
    }

    /// Returns the per-workspace authlib-injector servers.
    public static ObservableList<AuthlibInjectorServer> getAuthlibInjectorServers() {
        return authlibInjectorServers().getServers();
    }

    /// Returns the per-workspace account metadata records.
    public static ObservableList<JsonObject> getAccountMetadataRecords() {
        return gameAccounts().getAccounts();
    }

    /// Returns the shared account metadata records.
    public static ObservableList<JsonObject> getUserAccountMetadataRecords() {
        return userGameAccounts().getAccounts();
    }

    /// Creates a metadata/private data snapshot from the loaded account stores.
    ///
    /// @param accounts the loaded account metadata store
    /// @param currentPrivateData the loaded account private data store
    /// @return the split account metadata/private data snapshot
    private static AccountMetadataSnapshot createAccountMetadataSnapshot(
            AccountMetadataStore accounts,
            AccountPrivateData currentPrivateData) {
        AccountPrivateData privateData = new AccountPrivateData();
        privateData.replaceWith(currentPrivateData);
        AccountMetadataStore metadata = accounts.copyWithRecords(accounts.getAccounts());
        return new AccountMetadataSnapshot(metadata, privateData);
    }

    /// Backs up and overwrites account metadata and private data synchronously.
    ///
    /// Private data is saved before metadata so a failed private-data write cannot leave metadata stripped
    /// of credentials without a matching private-data file.
    ///
    /// @param accounts the loaded account metadata store
    /// @param accountsFile the account metadata file helper
    /// @param privateData the loaded account private data store
    /// @param privateDataFile the account private data file helper
    /// @throws IOException if saving either file fails
    private static void backupAndOverwriteAccountMetadataStore(
            AccountMetadataStore accounts,
            JsonSettingFile<AccountMetadataStore> accountsFile,
            AccountPrivateData privateData,
            JsonSettingFile<AccountPrivateData> privateDataFile) throws IOException {
        AccountMetadataSnapshot snapshot = createAccountMetadataSnapshot(accounts, privateData);
        privateDataFile.backupAndOverwriteSync(snapshot.privateData());
        accountsFile.backupAndOverwriteSync(snapshot.metadata());

        accounts.setSchema(AccountMetadataStore.CURRENT_SCHEMA);
        accounts.getAccounts().setAll(snapshot.metadata().getAccounts());
        accounts.setSavable(true);
        accounts.setBackupOnNextSave(false);
        privateData.setSchema(AccountPrivateData.CURRENT_SCHEMA);
        privateData.replaceWith(snapshot.privateData());
        privateData.setSavable(true);
        privateData.setBackupOnNextSave(false);
    }

    /// Updates the per-workspace account metadata and private data stores.
    ///
    /// @param metadataAccounts metadata-only account records
    /// @param privateData private account data keyed by account ID
    /// @param retainedAccountIDs account IDs that should keep private data
    static void updateGameAccounts(
            List<JsonObject> metadataAccounts,
            Map<AccountID, JsonObject> privateData,
            List<AccountID> retainedAccountIDs) {
        updateAccountMetadataStore(
                gameAccounts(),
                GAME_ACCOUNTS_FILE,
                new AccountPrivateDataStore(gameAccountPrivateData(), GAME_ACCOUNT_PRIVATE_DATA_FILE),
                List.of(
                        new AccountPrivateDataStore(gameAccountPrivateData(), GAME_ACCOUNT_PRIVATE_DATA_FILE),
                        new AccountPrivateDataStore(userGameAccountPrivateData(), USER_GAME_ACCOUNT_PRIVATE_DATA_FILE)),
                metadataAccounts,
                privateData,
                retainedAccountIDs,
                true);
    }

    /// Updates the shared account metadata and private data stores.
    ///
    /// @param metadataAccounts metadata-only account records
    /// @param privateData private account data keyed by account ID
    /// @param retainedAccountIDs account IDs that should keep private data
    static void updateUserGameAccounts(
            List<JsonObject> metadataAccounts,
            Map<AccountID, JsonObject> privateData,
            List<AccountID> retainedAccountIDs) {
        updateAccountMetadataStore(
                userGameAccounts(),
                USER_GAME_ACCOUNTS_FILE,
                new AccountPrivateDataStore(userGameAccountPrivateData(), USER_GAME_ACCOUNT_PRIVATE_DATA_FILE),
                List.of(
                        new AccountPrivateDataStore(userGameAccountPrivateData(), USER_GAME_ACCOUNT_PRIVATE_DATA_FILE),
                        new AccountPrivateDataStore(gameAccountPrivateData(), GAME_ACCOUNT_PRIVATE_DATA_FILE)),
                metadataAccounts,
                privateData,
                retainedAccountIDs,
                true);
    }

    /// Saves the loaded per-workspace account metadata store.
    static void saveGameAccountMetadataRecords() {
        saveLoadedAccountMetadataRecords(gameAccounts(), GAME_ACCOUNTS_FILE);
    }

    /// Saves the loaded shared account metadata store.
    static void saveUserGameAccountMetadataRecords() {
        saveLoadedAccountMetadataRecords(userGameAccounts(), USER_GAME_ACCOUNTS_FILE);
    }

    /// Saves loaded account metadata without changing private data.
    ///
    /// @param accounts the loaded account metadata store
    /// @param accountsFile the account metadata file helper
    private static void saveLoadedAccountMetadataRecords(
            AccountMetadataStore accounts,
            JsonSettingFile<AccountMetadataStore> accountsFile) {
        try {
            saveAccountMetadataStore(accounts, accountsFile, List.of(), true, false);
        } catch (IOException e) {
            LOG.warning("Failed to save account metadata", e);
        }
    }

    /// Returns private account data for a loaded account.
    ///
    /// @param accountID the account ID
    /// @param portable whether the account metadata belongs to the per-workspace account store
    /// @return the private account data, or an empty object when none is available
    static JsonObject getAccountPrivateData(AccountID accountID, boolean portable) {
        List<AccountPrivateData> privateDataStores = portable
                ? List.of(gameAccountPrivateData(), userGameAccountPrivateData())
                : List.of(userGameAccountPrivateData(), gameAccountPrivateData());
        @Nullable JsonObject privateData = AccountPrivateData.findPrivateData(accountID, privateDataStores);
        return privateData != null ? privateData : new JsonObject();
    }

    /// Updates account metadata and private data, then saves the changed files.
    ///
    /// @param accounts the loaded account metadata store
    /// @param accountsFile the account metadata file helper
    /// @param defaultPrivateData the default private data store used for new account private data
    /// @param privateDataStores private data stores searched and updated in order
    /// @param metadataAccounts metadata-only account records
    /// @param privateData private account data keyed by account ID
    /// @param retainedAccountIDs account IDs that should keep private data
    /// @param movePrivateDataToDefaultStore whether private data should be moved to the metadata store's default private data store
    private static void updateAccountMetadataStore(
            AccountMetadataStore accounts,
            JsonSettingFile<AccountMetadataStore> accountsFile,
            AccountPrivateDataStore defaultPrivateData,
            List<AccountPrivateDataStore> privateDataStores,
            List<JsonObject> metadataAccounts,
            Map<AccountID, JsonObject> privateData,
            List<AccountID> retainedAccountIDs,
            boolean movePrivateDataToDefaultStore) {
        try {
            List<AccountID> accountIDs = getAccountIDs(metadataAccounts);
            AccountPrivateDataUpdate accountPrivateData =
                    new AccountPrivateDataUpdate(accountIDs, mergeAccountIDs(accountIDs, retainedAccountIDs), privateData);
            if (!canSaveAccountPrivateDataUpdate(
                    accountPrivateData, defaultPrivateData, privateDataStores, movePrivateDataToDefaultStore)) {
                LOG.warning("Skipped account metadata save because account private data is not writable");
                return;
            }

            List<AccountPrivateDataStore> changedPrivateDataStores =
                    distributeAccountPrivateData(
                            accountPrivateData, defaultPrivateData, privateDataStores, movePrivateDataToDefaultStore, true);
            boolean metadataChanged = !metadataAccounts.equals(accounts.getAccounts());
            if (metadataChanged) {
                accounts.getAccounts().setAll(metadataAccounts);
            }
            saveAccountMetadataStore(
                    accounts,
                    accountsFile,
                    changedPrivateDataStores,
                    metadataChanged || accounts.isBackupOnNextSave(),
                    false);
        } catch (IOException e) {
            LOG.warning("Failed to save account private data; skipped account metadata save", e);
        }
    }

    /// Saves account metadata and private data.
    ///
    /// Private data is saved before metadata so a failed private-data write cannot leave metadata stripped
    /// of credentials without a matching private-data file.
    ///
    /// @param accounts the loaded account metadata store
    /// @param accountsFile the account metadata file helper
    /// @param changedPrivateDataStores private data stores whose content or backup state should be saved
    /// @param saveMetadata whether the metadata file should be saved
    /// @param sync whether the metadata file must be saved synchronously
    /// @throws IOException if saving private data fails, or if synchronous metadata saving fails
    private static void saveAccountMetadataStore(
            AccountMetadataStore accounts,
            JsonSettingFile<AccountMetadataStore> accountsFile,
            List<AccountPrivateDataStore> changedPrivateDataStores,
            boolean saveMetadata,
            boolean sync) throws IOException {
        boolean backupOnNextSave = accounts.isBackupOnNextSave();

        for (AccountPrivateDataStore privateDataStore : changedPrivateDataStores) {
            saveAccountPrivateDataStore(privateDataStore, backupOnNextSave);
        }

        if (saveMetadata && accounts.isSavable()) {
            AccountMetadataStore metadata = accounts.copyWithRecords(accounts.getAccounts());
            metadata.setBackupOnNextSave(backupOnNextSave);
            if (sync) {
                accountsFile.saveSync(metadata);
            } else {
                accountsFile.save(metadata);
            }
            accounts.setBackupOnNextSave(false);
        }
    }

    /// Returns account IDs from account metadata records.
    private static List<AccountID> getAccountIDs(List<JsonObject> metadataAccounts) {
        List<AccountID> accountIDs = new ArrayList<>(metadataAccounts.size());
        for (JsonObject metadata : metadataAccounts) {
            @Nullable AccountID accountID = Account.getAccountID(metadata);
            if (accountID != null) {
                accountIDs.add(accountID);
            }
        }
        return accountIDs;
    }

    /// Returns account IDs from two sources without duplicates.
    ///
    /// @param first the first account ID source
    /// @param second the second account ID source
    /// @return the merged account IDs
    private static List<AccountID> mergeAccountIDs(List<AccountID> first, List<AccountID> second) {
        List<AccountID> accountIDs = new ArrayList<>(first.size() + second.size());
        for (AccountID accountID : first) {
            if (!accountIDs.contains(accountID)) {
                accountIDs.add(accountID);
            }
        }
        for (AccountID accountID : second) {
            if (!accountIDs.contains(accountID)) {
                accountIDs.add(accountID);
            }
        }
        return accountIDs;
    }

    /// Creates a private data update from migrated account stores.
    private static AccountPrivateDataUpdate createAccountPrivateDataUpdate(
            LegacyConfigMigrator.AccountMigrationResult accounts) {
        List<AccountID> accountIDs = getAccountIDs(accounts.metadata().getAccounts());
        return new AccountPrivateDataUpdate(
                accountIDs,
                accountIDs,
                accounts.privateData().getPrivateData());
    }

    /// Returns whether private data needed by an account metadata update can be persisted.
    ///
    /// The check is performed before metadata is saved so credentials are not removed from account metadata unless
    /// the matching private data can be written to its target store.
    ///
    /// @param update the account IDs and private data to store
    /// @param defaultPrivateData the default private data store used when no existing store has the account private data
    /// @param privateDataStores private data stores searched in order
    /// @param movePrivateDataToDefaultStore whether private data should be moved to the metadata store's default private data store
    /// @return whether all changed private data can be saved
    private static boolean canSaveAccountPrivateDataUpdate(
            AccountPrivateDataUpdate update,
            AccountPrivateDataStore defaultPrivateData,
            List<AccountPrivateDataStore> privateDataStores,
            boolean movePrivateDataToDefaultStore) {
        for (AccountID accountID : update.accountIDs()) {
            @Nullable JsonObject accountPrivateData = update.privateData().get(accountID);
            if (accountPrivateData == null || accountPrivateData.isEmpty()) {
                continue;
            }

            AccountPrivateDataStore targetPrivateData = movePrivateDataToDefaultStore
                    ? defaultPrivateData
                    : findAccountPrivateDataStore(accountID, defaultPrivateData, privateDataStores);
            if (targetPrivateData.privateData().isSavable()) {
                continue;
            }

            @Nullable JsonObject currentPrivateData =
                    targetPrivateData.privateData().getPrivateData().get(accountID);
            if (!Objects.equals(currentPrivateData, accountPrivateData)) {
                return false;
            }
        }
        return true;
    }

    /// Saves one account private data store synchronously.
    ///
    /// @param privateDataStore the private data store to save
    /// @param backupOnNextSave whether the account metadata save requested a backup
    /// @throws IOException if saving the private data file fails
    private static void saveAccountPrivateDataStore(
            AccountPrivateDataStore privateDataStore,
            boolean backupOnNextSave) throws IOException {
        if (privateDataStore.privateData().isSavable()) {
            privateDataStore.privateData().setBackupOnNextSave(
                    backupOnNextSave || privateDataStore.privateData().isBackupOnNextSave());
            privateDataStore.file().saveSync(privateDataStore.privateData());
        }
    }

    /// Distributes extracted private data into the selected private data stores.
    ///
    /// @param update the account IDs and private data to store
    /// @param defaultPrivateData the default private data store used when no existing store has the account private data
    /// @param privateDataStores private data stores searched and updated in order
    /// @param movePrivateDataToDefaultStore whether private data should be moved to the metadata store's default private data store
    /// @param removeStalePrivateData whether private data for unretained account IDs should be removed
    /// @return private data stores whose content or backup state should be saved
    private static List<AccountPrivateDataStore> distributeAccountPrivateData(
            AccountPrivateDataUpdate update,
            AccountPrivateDataStore defaultPrivateData,
            List<AccountPrivateDataStore> privateDataStores,
            boolean movePrivateDataToDefaultStore,
            boolean removeStalePrivateData) {
        List<AccountPrivateDataStore> changedPrivateDataStores = new ArrayList<>();
        for (AccountID accountID : update.accountIDs()) {
            @Nullable JsonObject accountPrivateData = update.privateData().get(accountID);
            if (accountPrivateData != null && accountPrivateData.isEmpty()) {
                accountPrivateData = null;
            }
            AccountPrivateDataStore targetPrivateData;
            if (accountPrivateData == null || movePrivateDataToDefaultStore) {
                targetPrivateData = defaultPrivateData;
            } else {
                targetPrivateData = findAccountPrivateDataStore(accountID, defaultPrivateData, privateDataStores);
            }

            for (AccountPrivateDataStore privateDataStore : privateDataStores) {
                @Nullable JsonObject currentPrivateData =
                        privateDataStore.privateData().getPrivateData().get(accountID);
                if (privateDataStore.equals(targetPrivateData) && accountPrivateData != null) {
                    if (!Objects.equals(currentPrivateData, accountPrivateData)) {
                        privateDataStore.privateData().putPrivateData(accountID, accountPrivateData);
                        if (privateDataStore.privateData().isSavable()) {
                            addIfAbsent(changedPrivateDataStores, privateDataStore);
                        }
                    }
                } else if (privateDataStore.privateData().isSavable() && currentPrivateData != null) {
                    privateDataStore.privateData().removePrivateData(accountID);
                    addIfAbsent(changedPrivateDataStores, privateDataStore);
                }
            }

            if (accountPrivateData != null && !privateDataStores.contains(targetPrivateData)) {
                targetPrivateData.privateData().putPrivateData(accountID, accountPrivateData);
                if (targetPrivateData.privateData().isSavable()) {
                    addIfAbsent(changedPrivateDataStores, targetPrivateData);
                }
            }
        }

        if (removeStalePrivateData) {
            for (AccountPrivateDataStore privateDataStore : privateDataStores) {
                if (!privateDataStore.privateData().isSavable()) {
                    continue;
                }

                List<AccountID> staleAccountIDs = new ArrayList<>();
                for (AccountID accountID : privateDataStore.privateData().getPrivateData().keySet()) {
                    if (!update.retainedAccountIDs().contains(accountID)) {
                        staleAccountIDs.add(accountID);
                    }
                }
                for (AccountID accountID : staleAccountIDs) {
                    privateDataStore.privateData().removePrivateData(accountID);
                    addIfAbsent(changedPrivateDataStores, privateDataStore);
                }
            }
        }

        for (AccountPrivateDataStore privateDataStore : privateDataStores) {
            if (privateDataStore.privateData().isBackupOnNextSave()) {
                addIfAbsent(changedPrivateDataStores, privateDataStore);
            }
        }
        return changedPrivateDataStores;
    }

    /// Selects the private data store that should receive updated private data.
    ///
    /// @param accountID the stable account ID
    /// @param defaultPrivateData the default private data store used when no existing writable store has the account private data
    /// @param privateDataStores private data stores searched in order
    /// @return the private data store that should receive updated private data
    private static AccountPrivateDataStore findAccountPrivateDataStore(
            AccountID accountID,
            AccountPrivateDataStore defaultPrivateData,
            List<AccountPrivateDataStore> privateDataStores) {
        for (AccountPrivateDataStore privateDataStore : privateDataStores) {
            if (privateDataStore.privateData().isSavable()
                    && privateDataStore.privateData().containsPrivateData(accountID)) {
                return privateDataStore;
            }
        }

        return defaultPrivateData;
    }

    /// Adds a private data store to a list unless it is already present.
    ///
    /// @param privateDataStores the target list
    /// @param privateDataStore the private data store to add
    private static void addIfAbsent(
            List<AccountPrivateDataStore> privateDataStores,
            AccountPrivateDataStore privateDataStore) {
        if (!privateDataStores.contains(privateDataStore)) {
            privateDataStores.add(privateDataStore);
        }
    }

    /// Split account metadata and private data ready to be saved.
    ///
    /// @param metadata the metadata-only account store
    /// @param privateData the account private data store
    private record AccountMetadataSnapshot(AccountMetadataStore metadata, AccountPrivateData privateData) {
    }

    /// Account metadata load result.
    ///
    /// @param access account metadata file access status
    /// @param migratedAccountsSaved whether migrated account metadata and private data were saved
    private record AccountMetadataLoadResult(SettingFileAccess access, boolean migratedAccountsSaved) {
    }

    /// Account private data update ready to be distributed among private data stores.
    ///
    /// @param accountIDs account IDs whose private data should be updated
    /// @param retainedAccountIDs account IDs whose private data should be kept
    /// @param privateData private account data keyed by account ID
    private record AccountPrivateDataUpdate(
            List<AccountID> accountIDs,
            List<AccountID> retainedAccountIDs,
            Map<AccountID, JsonObject> privateData) {
    }

    /// Account private data store and its backing JSON file.
    ///
    /// @param privateData the loaded account private data store
    /// @param file the JSON setting file helper backing the private data store
    private record AccountPrivateDataStore(
            AccountPrivateData privateData,
            JsonSettingFile<AccountPrivateData> file) {
    }

    /// Returns the loaded per-workspace game directory store.
    static GameDirectories localGameDirectories() {
        if (localGameDirectories == null) {
            throw new IllegalStateException("Game directories haven't been loaded");
        }
        return localGameDirectories;
    }

    /// Returns the loaded user game directory store.
    static GameDirectories userGameDirectories() {
        if (userGameDirectories == null) {
            throw new IllegalStateException("Game directories haven't been loaded");
        }
        return userGameDirectories;
    }

    /// Returns the reusable game setting presets.
    public static ObservableList<GameSettings.Preset> getGameSettings() {
        return gameSettingsPresets().getPresets();
    }

    /// Returns the game setting preset with the given ID.
    public static GameSettings.@Nullable Preset getGameSettings(@Nullable GameSettingsPresetID id) {
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

    /// Returns whether the core launcher settings cannot be safely overwritten.
    public static boolean hasReadOnlyCoreSettings() {
        return launcherSettingsAccess.blocksEditing() || launcherStateAccess.blocksEditing();
    }

    /// Returns whether game settings presets cannot be safely overwritten.
    public static boolean isGameSettingsReadOnly() {
        return gameSettingsAccess.blocksEditing();
    }

    /// Returns whether the local game directory store cannot be safely overwritten.
    public static boolean isLocalGameDirectoriesReadOnly() {
        return localGameDirectoriesAccess.blocksEditing();
    }

    /// Returns whether the user game directory store cannot be safely overwritten.
    public static boolean isUserGameDirectoriesReadOnly() {
        return userGameDirectoriesAccess.blocksEditing();
    }

    /// Returns whether the local account files cannot be safely overwritten.
    public static boolean isGameAccountsReadOnly() {
        return gameAccountsAccess.blocksEditing() || gameAccountPrivateDataAccess.blocksEditing();
    }

    /// Returns whether the user account files cannot be safely overwritten.
    public static boolean isUserGameAccountsReadOnly() {
        return userGameAccountsAccess.blocksEditing() || userGameAccountPrivateDataAccess.blocksEditing();
    }

    /// Returns whether the authlib-injector server list cannot be safely overwritten.
    public static boolean isAuthlibInjectorServersReadOnly() {
        return authlibInjectorServersAccess.blocksEditing();
    }

    /// Returns whether the user settings store cannot be safely overwritten.
    public static boolean isUserSettingsReadOnly() {
        return userSettingsAccess.blocksEditing();
    }

    /// Backs up and overwrites `config/game-settings.json` with the currently loaded presets.
    public static void forceOverwriteGameSettings() {
        boolean installAutoSave = !gameSettingsPresets().isSavable();
        GAME_SETTINGS_FILE.backupAndOverwrite(gameSettingsPresets());
        if (installAutoSave) {
            GAME_SETTINGS_FILE.installAutoSave(gameSettingsPresets());
        }
        gameSettingsAccess = SettingFileAccess.READ_WRITE;
    }

    /// Backs up and overwrites local `config/game-directories.json` with the currently loaded game directories.
    public static void forceOverwriteLocalGameDirectories() {
        boolean installAutoSave = !localGameDirectories().isSavable();
        LOCAL_GAME_DIRECTORIES_FILE.backupAndOverwrite(localGameDirectories());
        if (installAutoSave) {
            LOCAL_GAME_DIRECTORIES_FILE.installAutoSave(localGameDirectories());
        }
        localGameDirectoriesAccess = SettingFileAccess.READ_WRITE;
    }

    /// Backs up and overwrites user `config/user-game-directories.json` with the currently loaded game directories.
    public static void forceOverwriteUserGameDirectories() {
        boolean installAutoSave = !userGameDirectories().isSavable();
        USER_GAME_DIRECTORIES_FILE.backupAndOverwrite(userGameDirectories());
        if (installAutoSave) {
            USER_GAME_DIRECTORIES_FILE.installAutoSave(userGameDirectories());
        }
        userGameDirectoriesAccess = SettingFileAccess.READ_WRITE;
    }

    /// Backs up and overwrites local account metadata and private data with the currently loaded accounts.
    ///
    /// @throws IOException if saving either file fails
    public static void forceOverwriteGameAccounts() throws IOException {
        backupAndOverwriteAccountMetadataStore(
                gameAccounts(),
                GAME_ACCOUNTS_FILE,
                gameAccountPrivateData(),
                GAME_ACCOUNT_PRIVATE_DATA_FILE);
        gameAccountsAccess = SettingFileAccess.READ_WRITE;
        gameAccountPrivateDataAccess = SettingFileAccess.READ_WRITE;
    }

    /// Backs up and overwrites user account metadata and private data with the currently loaded accounts.
    ///
    /// @throws IOException if saving either file fails
    public static void forceOverwriteUserGameAccounts() throws IOException {
        backupAndOverwriteAccountMetadataStore(
                userGameAccounts(),
                USER_GAME_ACCOUNTS_FILE,
                userGameAccountPrivateData(),
                USER_GAME_ACCOUNT_PRIVATE_DATA_FILE);
        userGameAccountsAccess = SettingFileAccess.READ_WRITE;
        userGameAccountPrivateDataAccess = SettingFileAccess.READ_WRITE;
    }

    /// Backs up and overwrites `config/authlib-injector-servers.json` with the current server list.
    public static void forceOverwriteAuthlibInjectorServers() {
        boolean installAutoSave = !authlibInjectorServers().isSavable();
        AUTHLIB_INJECTOR_SERVERS_FILE.backupAndOverwrite(authlibInjectorServers());
        if (installAutoSave) {
            AUTHLIB_INJECTOR_SERVERS_FILE.installAutoSave(authlibInjectorServers());
        }
        authlibInjectorServersAccess = SettingFileAccess.READ_WRITE;
    }

    /// Loads configs, installs save listeners, and applies process-wide settings.
    public static void init() throws IOException {
        if (launcherSettings != null) {
            throw new IllegalStateException("Configuration is already loaded");
        }

        checkLocalConfigOwner();
        newlyCreated = isNewWorkspace();

        LoadedLauncherSettings loadedLauncherSettings = loadLauncherSettings();
        launcherSettings = loadedLauncherSettings.settings();
        launcherSettingsAccess = loadedLauncherSettings.access();

        @Nullable LegacyConfigMigrator.LegacyConfigMigration legacyConfigMigration =
                loadedLauncherSettings.pendingMigration();
        LegacyConfigMigrator.DetachedSettings migratedDetachedSettings = legacyConfigMigration == null
                ? LegacyConfigMigrator.DetachedSettings.empty()
                : legacyConfigMigration.detachedSettings();

        boolean currentUserSettingsExist = Files.exists(USER_SETTINGS_LOCATION) && Files.exists(USER_STATE_LOCATION);
        @Nullable LegacyConfigMigrator.UserSettingsMigrationResult userSettingsMigrationResult = currentUserSettingsExist
                ? null
                : LegacyConfigMigrator.migrateLegacyUserSettings();
        userSettingsAccess = loadUserSettings(userSettingsMigrationResult);
        userStateAccess = loadUserState(userSettingsMigrationResult);
        if (userSettingsMigrationResult != null) {
            LegacyConfigMigrator.completeLegacyUserSettingsMigration(userSettingsMigrationResult);
        }

        Locale.setDefault(settings().languageProperty().get().getLocale());
        I18n.setLocale(launcherSettings.languageProperty().get());
        LOG.setLogRetention(userSettings().logRetentionProperty().get());
        boolean migratedGameDirectoriesSaved = loadGameDirectories(
                migratedDetachedSettings.localGameDirectories(),
                migratedDetachedSettings.userGameDirectories());
        gameSettingsAccess = loadGameSettingsPresets(migratedDetachedSettings.gameSettingsPresets());
        launcherStateAccess = loadLauncherState(migratedDetachedSettings.launcherState());
        authlibInjectorServersAccess =
                loadAuthlibInjectorServers(migratedDetachedSettings.authlibInjectorServers());
        loadAuthlibInjectorServerMetadataCache();
        userGameAccountPrivateDataAccess = loadUserGameAccountPrivateData();
        gameAccountPrivateDataAccess = loadGameAccountPrivateData();
        userGameAccountsAccess = loadUserGameAccounts();
        AccountMetadataLoadResult loadedGameAccounts =
                loadGameAccountsWithResult(migratedDetachedSettings.accountMigration());
        gameAccountsAccess = loadedGameAccounts.access();

        if (Files.exists(Metadata.HMCL_LOCAL_HOME)) {
            checkWritable(Metadata.HMCL_LOCAL_HOME);
        }

        if (legacyConfigMigration != null
                && loadedGameAccounts.migratedAccountsSaved()
                && migratedGameDirectoriesSaved) {
            LOG.info("Migrating settings from " + legacyConfigMigration.path() + " to " + SETTINGS_LOCATION);
            FileUtils.saveSafely(SETTINGS_LOCATION, legacyConfigMigration.launcherSettings().toJson());
            LegacyConfigMigrator.completeLegacyConfigMigration(legacyConfigMigration);
        } else if (legacyConfigMigration != null) {
            LOG.warning("Skipped legacy config migration because detached migration data was not saved");
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
                LOG.warning("Failed to read launcher settings file: " + SETTINGS_LOCATION, e);

                LauncherSettings settings = new LauncherSettings();
                settings.setBackupOnNextSave(true);
                return new LoadedLauncherSettings(settings, null, SettingFileAccess.READ_WRITE);
            }

            if (jsonObject == null) {
                LOG.warning("Launcher settings file is empty: " + SETTINGS_LOCATION);

                return new LoadedLauncherSettings(new LauncherSettings(), null, SettingFileAccess.READ_WRITE);
            }

            JsonSchema.CompatibilityResult schemaResult =
                    JsonSchema.check(jsonObject, LauncherSettings.CURRENT_SCHEMA);
            switch (schemaResult.status()) {
                case MISSING -> LOG.warning("Missing schema in launcher settings file: " + SETTINGS_LOCATION);
                case INVALID -> LOG.warning("Invalid schema in launcher settings file: "
                        + SETTINGS_LOCATION + ", Actual: " + schemaResult.invalidValue());
                case UNPARSEABLE -> LOG.warning("Unparseable schema in launcher settings file: "
                        + SETTINGS_LOCATION + ", Actual: " + schemaResult.actual());
                case UNEXPECTED_ID -> LOG.warning("Unexpected launcher settings file schema. Expected: "
                        + LauncherSettings.CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case UNSUPPORTED_MAJOR, READ_ONLY_PRESERVE_SCHEMA -> LOG.warning("Unsupported launcher settings file schema. Expected: "
                        + LauncherSettings.CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case READ_WRITE, READ_WRITE_PRESERVE_SCHEMA -> {
                }
            }
            if (!schemaResult.readable()) {
                LauncherSettings settings = new LauncherSettings();
                settings.setSavable(false);
                return new LoadedLauncherSettings(settings, null, SettingFileAccess.UNREADABLE);
            }

            try {
                LauncherSettings settings = LauncherSettings.fromJson(jsonObject);
                if (settings == null) {
                    settings = new LauncherSettings();
                    settings.setSavable(false);
                    return new LoadedLauncherSettings(settings, null, SettingFileAccess.UNREADABLE);
                }

                if (!schemaResult.preserveSchema() && !LauncherSettings.CURRENT_SCHEMA.equals(settings.schemaProperty().get())) {
                    settings.schemaProperty().set(LauncherSettings.CURRENT_SCHEMA);
                }

                settings.setSavable(schemaResult.allowSave());
                return new LoadedLauncherSettings(settings, null, schemaResult.allowSave()
                        ? SettingFileAccess.READ_WRITE
                        : SettingFileAccess.READ_ONLY);
            } catch (Exception e) {
                LOG.warning("Failed to parse launcher settings file: " + SETTINGS_LOCATION, e);
                LauncherSettings settings = new LauncherSettings();
                settings.setBackupOnNextSave(true);
                return new LoadedLauncherSettings(settings, null, SettingFileAccess.READ_WRITE);
            }
        } else {
            LegacyConfigMigrator.LegacyConfigMigration migration = migrateLegacySettings();
            if (migration != null) {
                return new LoadedLauncherSettings(migration.launcherSettings(), migration, SettingFileAccess.READ_WRITE);
            }

            migration = LegacyConfigMigrator.migrateLegacyConfig();
            if (migration != null) {
                return new LoadedLauncherSettings(migration.launcherSettings(), migration, SettingFileAccess.READ_WRITE);
            }
        }

        return new LoadedLauncherSettings(new LauncherSettings(), null, SettingFileAccess.READ_WRITE);
    }

    /// Migrates the legacy `settings.json` file when it is present.
    private static LegacyConfigMigrator.@Nullable LegacyConfigMigration migrateLegacySettings() throws IOException {
        if (!Files.isRegularFile(LEGACY_SETTINGS_LOCATION)) {
            return null;
        }

        JsonObject jsonObject;
        try {
            jsonObject = JsonUtils.fromJsonFile(LEGACY_SETTINGS_LOCATION, JsonObject.class);
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy settings file: " + LEGACY_SETTINGS_LOCATION, e);
            return null;
        }

        if (jsonObject == null) {
            LOG.info("Legacy settings file is empty: " + LEGACY_SETTINGS_LOCATION);
            return null;
        }

        if (jsonObject.has(JsonSchema.PROPERTY_SCHEMA)) {
            LOG.info("Ignoring schematized legacy settings file: " + LEGACY_SETTINGS_LOCATION);
            return null;
        }

        return LegacyConfigMigrator.migrateLegacyConfigIfNeeded(LEGACY_SETTINGS_LOCATION);
    }

    /// Returns whether the current workspace already has any local configuration footprint.
    private static boolean isNewWorkspace() {
        return !(Files.exists(SETTINGS_LOCATION)
                || Files.exists(STATE_LOCATION)
                || Files.exists(AUTHLIB_INJECTOR_SERVERS_LOCATION)
                || Files.exists(LOCAL_GAME_DIRECTORIES_LOCATION)
                || Files.exists(GAME_SETTINGS_LOCATION)
                || Files.exists(GAME_ACCOUNTS_LOCATION)
                || Files.exists(GAME_ACCOUNT_PRIVATE_DATA_LOCATION)
                || Files.exists(LEGACY_SETTINGS_LOCATION)
                || LegacyConfigMigrator.hasLegacyConfig());
    }

    /// Loads game directories and installs the save listener.
    ///
    /// @param fallbackLocalGameDirectories the fallback store used when the local game directory file does not exist
    /// @param migratedUserGameDirectories absolute migrated directories to merge into the user-level store
    /// @return whether migrated user-level game directories were saved or required no store changes
    private static boolean loadGameDirectories(
            @Nullable GameDirectories fallbackLocalGameDirectories,
            @Nullable GameDirectories migratedUserGameDirectories) throws IOException {
        if (localGameDirectories != null || userGameDirectories != null) {
            throw new IllegalStateException("Game directories are already loaded");
        }

        boolean newlyCreatedLocal = !Files.exists(LOCAL_GAME_DIRECTORIES_LOCATION);
        boolean newlyCreatedUser = !Files.exists(USER_GAME_DIRECTORIES_LOCATION);
        JsonSettingFile.LoadResult<GameDirectories> userResult = USER_GAME_DIRECTORIES_FILE.load(null);
        JsonSettingFile.LoadResult<GameDirectories> localResult =
                LOCAL_GAME_DIRECTORIES_FILE.load(fallbackLocalGameDirectories);

        boolean userGameDirectoriesChanged = migratedUserGameDirectories != null
                && LegacyConfigMigrator.mergeMigratedUserGameDirectories(
                        settings(),
                        userResult.value(),
                        migratedUserGameDirectories);

        localGameDirectories = localResult.value();
        localGameDirectories.setUserFile(false);
        localGameDirectories.setNewlyCreated(newlyCreatedLocal);

        userGameDirectories = userResult.value();
        userGameDirectories.setUserFile(true);
        userGameDirectories.setNewlyCreated(newlyCreatedUser);

        if (localGameDirectories.isSavable()) {
            LOCAL_GAME_DIRECTORIES_FILE.installAutoSave(localGameDirectories);
        }
        if (userGameDirectories.isSavable()) {
            USER_GAME_DIRECTORIES_FILE.installAutoSave(userGameDirectories);
        }

        if (newlyCreatedLocal && localGameDirectories.isSavable()) {
            LOCAL_GAME_DIRECTORIES_FILE.saveSync(localGameDirectories);
        }
        if ((newlyCreatedUser || userGameDirectoriesChanged) && userGameDirectories.isSavable()) {
            USER_GAME_DIRECTORIES_FILE.saveSync(userGameDirectories);
        }

        localGameDirectoriesAccess = localResult.access();
        userGameDirectoriesAccess = userResult.access();
        return !userGameDirectoriesChanged || userGameDirectories.isSavable();
    }

    /// Loads game settings presets and installs the save listener.
    ///
    /// @param fallbackGameSettingsPresets the fallback store used when the preset file does not exist
    /// @return the preset file access status
    private static SettingFileAccess loadGameSettingsPresets(
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
            GAME_SETTINGS_FILE.saveSync(gameSettingsPresets);
        }

        return result.access();
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
    /// @return the launcher state file access status
    private static SettingFileAccess loadLauncherState(
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
            STATE_FILE.saveSync(launcherState);
        }

        return result.access();
    }

    /// Loads authlib-injector servers and installs the save listener.
    ///
    /// @param fallbackAuthlibInjectorServers the fallback list used when the server list file does not exist
    /// @return the server list file access status
    private static SettingFileAccess loadAuthlibInjectorServers(
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
            AUTHLIB_INJECTOR_SERVERS_FILE.saveSync(authlibInjectorServers);
        }

        return result.access();
    }

    /// Loads authlib-injector server metadata cache and installs the save listener.
    private static void loadAuthlibInjectorServerMetadataCache() {
        if (authlibInjectorServerMetadataCache != null) {
            throw new IllegalStateException("Authlib-injector server metadata cache is already loaded");
        }

        try {
            JsonSettingFile.LoadResult<AuthlibInjectorServerMetadataCache> result =
                    AUTHLIB_INJECTOR_SERVER_METADATA_CACHE_FILE.load(null);
            authlibInjectorServerMetadataCache = result.value();
        } catch (IOException e) {
            LOG.warning("Failed to load authlib-injector server metadata cache", e);
            authlibInjectorServerMetadataCache = new AuthlibInjectorServerMetadataCache();
            authlibInjectorServerMetadataCache.setSavable(false);
        }

        if (authlibInjectorServerMetadataCache.isSavable()) {
            try {
                Files.createDirectories(AUTHLIB_INJECTOR_SERVER_METADATA_CACHE_LOCATION.getParent());
                AUTHLIB_INJECTOR_SERVER_METADATA_CACHE_FILE.installAutoSave(authlibInjectorServerMetadataCache);
            } catch (IOException e) {
                LOG.warning("Failed to prepare authlib-injector server metadata cache directory", e);
                authlibInjectorServerMetadataCache.setSavable(false);
            }
        }

        bindAuthlibInjectorServerMetadataCache();
    }

    /// Restores cached metadata for loaded servers and keeps the cache in sync with metadata refreshes.
    private static void bindAuthlibInjectorServerMetadataCache() {
        for (AuthlibInjectorServer server : authlibInjectorServers().getServers()) {
            bindAuthlibInjectorServerMetadataCache(server, false);
        }

        authlibInjectorServers().getServers().addListener((ListChangeListener<AuthlibInjectorServer>) change -> {
            while (change.next()) {
                for (AuthlibInjectorServer server : change.getRemoved()) {
                    unbindAuthlibInjectorServerMetadataCache(server);
                }
                for (AuthlibInjectorServer server : change.getAddedSubList()) {
                    bindAuthlibInjectorServerMetadataCache(server, true);
                }
            }
        });
    }

    /// Connects one server to the metadata cache.
    private static void bindAuthlibInjectorServerMetadataCache(
            AuthlibInjectorServer server,
            boolean storeExistingMetadata) {
        if (authlibInjectorServerMetadataListeners.containsKey(server)) {
            return;
        }

        AuthlibInjectorServerMetadataCache cache = authlibInjectorServerMetadataCache();
        cache.initialize(server, storeExistingMetadata);

        InvalidationListener listener = ignored -> cache.store(server);
        server.addListener(listener);
        authlibInjectorServerMetadataListeners.put(server, listener);
    }

    /// Disconnects one server from the metadata cache and removes its cached metadata.
    private static void unbindAuthlibInjectorServerMetadataCache(AuthlibInjectorServer server) {
        InvalidationListener listener = authlibInjectorServerMetadataListeners.remove(server);
        if (listener != null) {
            server.removeListener(listener);
        }

        authlibInjectorServerMetadataCache().remove(server);
    }

    /// Loads shared account private data.
    ///
    /// @return the shared account private data file access status
    private static SettingFileAccess loadUserGameAccountPrivateData() {
        if (userGameAccountPrivateData != null) {
            throw new IllegalStateException("User game account private data is already loaded");
        }

        try {
            JsonSettingFile.LoadResult<AccountPrivateData> result =
                    USER_GAME_ACCOUNT_PRIVATE_DATA_FILE.load(null);
            userGameAccountPrivateData = result.value();
            return result.access();
        } catch (IOException e) {
            LOG.warning("Failed to load user game account private data", e);
            userGameAccountPrivateData = new AccountPrivateData();
            userGameAccountPrivateData.setSavable(false);
            return SettingFileAccess.UNREADABLE;
        }
    }

    /// Loads per-workspace account private data.
    ///
    /// @return the per-workspace account private data file access status
    private static SettingFileAccess loadGameAccountPrivateData() {
        if (gameAccountPrivateData != null) {
            throw new IllegalStateException("Game account private data is already loaded");
        }

        try {
            JsonSettingFile.LoadResult<AccountPrivateData> result =
                    GAME_ACCOUNT_PRIVATE_DATA_FILE.load(null);
            gameAccountPrivateData = result.value();
            return result.access();
        } catch (IOException e) {
            LOG.warning("Failed to load game account private data", e);
            gameAccountPrivateData = new AccountPrivateData();
            gameAccountPrivateData.setSavable(false);
            return SettingFileAccess.UNREADABLE;
        }
    }

    /// Loads shared account metadata.
    ///
    /// @return the shared account metadata file access status
    private static SettingFileAccess loadUserGameAccounts() {
        if (userGameAccounts != null) {
            throw new IllegalStateException("User game accounts are already loaded");
        }

        boolean newlyCreated = !Files.exists(USER_GAME_ACCOUNTS_LOCATION);
        @Nullable LegacyConfigMigrator.UserAccountsMigrationResult migrationResult =
                newlyCreated ? LegacyConfigMigrator.migrateLegacyUserAccounts() : null;
        @Nullable LegacyConfigMigrator.AccountMigrationResult migrated =
                migrationResult != null ? migrationResult.accounts() : null;
        try {
            JsonSettingFile.LoadResult<AccountMetadataStore> result = USER_GAME_ACCOUNTS_FILE.load(
                    migrated != null ? migrated.metadata() : null);
            userGameAccounts = result.value();

            AccountPrivateDataStore defaultPrivateData =
                    new AccountPrivateDataStore(userGameAccountPrivateData(), USER_GAME_ACCOUNT_PRIVATE_DATA_FILE);
            List<AccountPrivateDataStore> privateDataStores = List.of(
                    new AccountPrivateDataStore(userGameAccountPrivateData(), USER_GAME_ACCOUNT_PRIVATE_DATA_FILE),
                    new AccountPrivateDataStore(gameAccountPrivateData(), GAME_ACCOUNT_PRIVATE_DATA_FILE));
            @Nullable AccountPrivateDataUpdate privateDataUpdate = migrated != null && newlyCreated
                    ? createAccountPrivateDataUpdate(migrated)
                    : null;
            boolean canSavePrivateData = privateDataUpdate == null
                    || canSaveAccountPrivateDataUpdate(privateDataUpdate, defaultPrivateData, privateDataStores, false);
            List<AccountPrivateDataStore> changedPrivateDataStores = privateDataUpdate != null && canSavePrivateData
                    ? distributeAccountPrivateData(privateDataUpdate, defaultPrivateData, privateDataStores, false, false)
                    : List.of();
            if ((newlyCreated || !changedPrivateDataStores.isEmpty() || userGameAccounts.isBackupOnNextSave())
                    && userGameAccounts.isSavable()
                    && canSavePrivateData) {
                saveAccountMetadataStore(
                        userGameAccounts,
                        USER_GAME_ACCOUNTS_FILE,
                        changedPrivateDataStores,
                        true,
                        true);
                if (migrationResult != null) {
                    LegacyConfigMigrator.completeLegacyUserAccountsMigration(migrationResult);
                }
            }

            return result.access();
        } catch (IOException e) {
            LOG.warning("Failed to load user game accounts", e);
            userGameAccounts = migrated != null ? migrated.metadata() : new AccountMetadataStore();
            if (migrated != null) {
                distributeAccountPrivateData(
                        createAccountPrivateDataUpdate(migrated),
                        new AccountPrivateDataStore(userGameAccountPrivateData(), USER_GAME_ACCOUNT_PRIVATE_DATA_FILE),
                        List.of(
                                new AccountPrivateDataStore(userGameAccountPrivateData(),
                                        USER_GAME_ACCOUNT_PRIVATE_DATA_FILE),
                                new AccountPrivateDataStore(gameAccountPrivateData(), GAME_ACCOUNT_PRIVATE_DATA_FILE)),
                        false,
                        false);
            }
            userGameAccounts.setSavable(false);
            return SettingFileAccess.UNREADABLE;
        }
    }

    /// Loads account metadata and reports whether migrated account data was persisted.
    ///
    /// @param fallbackGameAccounts the fallback stores used when the account metadata file does not exist
    /// @return the account metadata file access status and migration persistence result
    private static AccountMetadataLoadResult loadGameAccountsWithResult(
            @Nullable LegacyConfigMigrator.AccountMigrationResult fallbackGameAccounts) throws IOException {
        if (gameAccounts != null) {
            throw new IllegalStateException("Game accounts are already loaded");
        }

        boolean newlyCreated = !Files.exists(GAME_ACCOUNTS_LOCATION);
        JsonSettingFile.LoadResult<AccountMetadataStore> result =
                GAME_ACCOUNTS_FILE.load(fallbackGameAccounts != null ? fallbackGameAccounts.metadata() : null);
        gameAccounts = result.value();

        AccountPrivateDataStore defaultPrivateData =
                new AccountPrivateDataStore(gameAccountPrivateData(), GAME_ACCOUNT_PRIVATE_DATA_FILE);
        List<AccountPrivateDataStore> privateDataStores = List.of(
                new AccountPrivateDataStore(gameAccountPrivateData(), GAME_ACCOUNT_PRIVATE_DATA_FILE),
                new AccountPrivateDataStore(userGameAccountPrivateData(), USER_GAME_ACCOUNT_PRIVATE_DATA_FILE));
        @Nullable AccountPrivateDataUpdate privateDataUpdate = fallbackGameAccounts != null && newlyCreated
                ? createAccountPrivateDataUpdate(fallbackGameAccounts)
                : null;
        boolean canSavePrivateData = privateDataUpdate == null
                || canSaveAccountPrivateDataUpdate(privateDataUpdate, defaultPrivateData, privateDataStores, false);
        List<AccountPrivateDataStore> changedPrivateDataStores = privateDataUpdate != null && canSavePrivateData
                ? distributeAccountPrivateData(privateDataUpdate, defaultPrivateData, privateDataStores, false, false)
                : List.of();
        if ((newlyCreated || !changedPrivateDataStores.isEmpty() || gameAccounts.isBackupOnNextSave())
                && gameAccounts.isSavable()
                && canSavePrivateData) {
            saveAccountMetadataStore(
                    gameAccounts,
                    GAME_ACCOUNTS_FILE,
                    changedPrivateDataStores,
                    true,
                    true);
        }

        return new AccountMetadataLoadResult(result.access(), canSavePrivateData);
    }

    /// Checks whether root is reading per-workspace config data owned by another user.
    private static void checkLocalConfigOwner() {
        checkOwner(Metadata.HMCL_LOCAL_HOME);
        checkOwner(SETTINGS_LOCATION);
        checkOwner(LEGACY_SETTINGS_LOCATION);
        checkOwner(STATE_LOCATION);
        checkOwner(AUTHLIB_INJECTOR_SERVERS_LOCATION);
        checkOwner(LOCAL_GAME_DIRECTORIES_LOCATION);
        checkOwner(GAME_SETTINGS_LOCATION);
        checkOwner(GAME_ACCOUNTS_LOCATION);
        checkOwner(GAME_ACCOUNT_PRIVATE_DATA_LOCATION);
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
    ///
    /// @return the user settings file access status
    private static SettingFileAccess loadUserSettings(
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
            USER_SETTINGS_FILE.saveSync(userSettingsInstance);
        }

        return result.access();
    }

    /// Loads user state and installs the save listener.
    ///
    /// @return the user state file access status
    private static SettingFileAccess loadUserState(
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
            USER_STATE_FILE.saveSync(userStateInstance);
        }

        return result.access();
    }

    /// Result of loading per-workspace launcher settings.
    ///
    /// @param settings the loaded launcher settings
    /// @param pendingMigration the pending legacy config migration, or `null` when no legacy config was migrated
    private record LoadedLauncherSettings(
            LauncherSettings settings,
            @Nullable LegacyConfigMigrator.LegacyConfigMigration pendingMigration,
            SettingFileAccess access) {
    }

}
