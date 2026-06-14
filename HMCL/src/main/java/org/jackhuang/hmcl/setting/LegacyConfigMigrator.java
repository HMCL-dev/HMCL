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
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    /// The legacy built-in profile name for the current workspace game directory.
    private static final String LEGACY_DEFAULT_PROFILE = "Default";

    /// The total transparent custom window shadow size used by legacy launcher window bounds.
    private static final int LEGACY_CUSTOM_DECORATION_SHADOW_EXTENT = 16;

    /// The transparent custom window shadow size on one side used by legacy launcher window bounds.
    private static final int LEGACY_CUSTOM_DECORATION_SHADOW_SIZE = LEGACY_CUSTOM_DECORATION_SHADOW_EXTENT / 2;

    /// The legacy built-in profile name for the user-home game directory.
    private static final String LEGACY_HOME_PROFILE = "Home";

    /// The legacy built-in current-workspace profile ID.
    private static final SettingID LEGACY_DEFAULT_PROFILE_ID = getLegacyProfileID(LEGACY_DEFAULT_PROFILE);

    /// The legacy built-in user-home profile ID.
    private static final SettingID LEGACY_HOME_PROFILE_ID = getLegacyProfileID(LEGACY_HOME_PROFILE);

    /// The legacy Windows and portable configuration file name used before HMCL 3.16.
    private static final String LEGACY_CONFIG_FILENAME = "hmcl.json";

    /// The legacy Linux configuration file name used before HMCL 3.16.
    private static final String LEGACY_CONFIG_FILENAME_LINUX = ".hmcl.json";

    /// The legacy user settings path shared by all workspaces.
    private static final Path LEGACY_USER_SETTINGS_LOCATION = Metadata.HMCL_USER_HOME.resolve("config.json");

    /// The receipt recording the legacy user config migrated to the current user settings and state.
    private static final Path USER_SETTINGS_MIGRATION_RECEIPT_LOCATION =
            Metadata.HMCL_USER_HOME.resolve("state").resolve("user-settings.migration-receipt.json");

    /// The legacy user account storage path shared by all workspaces.
    private static final Path LEGACY_USER_ACCOUNTS_LOCATION = Metadata.HMCL_USER_HOME.resolve("accounts.json");

    /// The receipt recording the legacy shared accounts migrated to the shared account storage.
    private static final Path USER_ACCOUNTS_MIGRATION_RECEIPT_LOCATION =
            Metadata.HMCL_USER_HOME.resolve("state").resolve("user-game-accounts.migration-receipt.json");

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
    static SettingID getLegacyProfileID(String profileName) {
        return createLegacySettingID(LEGACY_PROFILE_ID_NAMESPACE, profileName);
    }

    /// Returns the stable game settings preset ID for a migrated legacy profile.
    static SettingID getLegacyGameSettingsID(String profileName) {
        return createLegacySettingID(LEGACY_GAME_SETTINGS_ID_NAMESPACE, profileName);
    }

    /// Returns whether any legacy workspace config file is present.
    static boolean hasLegacyConfig() {
        return locateLegacyConfig() != null;
    }

    /// Creates a deterministic setting ID for legacy migration data.
    private static SettingID createLegacySettingID(UUID namespace, String name) {
        return new SettingID(UUIDs.generateV5(namespace, name));
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
            AccountStorages accountStorages = Objects.requireNonNullElseGet(
                    extractAccountStorages(jsonObject),
                    AccountStorages::new);
            migrateLegacySelectedAccount(jsonObject, accountStorages);
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
                    launcherState, authlibInjectorServers, accountStorages);
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

    /// Migrates account storages from the legacy shared account file.
    ///
    /// @return the migrated shared account storages, or `null` when no legacy shared accounts can be used
    static @Nullable UserAccountsMigrationResult migrateLegacyUserAccounts() {
        if (!Files.exists(LEGACY_USER_ACCOUNTS_LOCATION)) {
            return null;
        }
        if (MigrationReceipt.matches(USER_ACCOUNTS_MIGRATION_RECEIPT_LOCATION, LEGACY_USER_ACCOUNTS_LOCATION)) {
            LOG.info("Skipping already migrated user accounts " + LEGACY_USER_ACCOUNTS_LOCATION);
            return null;
        }

        try {
            List<Map<Object, Object>> accounts = JsonUtils.fromJsonFile(
                    LauncherSettings.SETTINGS_GSON,
                    LEGACY_USER_ACCOUNTS_LOCATION,
                    JsonUtils.listTypeOf(JsonUtils.mapTypeOf(Object.class, Object.class))
            );
            if (accounts == null) {
                return null;
            }

            LOG.info("Migrating user accounts from " + LEGACY_USER_ACCOUNTS_LOCATION);
            return new UserAccountsMigrationResult(
                    LEGACY_USER_ACCOUNTS_LOCATION,
                    AccountStorages.fromAccounts(accounts));
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

    /// Extracts account storages from a config JSON object and removes the legacy member.
    static @Nullable AccountStorages extractAccountStorages(JsonObject json) {
        Objects.requireNonNull(json);

        JsonElement accounts = json.remove("accounts");
        if (accounts == null) {
            return null;
        }

        JsonObject object = new JsonObject();
        object.add(JsonSchema.PROPERTY_SCHEMA,
                JsonUtils.GSON.toJsonTree(AccountStorages.CURRENT_SCHEMA, JsonSchema.class));
        if (accounts instanceof JsonArray) {
            object.add("accounts", accounts);
        }

        AccountStorages result = JsonUtils.GSON.fromJson(object, AccountStorages.class);
        return result != null ? result : new AccountStorages();
    }

    /// Migrates the legacy selected account string into a structured selected account reference.
    static boolean migrateLegacySelectedAccount(JsonObject json, AccountStorages localAccounts) {
        Objects.requireNonNull(json);
        Objects.requireNonNull(localAccounts);

        JsonElement selectedAccount = json.get("selectedAccount");
        @Nullable JsonObject selectedMarkerReference = null;
        boolean changed = false;
        for (Map<Object, Object> account : localAccounts.getAccounts()) {
            Object selectedMarker = account.remove("selected");
            if (selectedMarker != null) {
                changed = true;
            }
            if (Boolean.TRUE.equals(selectedMarker) && selectedMarkerReference == null) {
                selectedMarkerReference = createSelectedAccountReference(account, false);
            }
        }

        if (selectedAccount == null) {
            if (selectedMarkerReference != null) {
                json.add("selectedAccount", selectedMarkerReference);
                return true;
            }
            return changed;
        }

        @Nullable String legacyIdentifier = JsonUtils.getString(selectedAccount);
        if (StringUtils.isBlank(legacyIdentifier)) {
            json.remove("selectedAccount");
            return true;
        }

        @Nullable JsonObject reference = findLegacySelectedAccountReference(legacyIdentifier, localAccounts, false);
        if (reference == null) {
            AccountStorages userAccounts = loadLegacyUserAccountStoragesForSelectedAccount();
            if (userAccounts != null) {
                reference = findLegacySelectedAccountReference(legacyIdentifier, userAccounts, true);
            }
        }

        if (reference != null) {
            json.add("selectedAccount", reference);
        } else {
            json.remove("selectedAccount");
        }
        return true;
    }

    /// Loads legacy user account storages only for resolving a selected account reference during migration.
    private static @Nullable AccountStorages loadLegacyUserAccountStoragesForSelectedAccount() {
        if (!Files.exists(LEGACY_USER_ACCOUNTS_LOCATION)) {
            return null;
        }

        try {
            List<Map<Object, Object>> accounts = JsonUtils.fromJsonFile(
                    LEGACY_USER_ACCOUNTS_LOCATION,
                    JsonUtils.listTypeOf(JsonUtils.mapTypeOf(Object.class, Object.class)));
            return accounts != null ? AccountStorages.fromAccounts(accounts) : null;
        } catch (Exception e) {
            LOG.warning("Failed to load legacy user accounts for selected account migration", e);
            return null;
        }
    }

    /// Finds the structured selected account reference matching a legacy selected account string.
    private static @Nullable JsonObject findLegacySelectedAccountReference(
            String legacyIdentifier,
            AccountStorages accounts,
            boolean userStorage) {
        String identifier = legacyIdentifier;
        boolean selectedUserStorage = false;
        if (identifier.startsWith("$GLOBAL:")) {
            selectedUserStorage = true;
            identifier = identifier.substring("$GLOBAL:".length());
        }
        if (selectedUserStorage != userStorage) {
            return null;
        }

        for (Map<Object, Object> account : accounts.getAccounts()) {
            if (matchesLegacySelectedAccountIdentifier(identifier, account)) {
                return createSelectedAccountReference(account, userStorage);
            }
        }
        return null;
    }

    /// Returns whether a serialized account entry matches a legacy selected account string.
    private static boolean matchesLegacySelectedAccountIdentifier(String identifier, Map<Object, Object> account) {
        @Nullable String legacyIdentifier = getLegacyAccountIdentifier(account, false);
        @Nullable String compactLegacyIdentifier = getLegacyAccountIdentifier(account, true);
        if (Objects.equals(identifier, legacyIdentifier)
                || Objects.equals(identifier, compactLegacyIdentifier)) {
            return true;
        }

        // Older legacy configs may store only the username for offline and Yggdrasil accounts.
        return Objects.equals(identifier, JsonUtils.getString(account, "username"));
    }

    /// Creates the structured selected account reference for a serialized account entry.
    private static @Nullable JsonObject createSelectedAccountReference(Map<Object, Object> account, boolean userStorage) {
        @Nullable String type = JsonUtils.getString(account, "type");
        if (type == null) {
            return null;
        }

        JsonObject reference = new JsonObject();
        reference.addProperty("storage", userStorage ? "user" : "local");
        reference.addProperty("type", type);

        switch (type) {
            case "offline" -> {
                @Nullable String username = JsonUtils.getString(account, "username");
                if (username == null) {
                    return null;
                }
                reference.addProperty("username", username);
            }
            case "microsoft" -> {
                @Nullable String uuid = JsonUtils.getString(account, "uuid");
                if (uuid == null) {
                    return null;
                }
                reference.addProperty("uuid", uuid);
                @Nullable String userId = JsonUtils.getString(account, "userid");
                if (userId != null) {
                    reference.addProperty("userid", userId);
                }
            }
            case "authlibInjector" -> {
                @Nullable String serverBaseURL = JsonUtils.getString(account, "serverBaseURL");
                @Nullable String username = JsonUtils.getString(account, "username");
                @Nullable String uuid = JsonUtils.getString(account, "uuid");
                if (serverBaseURL == null || username == null || uuid == null) {
                    return null;
                }
                reference.addProperty("serverBaseURL", serverBaseURL);
                reference.addProperty("username", username);
                reference.addProperty("uuid", uuid);
            }
            default -> {
                return null;
            }
        }
        return reference;
    }

    /// Returns the legacy string identifier for a serialized account entry.
    private static @Nullable String getLegacyAccountIdentifier(Map<Object, Object> account, boolean compactUuid) {
        @Nullable String type = JsonUtils.getString(account, "type");
        if (type == null) {
            return null;
        }

        return switch (type) {
            case "offline" -> {
                @Nullable String username = JsonUtils.getString(account, "username");
                yield username != null ? username + ":" + username : null;
            }
            case "microsoft" -> {
                @Nullable String uuid = JsonUtils.getString(account, "uuid");
                yield uuid != null ? "microsoft:" + formatLegacyUUID(uuid, compactUuid) : null;
            }
            case "authlibInjector" -> {
                @Nullable String serverBaseURL = JsonUtils.getString(account, "serverBaseURL");
                @Nullable String username = JsonUtils.getString(account, "username");
                @Nullable String uuid = JsonUtils.getString(account, "uuid");
                yield serverBaseURL != null && username != null && uuid != null
                        ? serverBaseURL + ":" + username + ":" + formatLegacyUUID(uuid, compactUuid)
                        : null;
            }
            default -> null;
        };
    }

    /// Formats a stored UUID the same way legacy account identifiers did.
    private static String formatLegacyUUID(String uuid, boolean compact) {
        if (compact) {
            return uuid;
        }

        try {
            return UUIDTypeAdapter.fromString(uuid).toString();
        } catch (IllegalArgumentException ignored) {
            return uuid;
        }
    }

    /// Moves one JSON member from the source object to the target object.
    private static void moveMember(JsonObject source, JsonObject target, String name) {
        JsonElement element = source.remove(name);
        if (element != null) {
            target.add(name, element);
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
                    JsonUtils.GSON.toJsonTree(getLegacyProfileID(selectedName), SettingID.class));
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
            @Nullable SettingID legacyGameSettings = profile.getLegacyGameSettings();
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
    /// @param accountStorages the detached account storages, or `null` when none was migrated
    record DetachedSettings(
            @Nullable GameDirectories gameDirectories,
            @Nullable GameSettingsPresets gameSettingsPresets,
            @Nullable LauncherState launcherState,
            @Nullable AuthlibInjectorServerList authlibInjectorServers,
            @Nullable AccountStorages accountStorages) {
        /// Returns an empty detached settings migration result.
        static DetachedSettings empty() {
            return new DetachedSettings(null, null, null, null, null);
        }
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
    /// @param path the legacy account storage path
    /// @param accountStorages the migrated account storage
    record UserAccountsMigrationResult(Path path, AccountStorages accountStorages) {
    }
}
