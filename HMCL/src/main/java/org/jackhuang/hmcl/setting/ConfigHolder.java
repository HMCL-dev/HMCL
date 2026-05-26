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
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
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

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns the process-wide configuration instances.
@NotNullByDefault
public final class ConfigHolder {

    /// Prevents instantiation.
    private ConfigHolder() {
    }

    /// The global launcher config path shared by all workspaces.
    public static final Path GLOBAL_CONFIG_PATH = Metadata.HMCL_GLOBAL_DIRECTORY.resolve("config.json");

    /// The current per-workspace config path.
    private static final Path SETTINGS_LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("settings.json");

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

    /// Whether the per-workspace config file on disk is invalid and must be backed up
    /// before being overwritten by the first successful save.
    private static boolean needBackupSettings = false;

    /// Whether the per-workspace config should be saved after extracting detached data.
    private static boolean needSaveSettings = false;

    /// Detached game directories migrated from a config file.
    private static @Nullable GameDirectories migratedGameDirectories;

    /// Detached game settings presets migrated from a legacy config file.
    private static @Nullable GameSettingsPresets migratedGameSettingsPresets;

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
        return SETTINGS_LOCATION;
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

        Locale.setDefault(config().getLocalization().getLocale());
        I18n.setLocale(configInstance.getLocalization());
        LOG.setLogRetention(globalConfig().getLogRetention());
        GameDirectoriesHolder.init(migratedGameDirectories, !unsupportedVersion);
        GameSettingsPresetsHolder.init(migratedGameSettingsPresets, !unsupportedVersion);
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

            JsonFileFormat.CheckResult format = JsonFileFormat.check(jsonObject, Config.CURRENT_FORMAT);
            if (format.isMissing()) {
                LOG.warning("Missing format in settings file: " + SETTINGS_LOCATION);
                unsupportedVersion = true;
                return new Config();
            } else if (format.isInvalid()) {
                LOG.warning("Invalid format in settings file: "
                        + SETTINGS_LOCATION + ", Actual: " + format.invalidValue());
                unsupportedVersion = true;
                return new Config();
            } else if (format.isUnexpectedId()) {
                LOG.warning("Unexpected settings file format. Expected: "
                        + Config.CURRENT_FORMAT + ", Actual: " + format.actual());
                unsupportedVersion = true;
                return new Config();
            } else if (format.isNewerThanExpected()) {
                LOG.warning("Unsupported settings file format. Expected: "
                        + Config.CURRENT_FORMAT + ", Actual: " + format.actual());
                unsupportedVersion = true;

                if (format.hasNewerMajorVersion()) {
                    // Unsupported major version, reset to default
                    return new Config();
                }
            }

            try {
                @Nullable GameDirectories gameDirectories = GameDirectories.extractFromConfigJson(jsonObject);
                if (gameDirectories != null) {
                    migratedGameDirectories = gameDirectories;
                    needSaveSettings = true;
                }

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
                FileUtils.saveSafely(SETTINGS_LOCATION, migrationResult.contentForMigration());
                return migrationResult.config();
            }
        }

        var newSettings = new Config();
        newlyCreated = true;
        return newSettings;
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
