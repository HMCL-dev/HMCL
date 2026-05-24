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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ConfigHolder {

    private ConfigHolder() {
    }

    public static final Path GLOBAL_CONFIG_PATH = Metadata.HMCL_GLOBAL_DIRECTORY.resolve("config.json");

    private static final Path configLocation = Metadata.HMCL_CURRENT_DIRECTORY.resolve("settings.json");
    private static Config configInstance;
    private static GlobalConfig globalConfigInstance;
    private static boolean newlyCreated;
    private static boolean ownerChanged = false;
    private static boolean unsupportedVersion = false;

    public static Config config() {
        if (configInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return configInstance;
    }

    public static GlobalConfig globalConfig() {
        if (globalConfigInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return globalConfigInstance;
    }

    public static Path configLocation() {
        return configLocation;
    }

    public static boolean isNewlyCreated() {
        return newlyCreated;
    }

    public static boolean isOwnerChanged() {
        return ownerChanged;
    }

    public static boolean isUnsupportedVersion() {
        return unsupportedVersion;
    }

    public static void init() throws IOException {
        if (configInstance != null) {
            throw new IllegalStateException("Configuration is already loaded");
        }

        LOG.info("Config location: " + configLocation);

        configInstance = loadConfig();
        if (!unsupportedVersion)
            configInstance.addListener(source -> FileSaver.save(configLocation, configInstance.toJson()));

        globalConfigInstance = loadGlobalConfig();
        globalConfigInstance.addListener(source -> FileSaver.save(GLOBAL_CONFIG_PATH, globalConfigInstance.toJson()));

        Locale.setDefault(config().getLocalization().getLocale());
        I18n.setLocale(configInstance.getLocalization());
        LOG.setLogRetention(globalConfig().getLogRetention());
        Settings.init();

        if (newlyCreated) {
            LOG.info("Creating config file " + configLocation);
            FileUtils.saveSafely(configLocation, configInstance.toJson());
        }

        if (!Files.isWritable(configLocation)) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    && configLocation.getFileSystem() == FileSystems.getDefault()
                    && configLocation.toFile().canWrite()) {
                LOG.warning("Config at " + configLocation + " is not writable, but it seems to be a Samba share or OpenJDK bug");
                // There are some serious problems with the implementation of Samba or OpenJDK
                throw new SambaException();
            } else {
                // the config cannot be saved
                // throw up the error now to prevent further data loss
                throw new IOException("Config at " + configLocation + " is not writable");
            }
        }
    }

    private static Config loadConfig() throws IOException {
        if (Files.exists(configLocation)) {
            checkOwner(configLocation);
            try {
                String content = Files.readString(configLocation);
                Config deserialized = Config.fromJson(content);
                if (deserialized == null) {
                    LOG.info("Config is empty");
                } else {
                    return deserialized;
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
                LOG.info("Migrating config from " + migrationResult.path() + " to " + configLocation);
                FileUtils.saveSafely(configLocation, loadedConfig.contentForMigration());
                return loadedConfig.config();
            }
        }

        newlyCreated = true;
        return new Config();
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

    // Global Config

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
