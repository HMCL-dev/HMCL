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
import java.util.UUID;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Migrates legacy per-workspace config files into the current settings.json file.
///
/// HMCL used hmcl.json and .hmcl.json as the main per-workspace config files through HMCL 3.14.1.
/// Those files are now legacy inputs only: migration reads them, writes a new settings.json, and leaves the original files unchanged.
///
/// @author Glavo
@NotNullByDefault
public final class LegacyConfigMigrator {
    /// The last numeric config version used by the legacy hmcl.json and .hmcl.json files.
    private static final int LEGACY_CURRENT_CONFIG_VERSION = 2;

    /// The legacy Windows and portable configuration file name used through HMCL 3.14.1.
    private static final String LEGACY_CONFIG_FILENAME = "hmcl.json";

    /// The legacy Linux configuration file name used through HMCL 3.14.1.
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

            migrateLegacyProfilePresetReferences(jsonObject);

            Config deserialized = Config.fromJson(jsonObject);
            if (deserialized == null) {
                return null;
            }

            GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();
            migrateLegacyPresetSettings(deserialized, gameSettingsPresets, jsonObject);
            return new MigrationResult(path, deserialized, gameSettingsPresets, deserialized.toJson());
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

    /// Writes legacy profile preset references into profile JSON before profile deserialization.
    private static void migrateLegacyProfilePresetReferences(JsonObject object) {
        if (object.get("configurations") instanceof JsonObject configurations) {
            for (Map.Entry<String, JsonElement> entry : configurations.entrySet()) {
                if (entry.getValue() instanceof JsonObject profileObject
                        && !profileObject.has("legacyGameSettingsParent")
                        && profileObject.get("global") instanceof JsonObject) {
                    profileObject.addProperty("legacyGameSettingsParent",
                            LegacyGameSettingsMigrator.getLegacyPresetId(entry.getKey()).toString());
                }
            }
        }
    }

    /// Migrates profile-global game settings from HMCL 3.14.1 and older config files.
    private static void migrateLegacyPresetSettings(Config config, GameSettingsPresets gameSettingsPresets, JsonObject object) {
        if (!(object.get("configurations") instanceof JsonObject configurations))
            return;

        for (Map.Entry<String, @Nullable Profile> entry : config.getConfigurations().entrySet()) {
            Profile profile = entry.getValue();
            if (profile == null) {
                continue;
            }

            String profileName = entry.getKey();
            UUID parentId = profile.getLegacyGameSettingsParent();
            if (parentId != null) {
                GameSettings.Preset parent = gameSettingsPresets.getGameSettings(parentId);
                if (parent != null) {
                    continue;
                }
            }

            GameSettings.Preset legacyParent = gameSettingsPresets.getGameSettings(
                    LegacyGameSettingsMigrator.getLegacyPresetId(profileName));
            if (legacyParent == null) {
                JsonObject profileObject = configurations.get(profileName) instanceof JsonObject profileJson ? profileJson : null;
                JsonObject legacySettingObject = profileObject != null && profileObject.get("global") instanceof JsonObject legacyJson ? legacyJson : null;
                if (legacySettingObject == null) {
                    continue;
                }

                legacyParent = LegacyGameSettingsMigrator.toPreset(profileName, profileName, legacySettingObject);
                gameSettingsPresets.getGameSettings().add(legacyParent);
            }

            profile.setLegacyGameSettingsParent(legacyParent.idProperty().getValue());
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
    /// @param gameSettingsPresets The detached preset store migrated from legacy profile globals.
    /// @param contentForMigration The content to save when migrating to settings.json.
    record MigrationResult(Path path, Config config, GameSettingsPresets gameSettingsPresets, String contentForMigration) {
    }
}
