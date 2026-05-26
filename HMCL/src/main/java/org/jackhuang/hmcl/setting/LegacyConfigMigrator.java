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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.StringUtils;
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

    /// The legacy Windows and portable configuration file name used through HMCL 3.15.0.345.
    private static final String LEGACY_CONFIG_FILENAME = "hmcl.json";

    /// The legacy Linux configuration file name used through HMCL 3.15.0.345.
    private static final String LEGACY_CONFIG_FILENAME_LINUX = ".hmcl.json";

    /// Prevents instantiation.
    private LegacyConfigMigrator() {
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

            @Nullable GameDirectories migratedGameDirectories = GameDirectories.extractFromConfigJson(jsonObject);
            GameDirectories gameDirectories = migratedGameDirectories != null
                    ? migratedGameDirectories
                    : new GameDirectories();

            Config deserialized = Config.fromJson(jsonObject);
            if (deserialized == null) {
                return null;
            }

            GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();
            migrateLegacyPresetSettings(gameDirectories, gameSettingsPresets, legacyConfigurations);
            return new MigrationResult(path, deserialized, gameDirectories, gameSettingsPresets, deserialized.toJson());
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy config file: " + path, e);
            return null;
        }
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

    /// Migrates profile-global game settings from HMCL 3.15.0.345 and older config files.
    private static void migrateLegacyPresetSettings(
            GameDirectories gameDirectories,
            GameSettingsPresets gameSettingsPresets,
            @Nullable JsonObject configurations) {
        if (configurations == null) {
            return;
        }

        for (Profile profile : gameDirectories.getGameDirectories()) {
            GameSettings.Preset legacyParent = gameSettingsPresets.getGameSettings(profile.getId());
            if (legacyParent == null) {
                String profileName = profile.getName();
                JsonObject profileObject = configurations.get(profileName) instanceof JsonObject profileJson ? profileJson : null;
                JsonObject legacySettingObject = profileObject != null && profileObject.get("global") instanceof JsonObject legacyJson ? legacyJson : null;
                if (legacySettingObject == null) {
                    continue;
                }

                legacyParent = LegacyGameSettingsMigrator.toPreset(profile.getId(), profileName, legacySettingObject);
                gameSettingsPresets.getGameSettings().add(legacyParent);
            }
        }
    }

    /// Reads a string field from a JSON object.
    @Contract("_,_,!null->!null")
    private static @Nullable String readString(JsonObject object, String key, @Nullable String defaultValue) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : defaultValue;
    }

    /// Result of locating and loading a legacy config file without modifying it.
    ///
    /// @param config              The parsed config object.
    /// @param gameDirectories     The detached game directory store migrated from legacy profiles.
    /// @param gameSettingsPresets The detached preset store migrated from legacy profile globals.
    /// @param contentForMigration The content to save when migrating to settings.json.
    record MigrationResult(Path path, Config config, GameDirectories gameDirectories, GameSettingsPresets gameSettingsPresets, String contentForMigration) {
    }
}
