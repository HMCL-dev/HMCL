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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns the process-wide configuration instances.
///
/// The main per-workspace config is stored in `settings.json`, while reusable game setting
/// presets are stored in `game-setting-presets.json`. Older config files may still embed those
/// presets in `settings.json`; during startup this holder moves them into the detached preset file
/// without modifying any legacy hmcl.json or .hmcl.json input file.
@NotNullByDefault
public final class ConfigHolder {

    /// Prevents instantiation.
    private ConfigHolder() {
    }

    /// The global launcher config path shared by all workspaces.
    public static final Path GLOBAL_CONFIG_PATH = Metadata.HMCL_GLOBAL_DIRECTORY.resolve("config.json");

    /// The current per-workspace config path.
    private static final Path CONFIG_LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("settings.json");

    /// The current per-workspace game setting preset path.
    private static final Path GAME_SETTING_PRESETS_LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-setting-presets.json");

    /// The loaded per-workspace config instance.
    private static @UnknownNullability Config configInstance;

    /// The loaded user-global config instance.
    private static @UnknownNullability GlobalConfig globalConfigInstance;

    /// Whether no current or legacy per-workspace config could be loaded.
    private static boolean newlyCreated;

    /// Whether root is reading a per-workspace config owned by another user.
    private static boolean ownerChanged = false;

    /// Whether a legacy config was newer than this build can safely overwrite.
    private static boolean unsupportedVersion = false;

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

    /// Returns the current per-workspace config path.
    public static Path configLocation() {
        return CONFIG_LOCATION;
    }

    /// Returns the current per-workspace game setting preset path.
    public static Path gameSettingPresetsLocation() {
        return GAME_SETTING_PRESETS_LOCATION;
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

        LOG.info("Config location: " + CONFIG_LOCATION);
        LOG.info("Game setting presets location: " + GAME_SETTING_PRESETS_LOCATION);

        configInstance = loadConfig();
        boolean gameSettingPresetsNewlyCreated = loadGameSettingPresets(configInstance);
        if (!unsupportedVersion) {
            configInstance.addListener(source -> FileSaver.save(CONFIG_LOCATION, configInstance.toJson()));
            configInstance.gameSettingPresets().addListener(source ->
                    FileSaver.save(GAME_SETTING_PRESETS_LOCATION, configInstance.gameSettingPresets().toJson()));
        }

        globalConfigInstance = loadGlobalConfig();
        globalConfigInstance.addListener(source -> FileSaver.save(GLOBAL_CONFIG_PATH, globalConfigInstance.toJson()));

        Locale.setDefault(config().getLocalization().getLocale());
        I18n.setLocale(configInstance.getLocalization());
        LOG.setLogRetention(globalConfig().getLogRetention());
        Settings.init();

        if (newlyCreated) {
            LOG.info("Creating config file " + CONFIG_LOCATION);
            FileUtils.saveSafely(CONFIG_LOCATION, configInstance.toJson());
        }

        if (gameSettingPresetsNewlyCreated) {
            LOG.info("Creating game setting presets file " + GAME_SETTING_PRESETS_LOCATION);
            FileUtils.saveSafely(GAME_SETTING_PRESETS_LOCATION, configInstance.gameSettingPresets().toJson());
        }

        if (!unsupportedVersion && !newlyCreated && configInstance.hasEmbeddedGameSettingPresetsLoaded()) {
            LOG.info("Removing embedded game setting presets from config file " + CONFIG_LOCATION);
            FileUtils.saveSafely(CONFIG_LOCATION, configInstance.toJson());
        }

        checkWritable(CONFIG_LOCATION);
        checkWritable(GAME_SETTING_PRESETS_LOCATION);
    }

    /// Loads the current per-workspace config or migrates a legacy config when needed.
    private static Config loadConfig() throws IOException {
        if (Files.exists(CONFIG_LOCATION)) {
            checkOwner(CONFIG_LOCATION);
            try {
                JsonObject jsonObject = readJsonObject(CONFIG_LOCATION);
                if (jsonObject == null) {
                    LOG.info("Config is empty");
                } else {
                    Config deserialized = Config.fromJson(jsonObject);
                    if (deserialized == null) {
                        LOG.info("Config is empty");
                    } else {
                        return deserialized;
                    }
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed config.", e);
            }
        } else {
            LegacyConfigMigrator.MigrationResult migrationResult = LegacyConfigMigrator.migrateLegacyConfig();
            if (migrationResult != null) {
                checkOwner(migrationResult.path());
                LegacyConfigMigrator.LoadedConfig loadedConfig = migrationResult.loadedConfig();
                unsupportedVersion = loadedConfig.unsupportedVersion();
                LOG.info("Migrating config from " + migrationResult.path() + " to " + CONFIG_LOCATION);
                FileUtils.saveSafely(CONFIG_LOCATION, loadedConfig.contentForMigration());
                return loadedConfig.config();
            }
        }

        newlyCreated = true;
        return new Config();
    }

    /// Loads the detached game setting preset file.
    ///
    /// Returns `true` when the preset file is missing and should be created from any presets that
    /// were embedded in the main config or produced by legacy migration.
    private static boolean loadGameSettingPresets(Config config) throws IOException {
        if (Files.exists(GAME_SETTING_PRESETS_LOCATION)) {
            checkOwner(GAME_SETTING_PRESETS_LOCATION);
            try {
                JsonObject jsonObject = readJsonObject(GAME_SETTING_PRESETS_LOCATION);
                if (jsonObject == null) {
                    LOG.info("Game setting presets are empty");
                } else {
                    GameSettingPresets deserialized = GameSettingPresets.fromJson(jsonObject);
                    if (deserialized == null) {
                        LOG.info("Game setting presets are empty");
                    } else {
                        config.setGameSettingPresets(deserialized);
                        return false;
                    }
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed game setting presets.", e);
            }

            config.setGameSettingPresets(new GameSettingPresets());
            return false;
        }

        return true;
    }

    /// Reads the given JSON file as an object.
    private static @Nullable JsonObject readJsonObject(Path path) throws IOException, JsonParseException {
        try (var reader = Files.newBufferedReader(path)) {
            return Config.CONFIG_GSON.fromJson(reader, JsonObject.class);
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
