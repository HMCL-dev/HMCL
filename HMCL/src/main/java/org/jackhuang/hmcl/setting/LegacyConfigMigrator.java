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
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /// The legacy Windows and portable configuration file name used through HMCL 3.15.0.345.
    private static final String LEGACY_CONFIG_FILENAME = "hmcl.json";

    /// The legacy Linux configuration file name used through HMCL 3.15.0.345.
    private static final String LEGACY_CONFIG_FILENAME_LINUX = ".hmcl.json";

    /// Prevents instantiation.
    private LegacyConfigMigrator() {
    }

    /// Returns the stable profile ID for a migrated legacy profile.
    public static GUID getLegacyProfileId(String profileName) {
        return GUID.v5(LEGACY_PROFILE_ID_NAMESPACE, profileName);
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
            migrateLegacySelectedVersions(jsonObject);
            @Nullable GameDirectories migratedGameDirectories = extractGameDirectoriesFromConfigJson(jsonObject);
            GameDirectories gameDirectories = migratedGameDirectories != null
                    ? migratedGameDirectories
                    : new GameDirectories();
            migrateLegacySelectedGameDirectory(jsonObject);

            Config deserialized = Config.fromJson(jsonObject);
            if (deserialized == null) {
                return null;
            }

            GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();
            migrateLegacyPresetSettings(gameDirectories, gameSettingsPresets, legacyConfigurations);
            return new MigrationResult(path, deserialized, gameDirectories, gameSettingsPresets,
                    launcherState, authlibInjectorServers, deserialized.toJson());
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy config file: " + path, e);
            return null;
        }
    }

    /// Extracts launcher state from a legacy config JSON object and removes those members.
    static LauncherState extractLauncherState(JsonObject json) {
        Objects.requireNonNull(json);

        JsonObject state = new JsonObject();
        state.add(JsonFileFormat.DEFAULT_MEMBER_NAME, JsonUtils.GSON.toJsonTree(LauncherState.CURRENT_FORMAT, JsonFileFormat.class));
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
        servers.add(JsonFileFormat.DEFAULT_MEMBER_NAME,
                JsonUtils.GSON.toJsonTree(AuthlibInjectorServerList.CURRENT_FORMAT, JsonFileFormat.class));
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

    /// Moves one JSON member from the source object to the target object.
    private static void moveMember(JsonObject source, JsonObject target, String name) {
        JsonElement element = source.remove(name);
        if (element != null) {
            target.add(name, element);
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
        object.add(JsonFileFormat.DEFAULT_MEMBER_NAME, JsonUtils.GSON.toJsonTree(GameDirectories.CURRENT_FORMAT, JsonFileFormat.class));
        object.add("gameDirectories", profiles);

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
        // Keep this order aligned with old ConfigHolder behavior so the same legacy file wins during migration.
        Path defaultConfigFile = Metadata.HMCL_CURRENT_DIRECTORY.resolve(LEGACY_CONFIG_FILENAME);
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
                        : readString(offline, "IAuthenticator_UserName", null);
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
                String commonDirectory = readString(jsonObject, "commonpath", Settings.getDefaultCommonDirectory());
                jsonObject.addProperty("commonDirType", commonDirectory.equals(Settings.getDefaultCommonDirectory())
                        ? EnumCommonDirectory.DEFAULT.name()
                        : EnumCommonDirectory.CUSTOM.name());
            }
            if (!jsonObject.has("backgroundType")) {
                String backgroundImage = readString(jsonObject, "bgpath", "");
                jsonObject.addProperty("backgroundType", StringUtils.isNotBlank(backgroundImage)
                        ? EnumBackgroundImage.CUSTOM.name()
                        : EnumBackgroundImage.DEFAULT.name());
            }
            if (!jsonObject.has("hasProxy")) {
                jsonObject.addProperty("hasProxy", StringUtils.isNotBlank(readString(jsonObject, "proxyHost", "")));
            }
            if (!jsonObject.has("hasProxyAuth")) {
                jsonObject.addProperty("hasProxyAuth", StringUtils.isNotBlank(readString(jsonObject, "proxyUserName", "")));
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

        if (json.has(Config.SELECTED_GAME_DIRECTORY_MEMBER_NAME)) {
            return true;
        }

        @Nullable String selectedName = readString(lastElement);
        if (selectedName != null) {
            json.add(Config.SELECTED_GAME_DIRECTORY_MEMBER_NAME,
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

        JsonObject selectedInstance = json.get(Config.SELECTED_INSTANCE_MEMBER_NAME) instanceof JsonObject existingSelectedInstance
                ? existingSelectedInstance
                : new JsonObject();
        boolean changed = false;

        for (Map.Entry<String, JsonElement> entry : configurations.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject profile)) {
                continue;
            }

            @Nullable String selectedVersion = readString(profile.get("selectedMinecraftVersion"));
            if (StringUtils.isBlank(selectedVersion)) {
                continue;
            }

            String id = getLegacyProfileId(entry.getKey()).toString();
            if (!selectedInstance.has(id)) {
                selectedInstance.addProperty(id, selectedVersion);
                changed = true;
            }
        }

        if (changed && !json.has(Config.SELECTED_INSTANCE_MEMBER_NAME)) {
            json.add(Config.SELECTED_INSTANCE_MEMBER_NAME, selectedInstance);
        }
        return changed;
    }

    /// Migrates profile-global game settings from HMCL 3.15.0.345 and older config files.
    private static void migrateLegacyPresetSettings(
            GameDirectories gameDirectories,
            GameSettingsPresets gameSettingsPresets,
            @Nullable JsonObject configurations) {
        if (configurations == null) {
            return;
        }

        for (Profile profile : gameDirectories.getGameDirectories()) {
            GameSettings.Preset legacyParent = gameSettingsPresets.getPreset(profile.getId());
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

                legacyParent = LegacyGameSettingsMigrator.toPreset(profile.getId(), profileName, legacySettingObject);
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

    /// Reads a string JSON value.
    private static @Nullable String readString(@Nullable JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isString()
                ? primitive.getAsString()
                : null;
    }

    /// Reads a string field from a JSON object.
    @Contract("_,_,!null->!null")
    private static @Nullable String readString(JsonObject object, String key, @Nullable String defaultValue) {
        @Nullable String value = readString(object.get(key));
        return value != null ? value : defaultValue;
    }

    /// Result of locating and loading a legacy config file without modifying it.
    ///
    /// @param config              The parsed config object.
    /// @param gameDirectories     The detached game directory store migrated from legacy profiles.
    /// @param gameSettingsPresets The detached preset store migrated from legacy profile globals.
    /// @param launcherState       The detached launcher state migrated from legacy config fields.
    /// @param authlibInjectorServers The detached authlib-injector servers migrated from legacy config fields.
    /// @param contentForMigration The content to save when migrating to settings.json.
    record MigrationResult(Path path, Config config, GameDirectories gameDirectories,
                           GameSettingsPresets gameSettingsPresets, LauncherState launcherState,
                           AuthlibInjectorServerList authlibInjectorServers, String contentForMigration) {
    }
}
