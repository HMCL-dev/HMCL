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

import com.google.gson.*;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Migrates legacy per-workspace config files into the current launcher settings file.
///
/// HMCL used hmcl.json and .hmcl.json as the main per-workspace config files before HMCL 3.16.
/// Those files are now legacy inputs only: migration reads them, writes a new launcher settings file,
/// and leaves the original files unchanged.
///
/// @author Glavo
@NotNullByDefault
public final class LegacyConfigMigrator {
    /// The last numeric config version used by the legacy hmcl.json and .hmcl.json files.
    private static final int LEGACY_CURRENT_CONFIG_VERSION = 2;

    /// Namespace used to generate stable IDs for legacy profiles.
    @VisibleForTesting
    static final UUID LEGACY_PROFILE_ID_NAMESPACE = new UUID(0xd5193464a32e52adL, 0xb50ad88fe967c3a2L);

    /// Namespace used to generate stable IDs for profile-level game settings migrated from legacy profiles.
    @VisibleForTesting
    static final UUID LEGACY_GAME_SETTINGS_ID_NAMESPACE = new UUID(0x4ba56ddd06b45aa7L, 0xb4da9e3c9707f873L);

    /// Namespace used to generate stable account IDs from legacy account identity fields.
    @VisibleForTesting
    static final UUID LEGACY_ACCOUNT_ID_NAMESPACE = new UUID(0xcdc608bb426e5b93L, 0x948db7965705c9feL);

    /// The legacy built-in profile name for the current workspace game directory.
    private static final String LEGACY_DEFAULT_PROFILE = "Default";

    /// The total transparent custom window shadow size used by legacy launcher window bounds.
    private static final int LEGACY_CUSTOM_DECORATION_SHADOW_EXTENT = 16;

    /// The transparent custom window shadow size on one side used by legacy launcher window bounds.
    private static final int LEGACY_CUSTOM_DECORATION_SHADOW_SIZE = LEGACY_CUSTOM_DECORATION_SHADOW_EXTENT / 2;

    /// The legacy built-in profile name for the user-home game directory.
    private static final String LEGACY_HOME_PROFILE = "Home";

    /// The legacy built-in current-workspace profile ID.
    private static final GameDirectoryID LEGACY_DEFAULT_PROFILE_ID = getLegacyProfileID(LEGACY_DEFAULT_PROFILE);

    /// The legacy built-in user-home profile ID.
    private static final GameDirectoryID LEGACY_HOME_PROFILE_ID = getLegacyProfileID(LEGACY_HOME_PROFILE);

    /// The legacy Windows and portable configuration file name used before HMCL 3.16.
    private static final String LEGACY_CONFIG_FILENAME = "hmcl.json";

    /// The legacy Linux configuration file name used before HMCL 3.16.
    private static final String LEGACY_CONFIG_FILENAME_LINUX = ".hmcl.json";

    /// The legacy user settings path shared by all workspaces.
    private static final Path LEGACY_USER_SETTINGS_LOCATION = Metadata.HMCL_USER_HOME.resolve("config.json");

    /// The receipt recording the legacy user config migrated to the current user settings and state.
    private static final Path USER_SETTINGS_MIGRATION_RECEIPT_LOCATION =
            Metadata.HMCL_USER_HOME.resolve("state").resolve("user-settings.migration-receipt.json");

    /// The legacy user account file path shared by all workspaces.
    private static final Path LEGACY_USER_ACCOUNTS_LOCATION = Metadata.HMCL_USER_HOME.resolve("accounts.json");

    /// Prefix used by legacy selected account strings for shared account entries.
    private static final String LEGACY_GLOBAL_ACCOUNT_PREFIX = "$GLOBAL:";

    /// The receipt recording the legacy shared accounts migrated to the shared account metadata.
    private static final Path USER_ACCOUNTS_MIGRATION_RECEIPT_LOCATION =
            Metadata.HMCL_USER_HOME.resolve("state").resolve("user-accounts.migration-receipt.json");

    /// The receipt recording the legacy config migrated to the current per-workspace config.
    private static final Path SETTINGS_MIGRATION_RECEIPT_LOCATION =
            Metadata.HMCL_LOCAL_HOME.resolve("state").resolve("launcher-settings.migration-receipt.json");

    /// Legacy ordinal order for `BackgroundType` in legacy settings.
    private static final String[] LEGACY_BACKGROUND_IMAGE_TYPES = {
            "DEFAULT",
            "CUSTOM",
            "CLASSIC",
            "NETWORK",
            "TRANSLUCENT",
            "PAINT"
    };

    /// Legacy ordinal order for `Proxy.Type` in legacy settings.
    private static final String[] LEGACY_PROXY_TYPES = {
            "DIRECT",
            "HTTP",
            "SOCKS"
    };

    /// Prevents instantiation.
    private LegacyConfigMigrator() {
    }

    /// Returns the stable profile ID for a migrated legacy profile.
    static GameDirectoryID getLegacyProfileID(String profileName) {
        return new GameDirectoryID(UUIDs.generateV5(LEGACY_PROFILE_ID_NAMESPACE, profileName));
    }

    /// Returns the stable game settings preset ID for a migrated legacy profile.
    static GameSettingsPresetID getLegacyGameSettingsID(String profileName) {
        return new GameSettingsPresetID(UUIDs.generateV5(LEGACY_GAME_SETTINGS_ID_NAMESPACE, profileName));
    }

    /// Returns whether any legacy workspace config file is present.
    static boolean hasLegacyConfig() {
        return locateLegacyConfig() != null;
    }

    /// Looks for a legacy config file and prepares it for writing as the new config file.
    static @Nullable LegacyConfigMigration migrateLegacyConfig() throws IOException {
        @Nullable Path path = locateLegacyConfig();
        if (path == null) {
            return null;
        }
        return migrateLegacyConfigIfNeeded(path);
    }

    /// Looks for a legacy config migration receipt before preparing the given file for migration.
    ///
    /// @param path the legacy config path to read
    /// @return the prepared migration result, or `null` when the file was already migrated or is unsupported
    static @Nullable LegacyConfigMigration migrateLegacyConfigIfNeeded(Path path) throws IOException {
        if (MigrationReceipt.matches(SETTINGS_MIGRATION_RECEIPT_LOCATION, path)) {
            LOG.info("Skipping already migrated legacy config " + path);
            return null;
        }

        return migrateLegacyConfig(path);
    }

    /// Prepares a legacy config file for writing as the new config file.
    ///
    /// @param path the legacy config path to read
    /// @return the prepared migration result, or `null` when the file is not a supported legacy config
    @VisibleForTesting
    static @Nullable LegacyConfigMigration migrateLegacyConfig(Path path) throws IOException {
        Objects.requireNonNull(path);

        try {
            JsonObject jsonObject = JsonUtils.fromJsonFile(path, JsonObject.class);
            if (jsonObject == null) {
                LOG.info("Legacy config file is empty");
                return null;
            }

            // _version belongs to the legacy file format only. The current launcher settings format will use
            // a separate versioning scheme and must not depend on this numeric value.
            // Older configs may not contain _version; historically those should be treated as the last
            // pre-launcher-settings schema unless older-field probes below prove they need extra upgrades.
            int configVersion = jsonObject.remove("_version") instanceof JsonPrimitive version && version.isNumber()
                    ? version.getAsInt()
                    : 0;

            if (configVersion > LEGACY_CURRENT_CONFIG_VERSION) {
                LOG.warning("Ignoring non-legacy config file with unsupported legacy version "
                        + configVersion + ": " + path);
                return null;
            }

            if (configVersion < LEGACY_CURRENT_CONFIG_VERSION) {
                upgradeConfig(jsonObject, configVersion);
            }

            @Nullable JsonObject legacyConfigurations = jsonObject.get("configurations") instanceof JsonObject configurations
                    ? configurations.deepCopy()
                    : null;

            LauncherState launcherState = extractLauncherState(jsonObject);
            AuthlibInjectorServerList authlibInjectorServers = extractAuthlibInjectorServers(jsonObject);
            AccountMigrationResult accountMigration = Objects.requireNonNullElseGet(
                    extractAccounts(jsonObject),
                    AccountMigrationResult::empty);
            migrateLegacySelectedAccount(jsonObject, accountMigration.metadata());
            JsonElement legacyAllowAutoAgent = jsonObject.remove("allowAutoAgent");
            JsonElement legacyDisableAutoGameOptions = jsonObject.remove("disableAutoGameOptions");
            migrateLegacyBackgroundImageType(jsonObject);
            migrateLegacyProxyType(jsonObject);
            migrateLegacyDownloadSources(jsonObject);
            renameMember(jsonObject, "commonDirType", "commonDirectoryType");
            renameMember(jsonObject, "commonpath", "commonDirectory");
            migrateLegacyLanguage(jsonObject);
            renameMember(jsonObject, "theme", "themeColor");
            renameMember(jsonObject, "fontFamily", "logFontFamily");
            renameMember(jsonObject, "fontSize", "logFontSize");
            renameMember(jsonObject, "bgpath", "backgroundImage");
            renameMember(jsonObject, "bgurl", "backgroundImageUrl");
            renameMember(jsonObject, "bgpaint", "backgroundPaint");
            migrateBackgroundOpacity(jsonObject);
            renameMember(jsonObject, "proxyUserName", "proxyUser");
            migrateLegacySelectedVersions(jsonObject);
            @Nullable GameDirectories migratedGameDirectories = extractGameDirectoriesFromConfigJson(jsonObject);
            GameDirectories gameDirectories = migratedGameDirectories != null
                    ? migratedGameDirectories
                    : new GameDirectories();
            migrateLegacySelectedGameDirectory(jsonObject);

            LauncherSettings deserialized = LauncherSettings.fromJson(jsonObject);
            if (deserialized == null) {
                return null;
            }

            GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();
            migrateLegacyPresetSettings(gameDirectories, gameSettingsPresets, legacyConfigurations);
            migrateLegacyAllowAutoAgent(deserialized, gameSettingsPresets, legacyAllowAutoAgent);
            migrateLegacyDisableAutoGameOptions(deserialized, gameSettingsPresets, legacyDisableAutoGameOptions);
            DetachedSettings detachedSettings = new DetachedSettings(gameDirectories, gameSettingsPresets,
                    launcherState, authlibInjectorServers, accountMigration);
            return new LegacyConfigMigration(path, deserialized, detachedSettings);
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy config file: " + path, e);
            return null;
        }
    }

    /// Records that the legacy config migration has been applied.
    ///
    /// @param migration the completed legacy config migration
    static void completeLegacyConfigMigration(LegacyConfigMigration migration) {
        MigrationReceipt.save(SETTINGS_MIGRATION_RECEIPT_LOCATION, migration.path());
    }

    /// Migrates user settings and state from the legacy global config file.
    ///
    /// @return the migrated user settings and state, or `null` when no legacy user settings can be used
    static @Nullable UserSettingsMigrationResult migrateLegacyUserSettings() throws IOException {
        if (!Files.exists(LEGACY_USER_SETTINGS_LOCATION)) {
            return null;
        }
        if (MigrationReceipt.matches(USER_SETTINGS_MIGRATION_RECEIPT_LOCATION, LEGACY_USER_SETTINGS_LOCATION)) {
            LOG.info("Skipping already migrated user settings " + LEGACY_USER_SETTINGS_LOCATION);
            return null;
        }

        try {
            String content = Files.readString(LEGACY_USER_SETTINGS_LOCATION);
            UserSettings deserialized = UserSettings.fromJson(content);
            UserState userState = UserState.fromJson(content);
            if (deserialized == null || userState == null) {
                LOG.info("Legacy user settings file is empty: " + LEGACY_USER_SETTINGS_LOCATION);
                return null;
            }

            LOG.info("Migrating user settings from " + LEGACY_USER_SETTINGS_LOCATION);
            return new UserSettingsMigrationResult(LEGACY_USER_SETTINGS_LOCATION, deserialized, userState);
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy user settings: " + LEGACY_USER_SETTINGS_LOCATION, e);
            return null;
        }
    }

    /// Records that the given legacy user settings migration result has been applied.
    static void completeLegacyUserSettingsMigration(UserSettingsMigrationResult migrationResult) {
        MigrationReceipt.save(USER_SETTINGS_MIGRATION_RECEIPT_LOCATION, migrationResult.path());
    }

    /// Migrates account records from the legacy shared account file.
    ///
    /// @return the migrated shared account metadata store, or `null` when no legacy shared accounts can be used
    static @Nullable UserAccountsMigrationResult migrateLegacyUserAccounts() {
        if (!Files.exists(LEGACY_USER_ACCOUNTS_LOCATION)) {
            return null;
        }
        if (MigrationReceipt.matches(USER_ACCOUNTS_MIGRATION_RECEIPT_LOCATION, LEGACY_USER_ACCOUNTS_LOCATION)) {
            LOG.info("Skipping already migrated user accounts " + LEGACY_USER_ACCOUNTS_LOCATION);
            return null;
        }

        try {
            List<JsonObject> accounts = JsonUtils.fromJsonFile(
                    LauncherSettings.SETTINGS_GSON,
                    LEGACY_USER_ACCOUNTS_LOCATION,
                    JsonUtils.listTypeOf(JsonObject.class)
            );
            if (accounts == null) {
                return null;
            }

            LOG.info("Migrating user accounts from " + LEGACY_USER_ACCOUNTS_LOCATION);
            return new UserAccountsMigrationResult(
                    LEGACY_USER_ACCOUNTS_LOCATION,
                    migrateLegacyAccounts(accounts, true));
        } catch (Throwable e) {
            LOG.warning("Failed to load legacy user accounts", e);
            return null;
        }
    }

    /// Records that the given legacy shared account migration result has been applied.
    static void completeLegacyUserAccountsMigration(UserAccountsMigrationResult migrationResult) {
        MigrationReceipt.save(USER_ACCOUNTS_MIGRATION_RECEIPT_LOCATION, migrationResult.path());
    }

    /// Extracts launcher state from a legacy config JSON object and removes those members.
    static LauncherState extractLauncherState(JsonObject json) {
        Rectangle2D screen = Screen.getPrimary().getBounds();
        return extractLauncherState(json, screen.getWidth(), screen.getHeight());
    }

    /// Extracts launcher state from a legacy config JSON object using explicit screen bounds.
    @VisibleForTesting
    static LauncherState extractLauncherState(JsonObject json, double screenWidth, double screenHeight) {
        Objects.requireNonNull(json);

        JsonObject state = new JsonObject();
        state.add(JsonSchema.PROPERTY_SCHEMA, JsonUtils.GSON.toJsonTree(LauncherState.CURRENT_SCHEMA, JsonSchema.class));
        moveMember(json, state, "x");
        moveMember(json, state, "y");
        moveMember(json, state, "width");
        moveMember(json, state, "height");
        migrateLegacyWindowContentPosition(state, "x", screenWidth);
        migrateLegacyWindowContentPosition(state, "y", screenHeight);
        migrateLegacyWindowContentSize(state, "width");
        migrateLegacyWindowContentSize(state, "height");
        moveMember(json, state, "promptedVersion");
        moveMember(json, state, "shownTips");

        LauncherState result = JsonUtils.GSON.fromJson(state, LauncherState.class);
        return result != null ? result : new LauncherState();
    }

    /// Converts one legacy launcher window outer position into a normalized content position.
    private static void migrateLegacyWindowContentPosition(JsonObject state, String name, double screenSize) {
        JsonElement element = state.get(name);
        if (!(element instanceof JsonPrimitive primitive)
                || !primitive.isNumber()
                || !Double.isFinite(screenSize)
                || screenSize <= 0.0) {
            return;
        }

        double position = primitive.getAsDouble() + LEGACY_CUSTOM_DECORATION_SHADOW_SIZE / screenSize;
        if (Double.isFinite(position)) {
            state.addProperty(name, position);
        }
    }

    /// Converts one legacy launcher window outer size into a content size.
    private static void migrateLegacyWindowContentSize(JsonObject state, String name) {
        JsonElement element = state.get(name);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return;
        }

        double size = primitive.getAsDouble() - LEGACY_CUSTOM_DECORATION_SHADOW_EXTENT;
        if (Double.isFinite(size)) {
            state.addProperty(name, Math.max(0.0, size));
        }
    }

    /// Extracts authlib-injector servers from a legacy config JSON object and removes those members.
    static AuthlibInjectorServerList extractAuthlibInjectorServers(JsonObject json) {
        Objects.requireNonNull(json);

        JsonObject servers = new JsonObject();
        servers.add(JsonSchema.PROPERTY_SCHEMA,
                JsonUtils.GSON.toJsonTree(AuthlibInjectorServerList.CURRENT_SCHEMA, JsonSchema.class));
        JsonElement authlibInjectorServers = json.remove("authlibInjectorServers");
        if (authlibInjectorServers != null) {
            servers.add("servers", authlibInjectorServers);
        }
        JsonElement addedLittleSkin = json.remove("addedLittleSkin");
        boolean shouldAddLittleSkin = !(addedLittleSkin instanceof JsonPrimitive primitive
                && primitive.isBoolean()
                && primitive.getAsBoolean());

        AuthlibInjectorServerList result = JsonUtils.GSON.fromJson(servers, AuthlibInjectorServerList.class);
        if (result == null) {
            result = new AuthlibInjectorServerList();
        }
        if (shouldAddLittleSkin) {
            result.addLittleSkinIfAbsent();
        }
        return result;
    }

    /// Extracts account metadata and private data from a config JSON object and removes the legacy member.
    static @Nullable AccountMigrationResult extractAccounts(JsonObject json) {
        Objects.requireNonNull(json);

        JsonElement accounts = json.remove("accounts");
        if (accounts == null) {
            return null;
        }

        if (accounts instanceof JsonArray array) {
            List<JsonObject> accountRecords = new ArrayList<>(array.size());
            for (JsonElement account : array) {
                if (account instanceof JsonObject object) {
                    accountRecords.add(object);
                }
            }
            return migrateLegacyAccounts(accountRecords, false, true);
        }

        return AccountMigrationResult.empty();
    }

    /// Creates the current account stores from legacy serialized account records.
    ///
    /// @param accounts legacy account records
    /// @return current account metadata and private data stores
    @VisibleForTesting
    static AccountMigrationResult migrateLegacyAccounts(List<JsonObject> accounts) {
        return migrateLegacyAccounts(accounts, false);
    }

    /// Creates the current account stores from legacy serialized account records.
    ///
    /// @param accounts legacy account records
    /// @param userStorage whether the legacy entries come from the shared user account file
    /// @return current account metadata and private data stores
    @VisibleForTesting
    static AccountMigrationResult migrateLegacyAccounts(List<JsonObject> accounts, boolean userStorage) {
        return migrateLegacyAccounts(accounts, userStorage, false);
    }

    /// Creates the current account stores from legacy serialized account records.
    ///
    /// @param accounts legacy account records
    /// @param userStorage whether the legacy entries come from the shared user account file
    /// @param keepSelectedMarker whether to keep the legacy selected marker for selected-account migration
    /// @return current account metadata and private data stores
    private static AccountMigrationResult migrateLegacyAccounts(
            List<JsonObject> accounts,
            boolean userStorage,
            boolean keepSelectedMarker) {
        List<JsonObject> metadataAccounts = new ArrayList<>(accounts.size());
        AccountPrivateData privateData = new AccountPrivateData();
        for (JsonObject account : accounts) {
            MigratedLegacyAccount migrated = migrateLegacyAccountRecord(account);
            JsonObject metadata = migrated.metadata();
            if (keepSelectedMarker) {
                copyMember(account, metadata, "selected");
            }
            AccountID accountID = createLegacyAccountID(metadata, userStorage);
            metadata.addProperty(Account.PROPERTY_ACCOUNT_ID, accountID.toString());

            if (!migrated.privateData().isEmpty()) {
                privateData.putPrivateData(accountID, migrated.privateData());
            }
            metadataAccounts.add(metadata);
        }

        return new AccountMigrationResult(AccountMetadataStore.fromRecords(metadataAccounts), privateData);
    }

    /// Ensures account IDs in one account metadata store do not collide with IDs already used by earlier stores.
    ///
    /// @param accountMetadata the account metadata store to update
    /// @param usedAccountIDs canonical account ID strings already reserved by earlier stores
    /// @param userStorage whether the account metadata store is shared across workspaces
    static boolean assignAccountIDs(AccountMetadataStore accountMetadata, Set<String> usedAccountIDs, boolean userStorage) {
        List<JsonObject> updatedAccounts = new ArrayList<>(accountMetadata.getAccounts().size());
        boolean changed = false;
        for (JsonObject account : accountMetadata.getAccounts()) {
            JsonObject updatedAccount = account.deepCopy();
            @Nullable AccountID existing = parseAccountID(JsonUtils.getString(account, Account.PROPERTY_ACCOUNT_ID));
            if (existing != null && usedAccountIDs.add(existing.toString())) {
                updatedAccount.addProperty(Account.PROPERTY_ACCOUNT_ID, existing.toString());
                changed |= !Objects.equals(JsonUtils.getString(account, Account.PROPERTY_ACCOUNT_ID), existing.toString());
                updatedAccounts.add(updatedAccount);
                continue;
            }

            AccountID accountID = createLegacyAccountID(account, userStorage);
            updatedAccount.addProperty(Account.PROPERTY_ACCOUNT_ID, accountID.toString());
            usedAccountIDs.add(accountID.toString());
            changed |= !Objects.equals(JsonUtils.getString(account, Account.PROPERTY_ACCOUNT_ID), accountID.toString());
            updatedAccounts.add(updatedAccount);
        }

        if (changed) {
            accountMetadata.getAccounts().setAll(updatedAccounts);
        }
        return changed;
    }

    /// Parses an account ID, returning `null` for missing or malformed values.
    private static @Nullable AccountID parseAccountID(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            return AccountID.parse(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// Creates a stable account ID from the legacy selected-account identifier represented by one account.
    private static AccountID createLegacyAccountID(JsonObject account, boolean userStorage) {
        String prefix = userStorage ? LEGACY_GLOBAL_ACCOUNT_PREFIX : "";
        @Nullable String legacyIdentifier = getLegacyAccountIdentifier(account);
        if (legacyIdentifier != null) {
            return new AccountID(UUIDs.generateV5(LEGACY_ACCOUNT_ID_NAMESPACE, prefix + legacyIdentifier));
        }

        @Nullable String type = JsonUtils.getString(account, "type");
        String uuidName = prefix + (type == null ? "unknown:" + JsonUtils.GSON.toJson(account) : switch (type) {
            case "offline" -> {
                String profileName = JsonUtils.getString(account, "profileName", "");
                String profileID = JsonUtils.getString(account, "profileID", "");
                yield "offline:" + profileName + ":" + profileID;
            }
            case "microsoft" -> {
                String profileID = JsonUtils.getString(account, "profileID", "");
                yield "microsoft:" + profileID;
            }
            case "yggdrasil" -> {
                String loginName = JsonUtils.getString(account, "loginName", "");
                String profileID = JsonUtils.getString(account, "profileID", "");
                yield "yggdrasil:" + loginName + ":" + profileID;
            }
            case "authlibInjector" -> {
                String serverBaseURL = JsonUtils.getString(account, "serverBaseURL", "");
                String loginName = JsonUtils.getString(account, "loginName", "");
                String profileID = JsonUtils.getString(account, "profileID", "");
                yield "authlibInjector:" + serverBaseURL + ":" + loginName + ":" + profileID;
            }
            default -> type + ":" + JsonUtils.GSON.toJson(account);
        });
        return new AccountID(UUIDs.generateV5(LEGACY_ACCOUNT_ID_NAMESPACE, uuidName));
    }

    /// Creates current metadata and private data records from one legacy account entry.
    private static MigratedLegacyAccount migrateLegacyAccountRecord(JsonObject account) {
        @Nullable String type = JsonUtils.getString(account, "type");
        if (type == null) {
            return new MigratedLegacyAccount(account.deepCopy(), new JsonObject());
        }

        return switch (type) {
            case "offline" -> {
                JsonObject metadata = createLegacyAccountMetadata("offline");
                JsonObject privateData = new JsonObject();
                addStringMember(metadata, "profileName", JsonUtils.getString(account, "username"));
                addNormalizedUUIDMember(metadata, "profileID", JsonUtils.getString(account, "uuid"));
                if (!metadata.has("profileID")) {
                    @Nullable String profileName = JsonUtils.getString(metadata, "profileName");
                    if (profileName != null) {
                        metadata.addProperty("profileID", OfflineAccountFactory.getUUIDFromUserName(profileName).toString());
                    }
                }
                copyMember(account, metadata, "skin");
                yield new MigratedLegacyAccount(metadata, privateData);
            }
            case "microsoft" -> {
                JsonObject metadata = createLegacyAccountMetadata("microsoft");
                JsonObject privateData = new JsonObject();
                addNormalizedUUIDMember(metadata, "profileID", JsonUtils.getString(account, "uuid"));
                addStringMember(privateData, "profileName", JsonUtils.getString(account, "displayName"));
                copyMember(account, privateData, "tokenType");
                copyMember(account, privateData, "accessToken");
                copyMember(account, privateData, "refreshToken");
                copyMember(account, privateData, "notAfter");
                copyMember(account, privateData, "userid");
                yield new MigratedLegacyAccount(metadata, privateData);
            }
            case "yggdrasil" -> {
                JsonObject metadata = createLegacyAccountMetadata("yggdrasil");
                JsonObject privateData = new JsonObject();
                addStringMember(metadata, "loginName", JsonUtils.getString(account, "username"));
                addNormalizedUUIDMember(metadata, "profileID", JsonUtils.getString(account, "uuid"));
                addStringMember(privateData, "profileName", JsonUtils.getString(account, "displayName"));
                copyMember(account, privateData, "clientToken");
                copyMember(account, privateData, "accessToken");
                copyMember(account, privateData, "userProperties");
                yield new MigratedLegacyAccount(metadata, privateData);
            }
            case "authlibInjector" -> {
                JsonObject metadata = createLegacyAccountMetadata("authlibInjector");
                JsonObject privateData = new JsonObject();
                copyMember(account, metadata, "serverBaseURL");
                addStringMember(metadata, "loginName", JsonUtils.getString(account, "username"));
                addNormalizedUUIDMember(metadata, "profileID", JsonUtils.getString(account, "uuid"));
                addStringMember(privateData, "profileName", JsonUtils.getString(account, "displayName"));
                copyMember(account, privateData, "clientToken");
                copyMember(account, privateData, "accessToken");
                copyMember(account, privateData, "userProperties");
                copyMember(account, privateData, "profileProperties");
                yield new MigratedLegacyAccount(metadata, privateData);
            }
            default -> {
                JsonObject metadata = account.deepCopy();
                yield new MigratedLegacyAccount(metadata, new JsonObject());
            }
        };
    }

    /// Creates current metadata and private data for one legacy offline account.
    private static MigratedLegacyAccount migrateLegacyOfflineAccount(JsonObject account) {
        JsonObject metadata = createLegacyAccountMetadata("offline");
        JsonObject privateData = new JsonObject();
        addStringMember(metadata, "profileName", JsonUtils.getString(account, "username"));
        addNormalizedUUIDMember(metadata, "profileID", JsonUtils.getString(account, "uuid"));
        if (!metadata.has("profileID")) {
            @Nullable String profileName = JsonUtils.getString(metadata, "profileName");
            if (profileName != null) {
                metadata.addProperty("profileID", OfflineAccountFactory.getUUIDFromUserName(profileName).toString());
            }
        }
        copyMember(account, metadata, "skin");
        return new MigratedLegacyAccount(metadata, privateData);
    }

    /// Creates a metadata object with the account type.
    private static JsonObject createLegacyAccountMetadata(String type) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("type", type);
        return metadata;
    }

    /// Adds a string member when the value is present.
    private static void addStringMember(JsonObject target, String name, @Nullable String value) {
        if (value != null) {
            target.addProperty(name, value);
        }
    }

    /// Adds a normalized UUID string member when the value is present and valid.
    private static void addNormalizedUUIDMember(JsonObject target, String name, @Nullable String value) {
        if (value == null) {
            return;
        }

        @Nullable String normalized = normalizeUUID(value);
        if (normalized != null) {
            target.addProperty(name, normalized);
        }
    }

    /// Returns the current stored UUID string form, or `null` when the value is malformed.
    private static @Nullable String normalizeUUID(String uuid) {
        try {
            return UUIDs.parse(uuid).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// Migrates the legacy selected account string into a selected account ID.
    static boolean migrateLegacySelectedAccount(JsonObject json, AccountMetadataStore localAccounts) {
        Objects.requireNonNull(json);
        Objects.requireNonNull(localAccounts);

        JsonElement selectedAccount = json.get("selectedAccount");
        @Nullable AccountID selectedMarkerAccountID = null;
        boolean changed = false;
        Set<String> usedAccountIDs = new HashSet<>();
        assignAccountIDs(localAccounts, usedAccountIDs, false);
        for (JsonObject account : localAccounts.getAccounts()) {
            JsonElement selectedMarker = account.remove("selected");
            if (selectedMarker != null) {
                changed = true;
            }
            if (JsonUtils.getBoolean(selectedMarker, false) && selectedMarkerAccountID == null) {
                selectedMarkerAccountID = getSelectedAccountID(account);
            }
        }

        if (selectedAccount == null) {
            if (selectedMarkerAccountID != null) {
                json.addProperty("selectedAccount", selectedMarkerAccountID.toString());
                return true;
            }
            return changed;
        }

        @Nullable String legacyIdentifier = JsonUtils.getString(selectedAccount);
        if (StringUtils.isBlank(legacyIdentifier)) {
            json.remove("selectedAccount");
            return true;
        }

        @Nullable AccountID accountID = findLegacySelectedAccountID(legacyIdentifier, localAccounts, false);
        if (accountID == null) {
            AccountMetadataStore userAccounts = loadLegacyUserAccountMetadataStoreForSelectedAccount(usedAccountIDs);
            if (userAccounts != null) {
                accountID = findLegacySelectedAccountID(legacyIdentifier, userAccounts, true);
            }
        }

        if (accountID != null) {
            json.addProperty("selectedAccount", accountID.toString());
        } else {
            json.remove("selectedAccount");
        }
        return true;
    }

    /// Loads legacy user account records only for resolving a selected account ID during migration.
    private static @Nullable AccountMetadataStore loadLegacyUserAccountMetadataStoreForSelectedAccount(Set<String> usedAccountIDs) {
        if (!Files.exists(LEGACY_USER_ACCOUNTS_LOCATION)) {
            return null;
        }

        try {
            List<JsonObject> accounts = JsonUtils.fromJsonFile(
                    LEGACY_USER_ACCOUNTS_LOCATION,
                    JsonUtils.listTypeOf(JsonObject.class));
            if (accounts == null) {
                return null;
            }
            AccountMetadataStore accountMetadata = migrateLegacyAccounts(accounts, true).metadata();
            assignAccountIDs(accountMetadata, usedAccountIDs, true);
            return accountMetadata;
        } catch (Exception e) {
            LOG.warning("Failed to load legacy user accounts for selected account migration", e);
            return null;
        }
    }

    /// Finds the selected account ID matching a legacy selected account string.
    private static @Nullable AccountID findLegacySelectedAccountID(
            String legacyIdentifier,
            AccountMetadataStore accounts,
            boolean userStorage) {
        String identifier = legacyIdentifier;
        boolean selectedUserStorage = false;
        if (identifier.startsWith(LEGACY_GLOBAL_ACCOUNT_PREFIX)) {
            selectedUserStorage = true;
            identifier = identifier.substring(LEGACY_GLOBAL_ACCOUNT_PREFIX.length());
        }
        if (selectedUserStorage != userStorage) {
            return null;
        }

        for (JsonObject account : accounts.getAccounts()) {
            if (matchesLegacySelectedAccountIdentifier(identifier, account)) {
                return getSelectedAccountID(account);
            }
        }
        return null;
    }

    /// Returns whether a serialized account entry matches a legacy selected account string.
    private static boolean matchesLegacySelectedAccountIdentifier(String identifier, JsonObject metadata) {
        return Objects.equals(identifier, getLegacyAccountIdentifier(metadata));
    }

    /// Returns the selected account ID for a serialized account entry.
    private static @Nullable AccountID getSelectedAccountID(JsonObject metadata) {
        return Account.getAccountID(metadata);
    }

    /// Returns the legacy selected-account string represented by a migrated account metadata entry.
    private static @Nullable String getLegacyAccountIdentifier(JsonObject metadata) {
        @Nullable String type = JsonUtils.getString(metadata, "type");
        if (type == null) {
            return null;
        }

        return switch (type) {
            case "offline" -> {
                @Nullable String profileName = JsonUtils.getString(metadata, "profileName");
                yield profileName != null ? profileName + ":" + profileName : null;
            }
            case "microsoft" -> {
                @Nullable String profileID = JsonUtils.getString(metadata, "profileID");
                @Nullable String formattedProfileID = profileID != null ? formatLegacyUUID(profileID) : null;
                yield formattedProfileID != null ? "microsoft:" + formattedProfileID : null;
            }
            case "yggdrasil" -> {
                @Nullable String loginName = JsonUtils.getString(metadata, "loginName");
                @Nullable String profileID = JsonUtils.getString(metadata, "profileID");
                @Nullable String formattedProfileID = profileID != null ? formatLegacyUUID(profileID) : null;
                yield loginName != null && formattedProfileID != null
                        ? loginName + ":" + formattedProfileID
                        : null;
            }
            case "authlibInjector" -> {
                @Nullable String serverBaseURL = JsonUtils.getString(metadata, "serverBaseURL");
                @Nullable String loginName = JsonUtils.getString(metadata, "loginName");
                @Nullable String profileID = JsonUtils.getString(metadata, "profileID");
                @Nullable String formattedProfileID = profileID != null ? formatLegacyUUID(profileID) : null;
                yield serverBaseURL != null && loginName != null && formattedProfileID != null
                        ? serverBaseURL + ":" + loginName + ":" + formattedProfileID
                        : null;
            }
            default -> null;
        };
    }

    /// Formats a stored UUID the same way legacy account identifiers did.
    private static @Nullable String formatLegacyUUID(String uuid) {
        return normalizeUUID(uuid);
    }

    /// Moves one JSON member from the source object to the target object.
    private static void moveMember(JsonObject source, JsonObject target, String name) {
        JsonElement element = source.remove(name);
        if (element != null) {
            target.add(name, element);
        }
    }

    /// Copies one JSON member from the source object to the target object.
    private static void copyMember(JsonObject source, JsonObject target, String name) {
        JsonElement element = source.get(name);
        if (element != null && !element.isJsonNull()) {
            target.add(name, element.deepCopy());
        }
    }

    /// Renames one JSON member to the current name.
    private static void renameMember(JsonObject json, String legacyName, String currentName) {
        JsonElement legacyValue = json.remove(legacyName);
        if (legacyValue != null) {
            json.add(currentName, legacyValue);
        }
    }

    /// Migrates the legacy background opacity percentage into the current opacity value.
    private static void migrateBackgroundOpacity(JsonObject json) {
        JsonElement legacyValue = json.remove("bgImageOpacity");
        if (legacyValue == null) {
            return;
        }

        if (json.has("backgroundOpacity")) {
            return;
        }

        @Nullable Double opacityPercent = JsonUtils.getDouble(legacyValue);
        if (opacityPercent == null) {
            return;
        }

        double opacity = opacityPercent / 100.;
        json.addProperty("backgroundOpacity", Math.max(0., Math.min(opacity, 1.)));
    }

    /// Migrates the legacy `localization` field into the current `language` field.
    static void migrateLegacyLanguage(JsonObject json) {
        Objects.requireNonNull(json);

        JsonElement legacyLanguage = json.remove("localization");
        if (!(legacyLanguage instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return;
        }

        json.addProperty("language", switch (primitive.getAsString()) {
            case "zh_CN" -> "zh-Hans";
            case "zh" -> "zh-Hant";
            default -> primitive.getAsString();
        });
    }

    /// Migrates the legacy background image type into the current enum values.
    @VisibleForTesting
    static void migrateLegacyBackgroundImageType(JsonObject json) {
        JsonElement legacyValue = json.get("backgroundType");
        @Nullable Integer ordinal = JsonUtils.getInteger(legacyValue);
        if (ordinal != null && ordinal >= 0 && ordinal < LEGACY_BACKGROUND_IMAGE_TYPES.length) {
            json.addProperty("backgroundType", LEGACY_BACKGROUND_IMAGE_TYPES[ordinal]);
        }

        if (Objects.equals(JsonUtils.getString(json, "backgroundType"), "TRANSLUCENT")) {
            json.addProperty("backgroundType", BackgroundType.PAINT.name());
            json.addProperty("backgroundPaint", "#ffffff");
            json.addProperty("backgroundOpacity", 0.5);
            json.remove("bgpaint");
        }
    }

    /// Migrates the legacy proxy type ordinal into the current enum value.
    @VisibleForTesting
    static void migrateLegacyProxyType(JsonObject json) {
        JsonElement legacyValue = json.get("proxyType");
        @Nullable Integer ordinal = JsonUtils.getInteger(legacyValue);
        if (ordinal == null || ordinal < 0 || ordinal >= LEGACY_PROXY_TYPES.length) {
            return;
        }

        json.addProperty("proxyType", LEGACY_PROXY_TYPES[ordinal]);
    }

    /// Migrates legacy download source fields into `versionListSource` and `fileDownloadSource`.
    static void migrateLegacyDownloadSources(JsonObject json) {
        Objects.requireNonNull(json);

        JsonElement autoChooseDownloadType = json.remove("autoChooseDownloadType");
        JsonElement legacyDownloadType = json.remove("downloadType");
        JsonElement legacyVersionListSource = json.remove("versionListSource");
        if (autoChooseDownloadType == null && legacyDownloadType == null && legacyVersionListSource == null) {
            return;
        }

        DownloadSource source = JsonUtils.getBoolean(autoChooseDownloadType, true)
                ? parseLegacyDownloadSource(legacyVersionListSource, DownloadSource.DEFAULT)
                : parseLegacyDownloadSource(legacyDownloadType, DownloadSource.DEFAULT);

        json.addProperty("versionListSource", source.name());
        json.addProperty("fileDownloadSource", source.name());
    }

    /// Parses an old download source identifier.
    private static DownloadSource parseLegacyDownloadSource(@Nullable JsonElement element, DownloadSource defaultValue) {
        @Nullable String value = JsonUtils.getString(element);
        if (value == null) {
            return defaultValue;
        }

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "default", "balanced" -> DownloadSource.DEFAULT;
            case "official", "mojang" -> DownloadSource.OFFICIAL;
            case "mirror", "bmclapi" -> DownloadSource.MIRROR;
            default -> {
                try {
                    yield DownloadSource.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    yield defaultValue;
                }
            }
        };
    }

    /// Migrates the legacy workspace-wide automatic Java agent permission into game setting presets.
    static void migrateLegacyAllowAutoAgent(
            LauncherSettings launcherSettings,
            GameSettingsPresets gameSettingsPresets,
            @Nullable JsonElement legacyAllowAutoAgent) {
        Objects.requireNonNull(launcherSettings);
        Objects.requireNonNull(gameSettingsPresets);

        if (!(legacyAllowAutoAgent instanceof JsonPrimitive primitive)
                || !primitive.isBoolean()
                || !primitive.getAsBoolean()) {
            return;
        }

        ensureDefaultGameSettingsPreset(launcherSettings, gameSettingsPresets);
        for (GameSettings.Preset preset : gameSettingsPresets.getPresets()) {
            preset.allowAutoAgentProperty().setValue(true);
        }
    }

    /// Migrates the legacy workspace-wide automatic game options switch into game setting presets.
    static void migrateLegacyDisableAutoGameOptions(
            LauncherSettings launcherSettings,
            GameSettingsPresets gameSettingsPresets,
            @Nullable JsonElement legacyDisableAutoGameOptions) {
        Objects.requireNonNull(launcherSettings);
        Objects.requireNonNull(gameSettingsPresets);

        if (!(legacyDisableAutoGameOptions instanceof JsonPrimitive primitive)
                || !primitive.isBoolean()
                || !primitive.getAsBoolean()) {
            return;
        }

        ensureDefaultGameSettingsPreset(launcherSettings, gameSettingsPresets);
        for (GameSettings.Preset preset : gameSettingsPresets.getPresets()) {
            preset.disableAutoGameOptionsProperty().setValue(true);
        }
    }

    /// Ensures there is a default preset to receive migrated workspace-wide game settings.
    private static void ensureDefaultGameSettingsPreset(LauncherSettings launcherSettings, GameSettingsPresets gameSettingsPresets) {
        if (gameSettingsPresets.getPresets().isEmpty()) {
            GameSettings.Preset preset = new GameSettings.Preset(gameSettingsPresets.newPresetId());
            preset.autoNameNumberProperty().setValue(1);
            gameSettingsPresets.getPresets().add(preset);
            launcherSettings.defaultGameSettingsPresetProperty().set(preset.idProperty().getValue());
        }
    }

    /// Extracts game directory data from a legacy config JSON object and removes the legacy members.
    ///
    /// This supports migrating the legacy `configurations` map into `config/game-directories.json`.
    ///
    /// @param json the legacy config JSON object
    /// @return the extracted game directory store, or `null` when the object contains no game directory data
    static @Nullable GameDirectories extractGameDirectoriesFromConfigJson(JsonObject json) {
        Objects.requireNonNull(json);

        @Nullable JsonElement configurationsElement = json.remove("configurations");

        @Nullable JsonArray profiles = null;
        if (configurationsElement instanceof JsonObject configurations) {
            profiles = migrateConfigurationMap(configurations);
        }

        if (profiles == null) {
            return null;
        }

        JsonObject object = new JsonObject();
        object.add(JsonSchema.PROPERTY_SCHEMA, JsonUtils.GSON.toJsonTree(GameDirectories.CURRENT_SCHEMA, JsonSchema.class));
        object.add("directories", profiles);

        return JsonUtils.GSON.fromJson(object, GameDirectories.class);
    }

    /// Converts a legacy profile map into game directory JSON.
    private static JsonArray migrateConfigurationMap(JsonObject configurations) {
        JsonArray result = new JsonArray();
        for (Map.Entry<String, JsonElement> entry : configurations.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject profile)) {
                continue;
            }

            JsonObject migrated = profile.deepCopy();
            JsonElement legacyGameDir = migrated.remove("gameDir");
            if (legacyGameDir != null) {
                migrated.add("path", legacyGameDir);
            } else if (!migrated.has("path")) {
                migrated.addProperty("path", "");
            }
            String name = entry.getKey();
            if (isBuiltInProfileName(name)) {
                migrated.remove("name");
            } else {
                migrated.addProperty("name", name);
            }
            migrated.addProperty("id", getLegacyProfileID(name).toString());
            if (profile.get("global") instanceof JsonObject) {
                migrated.addProperty("legacyGameSettings", getLegacyGameSettingsID(name).toString());
            }
            result.add(migrated);
        }
        return result;
    }

    /// Returns whether the given legacy profile name belongs to a built-in profile.
    private static boolean isBuiltInProfileName(@Nullable String name) {
        return LEGACY_DEFAULT_PROFILE.equals(name) || LEGACY_HOME_PROFILE.equals(name);
    }

    /// Finds a legacy config file with the same precedence as old HMCL versions.
    private static @Nullable Path locateLegacyConfig() {
        // Keep this order aligned with old HMCL versions so the same legacy file wins during migration.
        Path defaultConfigFile = Metadata.HMCL_LOCAL_HOME.resolve(LEGACY_CONFIG_FILENAME);
        if (Files.isRegularFile(defaultConfigFile)) {
            return defaultConfigFile;
        }

        try {
            @Nullable Path jarPath = JarUtils.thisJarPath();
            if (jarPath != null && Files.isRegularFile(jarPath) && Files.isWritable(jarPath)) {
                Path jarDirectory = jarPath.getParent();

                Path config = jarDirectory.resolve(LEGACY_CONFIG_FILENAME);
                if (Files.isRegularFile(config)) {
                    return config;
                }

                Path dotConfig = jarDirectory.resolve(LEGACY_CONFIG_FILENAME_LINUX);
                if (Files.isRegularFile(dotConfig)) {
                    return dotConfig;
                }
            }
        } catch (Throwable ignore) {
        }

        Path config = Paths.get(LEGACY_CONFIG_FILENAME);
        if (Files.isRegularFile(config)) {
            return config;
        }

        Path dotConfig = Paths.get(LEGACY_CONFIG_FILENAME_LINUX);
        if (Files.isRegularFile(dotConfig)) {
            return dotConfig;
        }

        return null;
    }

    /// Upgrades old config fields in the raw JSON object to the current schema.
    private static void upgradeConfig(JsonObject jsonObject, int configVersion) {
        LOG.info(String.format("Updating legacy configuration from %d to %d.", configVersion, LEGACY_CURRENT_CONFIG_VERSION));
        if (configVersion < 1) {
            // Upgrade configuration of HMCL 2.x: Convert OfflineAccounts whose stored uuid is important.
            if (jsonObject.get("auth") instanceof JsonObject auth
                    && auth.get("offline") instanceof JsonObject offline
                    && offline.get("uuidMap") instanceof JsonObject uuidMap) {

                String selected = jsonObject.has("selectedAccount")
                        ? null
                        : JsonUtils.getString(offline, "IAuthenticator_UserName", null);
                JsonArray accounts = new JsonArray();
                for (Map.Entry<String, JsonElement> entry : uuidMap.entrySet()) {
                    JsonObject storage = new JsonObject();
                    storage.addProperty("type", "offline");
                    storage.addProperty("username", entry.getKey());
                    storage.add("uuid", entry.getValue());
                    if (entry.getKey().equals(selected)) {
                        storage.addProperty("selected", true);
                    }
                    accounts.add(storage);
                }
                jsonObject.add("accounts", accounts);
            }


            // Upgrade configuration of HMCL earlier than 3.1.70.
            if (!jsonObject.has("commonDirType")) {
                String commonDirectory = JsonUtils.getString(jsonObject, "commonpath", LauncherSettings.getDefaultCommonDirectory());
                jsonObject.addProperty("commonDirectoryType", commonDirectory.equals(LauncherSettings.getDefaultCommonDirectory())
                        ? EnumCommonDirectory.DEFAULT.name()
                        : EnumCommonDirectory.CUSTOM.name());
            }
            String backgroundImage = JsonUtils.getString(jsonObject, "bgpath", "");
            jsonObject.addProperty("backgroundType", StringUtils.isNotBlank(backgroundImage)
                    ? BackgroundType.CUSTOM.name()
                    : BackgroundType.DEFAULT.name());
            jsonObject.addProperty("hasProxy", StringUtils.isNotBlank(JsonUtils.getString(jsonObject, "proxyHost", "")));
            jsonObject.addProperty("hasProxyAuth", StringUtils.isNotBlank(JsonUtils.getString(jsonObject, "proxyUserName", "")));

            JsonElement legacyDownloadType = jsonObject.get("downloadtype");
            if (legacyDownloadType != null && legacyDownloadType.isJsonPrimitive()
                    && legacyDownloadType.getAsJsonPrimitive().isNumber()) {
                int id = legacyDownloadType.getAsInt();
                if (id == 0) {
                    jsonObject.addProperty("downloadType", "mojang");
                } else if (id == 1) {
                    jsonObject.addProperty("downloadType", "bmclapi");
                }
            }
        }
    }

    /// Migrates the legacy selected profile name into the current selected game directory ID.
    ///
    /// @param json the legacy config JSON object
    /// @return whether the JSON object was changed
    static boolean migrateLegacySelectedGameDirectory(JsonObject json) {
        Objects.requireNonNull(json);

        @Nullable JsonElement lastElement = json.remove("last");
        if (lastElement == null) {
            return false;
        }

        @Nullable String selectedName = JsonUtils.getString(lastElement);
        if (selectedName != null) {
            json.add(LauncherSettings.PROPERTY_SELECTED_GAME_DIRECTORY,
                    JsonUtils.GSON.toJsonTree(getLegacyProfileID(selectedName), GameDirectoryID.class));
        }
        return true;
    }

    /// Migrates legacy per-profile selected versions into the current selected version map.
    ///
    /// @param json the legacy config JSON object
    /// @return whether the JSON object was changed
    static boolean migrateLegacySelectedVersions(JsonObject json) {
        Objects.requireNonNull(json);

        if (!(json.get("configurations") instanceof JsonObject configurations)) {
            return false;
        }

        JsonObject selectedInstance = json.get(LauncherSettings.PROPERTY_SELECTED_INSTANCE) instanceof JsonObject existingSelectedInstance
                ? existingSelectedInstance
                : new JsonObject();
        boolean changed = false;

        for (Map.Entry<String, JsonElement> entry : configurations.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject profile)) {
                continue;
            }

            @Nullable String selectedVersion = JsonUtils.getString(profile.get("selectedMinecraftVersion"));
            if (StringUtils.isBlank(selectedVersion)) {
                continue;
            }

            String id = getLegacyProfileID(entry.getKey()).toString();
            selectedInstance.addProperty(id, selectedVersion);
            changed = true;
        }

        if (changed && !json.has(LauncherSettings.PROPERTY_SELECTED_INSTANCE)) {
            json.add(LauncherSettings.PROPERTY_SELECTED_INSTANCE, selectedInstance);
        }
        return changed;
    }

    /// Migrates profile-global game settings from config files used before HMCL 3.16.
    static void migrateLegacyPresetSettings(
            GameDirectories gameDirectories,
            GameSettingsPresets gameSettingsPresets,
            @Nullable JsonObject configurations) {
        if (configurations == null) {
            return;
        }

        for (Profile profile : gameDirectories.getGameDirectories()) {
            @Nullable GameSettingsPresetID legacyGameSettings = profile.getLegacyGameSettings();
            if (legacyGameSettings == null) {
                continue;
            }

            GameSettings.Preset legacyParent = gameSettingsPresets.getPreset(legacyGameSettings);
            if (legacyParent == null) {
                @Nullable String profileName = getLegacyProfileName(profile);
                if (profileName == null) {
                    continue;
                }
                JsonObject profileObject = configurations.get(profileName) instanceof JsonObject profileJson ? profileJson : null;
                JsonObject legacySettingObject = profileObject != null && profileObject.get("global") instanceof JsonObject legacyJson ? legacyJson : null;
                if (legacySettingObject == null) {
                    continue;
                }

                legacyParent = LegacyGameSettingsMigrator.toPreset(legacyGameSettings, profileName, legacySettingObject);
                gameSettingsPresets.getPresets().add(legacyParent);
            }
        }
    }

    /// Returns the legacy profile name used in `configurations`.
    private static @Nullable String getLegacyProfileName(Profile profile) {
        if (LEGACY_DEFAULT_PROFILE_ID.equals(profile.getId())) {
            return LEGACY_DEFAULT_PROFILE;
        }
        if (LEGACY_HOME_PROFILE_ID.equals(profile.getId())) {
            return LEGACY_HOME_PROFILE;
        }
        return Profiles.getProfileCustomName(profile);
    }

    /// Detached settings migrated out of an old config file.
    ///
    /// @param gameDirectories the detached game directory store, or `null` when none was migrated
    /// @param gameSettingsPresets the detached preset store, or `null` when none was migrated
    /// @param launcherState the detached launcher state, or `null` when none was migrated
    /// @param authlibInjectorServers the detached authlib-injector servers, or `null` when none was migrated
    /// @param accountMigration the detached account metadata and private data stores, or `null` when none was migrated
    record DetachedSettings(
            @Nullable GameDirectories gameDirectories,
            @Nullable GameSettingsPresets gameSettingsPresets,
            @Nullable LauncherState launcherState,
            @Nullable AuthlibInjectorServerList authlibInjectorServers,
            @Nullable AccountMigrationResult accountMigration) {
        /// Returns an empty detached settings migration result.
        static DetachedSettings empty() {
            return new DetachedSettings(null, null, null, null, null);
        }
    }

    /// Account stores migrated from legacy account records.
    ///
    /// @param metadata the migrated metadata-only account store
    /// @param privateData the migrated private account data store
    record AccountMigrationResult(AccountMetadataStore metadata, AccountPrivateData privateData) {
        /// Returns an empty account migration result.
        static AccountMigrationResult empty() {
            return new AccountMigrationResult(new AccountMetadataStore(), new AccountPrivateData());
        }
    }

    /// One migrated legacy account entry split into current metadata and private data records.
    ///
    /// @param metadata the migrated account metadata
    /// @param privateData the migrated account private data
    private record MigratedLegacyAccount(JsonObject metadata, JsonObject privateData) {
    }

    /// Prepared migration data for a legacy config file.
    ///
    /// @param path the legacy config path
    /// @param launcherSettings the parsed launcher settings
    /// @param detachedSettings the detached settings migrated from legacy config fields
    record LegacyConfigMigration(
            Path path,
            LauncherSettings launcherSettings,
            DetachedSettings detachedSettings) {
    }

    /// Result of migrating the legacy user settings file.
    ///
    /// @param path the legacy user settings path
    /// @param userSettings the migrated user settings
    /// @param userState the migrated user state
    record UserSettingsMigrationResult(Path path, UserSettings userSettings, UserState userState) {
    }

    /// Result of migrating the legacy shared accounts file.
    ///
    /// @param path the legacy account file path
    /// @param accounts the migrated account metadata and private data stores
    record UserAccountsMigrationResult(Path path, AccountMigrationResult accounts) {
    }
}
