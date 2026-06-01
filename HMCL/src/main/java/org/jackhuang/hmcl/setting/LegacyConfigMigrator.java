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

import com.github.f4b6a3.uuid.alt.GUID;
import com.google.gson.*;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Migrates legacy per-workspace config files into the current settings.json file.
///
/// HMCL used hmcl.json and .hmcl.json as the main per-workspace config files through HMCL 3.15.0.345.
/// Those files are now legacy inputs only: migration reads them, writes a new settings.json, and leaves the original files unchanged.
///
/// @author Glavo
@NotNullByDefault
public final class LegacyConfigMigrator {
    /// The last numeric config version used by the legacy hmcl.json and .hmcl.json files.
    private static final int LEGACY_CURRENT_CONFIG_VERSION = 2;

    /// Namespace used to generate stable IDs for legacy profiles.
    private static final GUID LEGACY_PROFILE_ID_NAMESPACE = GUID.v5(GUID.NAMESPACE_URL, "hmcl:legacy-profile");

    /// Namespace used to generate stable IDs for profile-level game settings migrated from legacy profiles.
    private static final GUID LEGACY_GAME_SETTINGS_ID_NAMESPACE = GUID.v5(GUID.NAMESPACE_URL, "hmcl:legacy-game-settings");

    /// The legacy Windows and portable configuration file name used through HMCL 3.15.0.345.
    private static final String LEGACY_CONFIG_FILENAME = "hmcl.json";

    /// The legacy Linux configuration file name used through HMCL 3.15.0.345.
    private static final String LEGACY_CONFIG_FILENAME_LINUX = ".hmcl.json";

    /// The legacy user settings path shared by all workspaces.
    private static final Path LEGACY_USER_SETTINGS_LOCATION = Metadata.HMCL_USER_HOME.resolve("config.json");

    /// The legacy user account storage path shared by all workspaces.
    private static final Path LEGACY_USER_ACCOUNTS_LOCATION = Metadata.HMCL_USER_HOME.resolve("accounts.json");

    /// Legacy ordinal order for `EnumBackgroundImage` in upstream/main configs.
    private static final String[] LEGACY_BACKGROUND_IMAGE_TYPES = {
            "DEFAULT",
            "CUSTOM",
            "CLASSIC",
            "NETWORK",
            "TRANSLUCENT",
            "PAINT"
    };

    /// Legacy ordinal order for `Proxy.Type` in upstream/main configs.
    private static final String[] LEGACY_PROXY_TYPES = {
            "DIRECT",
            "HTTP",
            "SOCKS"
    };

    /// Prevents instantiation.
    private LegacyConfigMigrator() {
    }

    /// Returns the stable profile ID for a migrated legacy profile.
    public static GUID getLegacyProfileId(String profileName) {
        return GUID.v5(LEGACY_PROFILE_ID_NAMESPACE, profileName);
    }

    /// Returns the stable game settings preset ID for a migrated legacy profile.
    public static GUID getLegacyGameSettingsId(String profileName) {
        return GUID.v5(LEGACY_GAME_SETTINGS_ID_NAMESPACE, profileName);
    }

    /// Looks for a legacy config file and prepares it for writing as the new config file.
    static @Nullable MigrationResult migrateLegacyConfig() throws IOException {
        @Nullable Path path = locateLegacyConfig();
        if (path == null) {
            return null;
        }

        try {
            JsonObject jsonObject = JsonUtils.fromJsonFile(path, JsonObject.class);
            if (jsonObject == null) {
                LOG.info("Legacy config file is empty");
                return null;
            }

            // _version belongs to the legacy file format only. The current settings.json format will use
            // a separate versioning scheme and must not depend on this numeric value.
            // Older configs may not contain _version; historically those should be treated as the last
            // pre-settings.json schema unless older-field probes below prove they need extra upgrades.
            int configVersion = jsonObject.remove("_version") instanceof JsonPrimitive version && version.isNumber()
                    ? version.getAsInt()
                    : 0;

            if (configVersion > LEGACY_CURRENT_CONFIG_VERSION) {
                LOG.warning("Unsupported legacy config version: " + configVersion);
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
            migrateLegacyEnumOrdinals(jsonObject);
            migrateLegacyDownloadSources(jsonObject);
            migrateLegacyCommonDirectoryType(jsonObject);
            migrateLegacyCommonDirectory(jsonObject);
            migrateLegacyLanguage(jsonObject);
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
            return new MigrationResult(path, deserialized, detachedSettings, deserialized.toJson());
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy config file: " + path, e);
            return null;
        }
    }

    /// Extracts detached settings data that may still be embedded in a current settings JSON object.
    ///
    /// @param json the current settings JSON object
    /// @return the extracted detached settings and whether the JSON object was changed
    static CurrentSettingsMigration migrateCurrentSettings(JsonObject json) {
        Objects.requireNonNull(json);

        @Nullable AccountStorages accountStorages = extractAccountStorages(json);
        if (accountStorages == null) {
            return new CurrentSettingsMigration(DetachedSettings.empty(), false);
        }

        return new CurrentSettingsMigration(
                new DetachedSettings(null, null, null, null, accountStorages),
                true);
    }

    /// Migrates user settings from the legacy global config file.
    ///
    /// @param targetLocation the current user settings path used for logging
    /// @return the migrated user settings, or `null` when no legacy user settings can be used
    static @Nullable UserSettings migrateLegacyUserSettings(Path targetLocation) throws IOException {
        Objects.requireNonNull(targetLocation);

        if (!Files.exists(LEGACY_USER_SETTINGS_LOCATION)) {
            return null;
        }

        try {
            String content = Files.readString(LEGACY_USER_SETTINGS_LOCATION);
            UserSettings deserialized = UserSettings.fromJson(content);
            if (deserialized == null) {
                LOG.info("Legacy user settings file is empty: " + LEGACY_USER_SETTINGS_LOCATION);
                return null;
            }

            LOG.info("Migrating user settings from " + LEGACY_USER_SETTINGS_LOCATION + " to " + targetLocation);
            return deserialized;
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy user settings: " + LEGACY_USER_SETTINGS_LOCATION, e);
            return null;
        }
    }

    /// Extracts launcher state from a legacy config JSON object and removes those members.
    static LauncherState extractLauncherState(JsonObject json) {
        Objects.requireNonNull(json);

        JsonObject state = new JsonObject();
        state.add(JsonSchema.PROPERTY_SCHEMA, JsonUtils.GSON.toJsonTree(LauncherState.CURRENT_SCHEMA, JsonSchema.class));
        moveMember(json, state, "x");
        moveMember(json, state, "y");
        moveMember(json, state, "width");
        moveMember(json, state, "height");
        moveMember(json, state, "promptedVersion");
        moveMember(json, state, "shownTips");

        LauncherState result = JsonUtils.GSON.fromJson(state, LauncherState.class);
        return result != null ? result : new LauncherState();
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
        if (selectedAccount == null || selectedAccount instanceof JsonObject) {
            return false;
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

        try (Reader reader = Files.newBufferedReader(LEGACY_USER_ACCOUNTS_LOCATION)) {
            List<Map<Object, Object>> accounts =
                    LauncherSettings.SETTINGS_GSON.fromJson(reader, JsonUtils.listTypeOf(JsonUtils.mapTypeOf(Object.class, Object.class)));
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

    /// Migrates the legacy `localization` field into the current `language` field.
    static void migrateLegacyLanguage(JsonObject json) {
        Objects.requireNonNull(json);

        JsonElement legacyLanguage = json.remove("localization");
        if (json.has("language") || !(legacyLanguage instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return;
        }

        json.addProperty("language", switch (primitive.getAsString()) {
            case "zh_CN" -> "zh-Hans";
            case "zh" -> "zh-Hant";
            default -> primitive.getAsString();
        });
    }

    /// Migrates the legacy `commonpath` field into the current `commonDirectory` field.
    static void migrateLegacyCommonDirectory(JsonObject json) {
        Objects.requireNonNull(json);

        JsonElement legacyCommonDirectory = json.remove("commonpath");
        if (json.has("commonDirectory")
                || !(legacyCommonDirectory instanceof JsonPrimitive primitive)
                || !primitive.isString()) {
            return;
        }

        json.addProperty("commonDirectory", primitive.getAsString());
    }

    /// Migrates the legacy `commonDirType` field into the current `commonDirectoryType` field.
    static void migrateLegacyCommonDirectoryType(JsonObject json) {
        Objects.requireNonNull(json);

        JsonElement legacyCommonDirectoryType = json.remove("commonDirType");
        if (json.has("commonDirectoryType") || legacyCommonDirectoryType == null) {
            return;
        }

        json.add("commonDirectoryType", legacyCommonDirectoryType);
    }

    /// Migrates legacy enum ordinal fields into stable enum names.
    static void migrateLegacyEnumOrdinals(JsonObject json) {
        Objects.requireNonNull(json);

        migrateLegacyEnumOrdinal(json, "backgroundType", LEGACY_BACKGROUND_IMAGE_TYPES);
        migrateLegacyEnumOrdinal(json, "proxyType", LEGACY_PROXY_TYPES);
    }

    /// Migrates one legacy enum ordinal field into a stable enum name.
    private static void migrateLegacyEnumOrdinal(JsonObject json, String propertyName, String[] legacyNames) {
        @Nullable Integer ordinal = JsonUtils.getInteger(json.get(propertyName));
        if (ordinal == null || ordinal < 0 || ordinal >= legacyNames.length) {
            return;
        }

        json.addProperty(propertyName, legacyNames[ordinal]);
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

        if (!json.has("versionListSource")) {
            json.addProperty("versionListSource", source.name());
        }
        if (!json.has("fileDownloadSource")) {
            json.addProperty("fileDownloadSource", source.name());
        }
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
            preset.nameProperty().setValue("Default");
            gameSettingsPresets.getPresets().add(preset);
            launcherSettings.defaultGameSettingsPresetProperty().set(preset.idProperty().getValue());
        }
    }

    /// Extracts game directory data from a legacy config JSON object and removes the legacy members.
    ///
    /// This supports migrating the upstream/main `configurations` map into `game-directories.json`.
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
            String name = entry.getKey();
            if (isBuiltInProfileName(name)) {
                migrated.remove("name");
            } else {
                migrated.addProperty("name", name);
            }
            migrated.addProperty("id", getLegacyProfileId(name).toString());
            if (profile.get("global") instanceof JsonObject) {
                migrated.addProperty("legacyGameSettings", getLegacyGameSettingsId(name).toString());
            }
            result.add(migrated);
        }
        return result;
    }

    /// Returns whether the given legacy profile name belongs to a built-in profile.
    private static boolean isBuiltInProfileName(@Nullable String name) {
        return Profiles.DEFAULT_PROFILE.equals(name) || Profiles.HOME_PROFILE.equals(name);
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
            if (!jsonObject.has("commonDirType") && !jsonObject.has("commonDirectoryType")) {
                String commonDirectory = JsonUtils.getString(jsonObject, "commonpath", LauncherSettings.getDefaultCommonDirectory());
                jsonObject.addProperty("commonDirectoryType", commonDirectory.equals(LauncherSettings.getDefaultCommonDirectory())
                        ? EnumCommonDirectory.DEFAULT.name()
                        : EnumCommonDirectory.CUSTOM.name());
            }
            if (!jsonObject.has("backgroundType")) {
                String backgroundImage = JsonUtils.getString(jsonObject, "bgpath", "");
                jsonObject.addProperty("backgroundType", StringUtils.isNotBlank(backgroundImage)
                        ? EnumBackgroundImage.CUSTOM.name()
                        : EnumBackgroundImage.DEFAULT.name());
            }
            if (!jsonObject.has("hasProxy")) {
                jsonObject.addProperty("hasProxy", StringUtils.isNotBlank(JsonUtils.getString(jsonObject, "proxyHost", "")));
            }
            if (!jsonObject.has("hasProxyAuth")) {
                jsonObject.addProperty("hasProxyAuth", StringUtils.isNotBlank(JsonUtils.getString(jsonObject, "proxyUserName", "")));
            }

            if (!jsonObject.has("downloadType")) {
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

        if (json.has(LauncherSettings.PROPERTY_SELECTED_GAME_DIRECTORY)) {
            return true;
        }

        @Nullable String selectedName = JsonUtils.getString(lastElement);
        if (selectedName != null) {
            json.add(LauncherSettings.PROPERTY_SELECTED_GAME_DIRECTORY,
                    JsonUtils.GSON.toJsonTree(getLegacyProfileId(selectedName), GUID.class));
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

            String id = getLegacyProfileId(entry.getKey()).toString();
            if (!selectedInstance.has(id)) {
                selectedInstance.addProperty(id, selectedVersion);
                changed = true;
            }
        }

        if (changed && !json.has(LauncherSettings.PROPERTY_SELECTED_INSTANCE)) {
            json.add(LauncherSettings.PROPERTY_SELECTED_INSTANCE, selectedInstance);
        }
        return changed;
    }

    /// Migrates profile-global game settings from HMCL 3.15.0.345 and older config files.
    static void migrateLegacyPresetSettings(
            GameDirectories gameDirectories,
            GameSettingsPresets gameSettingsPresets,
            @Nullable JsonObject configurations) {
        if (configurations == null) {
            return;
        }

        for (Profile profile : gameDirectories.getGameDirectories()) {
            @Nullable GUID legacyGameSettings = profile.getLegacyGameSettings();
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
        if (Profiles.DEFAULT_PROFILE_ID.equals(profile.getId())) {
            return Profiles.DEFAULT_PROFILE;
        }
        if (Profiles.HOME_PROFILE_ID.equals(profile.getId())) {
            return Profiles.HOME_PROFILE;
        }
        return profile.getName();
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

    /// Result of migrating detached data out of an existing settings file.
    ///
    /// @param detachedSettings the detached settings migrated out of the settings JSON object
    /// @param changed whether the settings JSON object was changed
    record CurrentSettingsMigration(DetachedSettings detachedSettings, boolean changed) {
    }

    /// Result of locating and loading a legacy config file without modifying it.
    ///
    /// @param path the legacy config path
    /// @param launcherSettings the parsed launcher settings
    /// @param detachedSettings the detached settings migrated from legacy config fields
    /// @param contentForMigration the content to save when migrating to settings.json
    record MigrationResult(
            Path path,
            LauncherSettings launcherSettings,
            DetachedSettings detachedSettings,
            String contentForMigration) {
    }
}
