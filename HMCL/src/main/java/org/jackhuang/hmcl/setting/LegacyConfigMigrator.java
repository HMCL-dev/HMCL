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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
@NotNullByDefault
public final class LegacyConfigMigrator {
    /// The last numeric config version used by legacy config files.
    private static final int LEGACY_CURRENT_CONFIG_VERSION = 2;

    /// The legacy Windows and portable configuration file name.
    private static final String LEGACY_CONFIG_FILENAME = "hmcl.json";

    /// The legacy Linux configuration file name.
    private static final String LEGACY_CONFIG_FILENAME_LINUX = ".hmcl.json";

    /// Prevents instantiation.
    private LegacyConfigMigrator() {
    }

    /// Loads a legacy config file and applies legacy schema upgrades in memory.
    private static @Nullable LoadedConfig loadLegacyConfig(Path path) throws IOException, JsonParseException {
        String content = Files.readString(path);
        Map<?, ?> rawJson = readRawJson(content);
        Config deserialized = Config.fromJson(content);
        if (deserialized == null) {
            return null;
        }

        int configVersion = getLegacyConfigVersion(rawJson);
        if (configVersion < LEGACY_CURRENT_CONFIG_VERSION) {
            upgradeConfig(deserialized, rawJson, configVersion);
            return new LoadedConfig(deserialized, content, deserialized.toJson(), false);
        } else if (configVersion > LEGACY_CURRENT_CONFIG_VERSION) {
            LOG.warning(String.format("Current HMCL only support the legacy configuration version up to %d. However, the version now is %d.", LEGACY_CURRENT_CONFIG_VERSION, configVersion));
            return new LoadedConfig(deserialized, content, null, true);
        } else {
            return new LoadedConfig(deserialized, content, deserialized.toJson(), false);
        }
    }

    /// Looks for a legacy config file and prepares it for writing as the new config file.
    static @Nullable MigrationResult migrateLegacyConfig() throws IOException {
        @Nullable Path path = locateLegacyConfig();
        if (path == null) {
            return null;
        }

        try {
            @Nullable LoadedConfig loadedConfig = loadLegacyConfig(path);
            if (loadedConfig == null) {
                LOG.info("Legacy config is empty: " + path);
                return null;
            }

            return new MigrationResult(path, loadedConfig);
        } catch (JsonParseException e) {
            LOG.warning("Malformed legacy config: " + path, e);
            return null;
        }
    }

    /// Finds a legacy config file with the same precedence as old HMCL versions.
    private static @Nullable Path locateLegacyConfig() {
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

    /// Reads the legacy numeric config version from raw JSON.
    private static int getLegacyConfigVersion(Map<?, ?> rawJson) {
        return tryCast(rawJson.get("_version"), Number.class)
                .map(Number::intValue)
                .orElse(LEGACY_CURRENT_CONFIG_VERSION);
    }

    /// Reads the raw legacy config JSON object.
    private static Map<?, ?> readRawJson(String rawContent) {
        Map<?, ?> rawJson = Collections.unmodifiableMap(new Gson().<Map<?, ?>>fromJson(rawContent, Map.class));
        return rawJson;
    }

    /// Upgrades old config fields to the current schema.
    private static void upgradeConfig(Config deserialized, Map<?, ?> rawJson, int configVersion) {
        LOG.info(String.format("Updating legacy configuration from %d to %d.", configVersion, LEGACY_CURRENT_CONFIG_VERSION));
        if (configVersion < 1) {
            tryCast(rawJson.get("auth"), Map.class).ifPresent(auth -> {
                tryCast(auth.get("offline"), Map.class).ifPresent(offline -> {
                    String selected = rawJson.containsKey("selectedAccount") ? null
                            : tryCast(offline.get("IAuthenticator_UserName"), String.class).orElse(null);

                    tryCast(offline.get("uuidMap"), Map.class).ifPresent(uuidMap -> {
                        ((Map<?, ?>) uuidMap).forEach((key, value) -> {
                            Map<Object, Object> storage = new HashMap<>();
                            storage.put("type", "offline");
                            storage.put("username", key);
                            storage.put("uuid", value);
                            if (key.equals(selected)) {
                                storage.put("selected", true);
                            }
                            deserialized.getAccountStorages().add(storage);
                        });
                    });
                });
            });

            if (!rawJson.containsKey("commonDirType")) {
                deserialized.setCommonDirType(deserialized.getCommonDirectory().equals(Settings.getDefaultCommonDirectory()) ? EnumCommonDirectory.DEFAULT : EnumCommonDirectory.CUSTOM);
            }
            if (!rawJson.containsKey("backgroundType")) {
                deserialized.setBackgroundImageType(StringUtils.isNotBlank(deserialized.getBackgroundImage()) ? EnumBackgroundImage.CUSTOM : EnumBackgroundImage.DEFAULT);
            }
            if (!rawJson.containsKey("hasProxy")) {
                deserialized.setHasProxy(StringUtils.isNotBlank(deserialized.getProxyHost()));
            }
            if (!rawJson.containsKey("hasProxyAuth")) {
                deserialized.setHasProxyAuth(StringUtils.isNotBlank(deserialized.getProxyUser()));
            }

            if (!rawJson.containsKey("downloadType")) {
                tryCast(rawJson.get("downloadtype"), Number.class)
                        .map(Number::intValue)
                        .ifPresent(id -> {
                            if (id == 0) {
                                deserialized.setDownloadType("mojang");
                            } else if (id == 1) {
                                deserialized.setDownloadType("bmclapi");
                            }
                        });
            }
        }
    }

    /// Result of loading a config file.
    static final class LoadedConfig {
        /// The parsed config object.
        private final Config config;

        /// The original file content.
        private final String rawContent;

        /// The upgraded content to save, or null when no schema upgrade was needed.
        private final @Nullable String upgradedContent;

        /// Whether the config version is newer than this HMCL build supports.
        private final boolean unsupportedVersion;

        /// Creates a loaded config result.
        private LoadedConfig(Config config, String rawContent, @Nullable String upgradedContent, boolean unsupportedVersion) {
            this.config = config;
            this.rawContent = rawContent;
            this.upgradedContent = upgradedContent;
            this.unsupportedVersion = unsupportedVersion;
        }

        /// Returns the parsed config object.
        Config config() {
            return config;
        }

        /// Returns the content that should be written when migrating to the new path.
        String contentForMigration() {
            return upgradedContent != null ? upgradedContent : rawContent;
        }

        /// Returns whether the config version is unsupported.
        boolean unsupportedVersion() {
            return unsupportedVersion;
        }
    }

    /// Result of locating and loading a legacy config file.
    static final class MigrationResult {
        /// The legacy config path.
        private final Path path;

        /// The loaded config data.
        private final LoadedConfig loadedConfig;

        /// Creates a migration result.
        private MigrationResult(Path path, LoadedConfig loadedConfig) {
            this.path = path;
            this.loadedConfig = loadedConfig;
        }

        /// Returns the legacy config path.
        Path path() {
            return path;
        }

        /// Returns the loaded config data.
        LoadedConfig loadedConfig() {
            return loadedConfig;
        }
    }
}
