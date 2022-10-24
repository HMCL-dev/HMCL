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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class ConfigHolder {

    private ConfigHolder() {
    }

    public static final String CONFIG_FILENAME = "hmcl.json";
    public static final String CONFIG_FILENAME_LINUX = ".hmcl.json";
    public static final Path GLOBAL_CONFIG_PATH = Metadata.HMCL_DIRECTORY.resolve("config.json");

    private static Path configLocation;
    private static Config configInstance;
    private static GlobalConfig globalConfigInstance;
    private static boolean newlyCreated;

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

    public static boolean isNewlyCreated() {
        return newlyCreated;
    }

    public synchronized static void init() throws IOException {
        if (configInstance != null) {
            throw new IllegalStateException("Configuration is already loaded");
        }

        configLocation = locateConfig();

        LOG.log(Level.INFO, "Config location: " + configLocation);

        configInstance = loadConfig();
        configInstance.addListener(source -> markConfigDirty());

        globalConfigInstance = loadGlobalConfig();
        globalConfigInstance.addListener(source -> markGlobalConfigDirty());

        Settings.init();

        if (newlyCreated) {
            saveConfigSync();

            // hide the config file on windows
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                try {
                    Files.setAttribute(configLocation, "dos:hidden", true);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to set hidden attribute of " + configLocation, e);
                }
            }
        }

        if (!Files.isWritable(configLocation)) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    && configLocation.getFileSystem() == FileSystems.getDefault()
                    && configLocation.toFile().canWrite()) {
                // There are some serious problems with the implementation of Samba or OpenJDK
                throw new SambaException();
            } else {
                // the config cannot be saved
                // throw up the error now to prevent further data loss
                throw new IOException("Config at " + configLocation + " is not writable");
            }
        }
    }

    private static Path locateConfig() {
        Path exePath = Paths.get("");
        Path config = Paths.get(CONFIG_FILENAME);
        if (Files.isRegularFile(config))
            return config;

        Path dotConfig = Paths.get(CONFIG_FILENAME_LINUX);
        if (Files.isRegularFile(dotConfig))
            return dotConfig;

        try {
            Path jarPath = JarUtils.thisJar().orElse(null);
            if (jarPath != null && Files.isRegularFile(jarPath) && Files.isWritable(jarPath)) {
                Path jarDir = jarPath.getParent();

                Path jarConfig = jarDir.resolve(CONFIG_FILENAME);
                if (Files.isRegularFile(jarConfig))
                    return jarConfig;

                Path jarDotConfig = jarDir.resolve(CONFIG_FILENAME_LINUX);
                if (Files.isRegularFile(jarDotConfig))
                    return jarDotConfig;
            }
        } catch (Throwable ignore) {
        }
        return config;
    }

    private static Config loadConfig() throws IOException {
        if (Files.exists(configLocation)) {
            try {
                String content = FileUtils.readText(configLocation);
                Config deserialized = Config.fromJson(content);
                if (deserialized == null) {
                    LOG.info("Config is empty");
                } else {
                    Map<?, ?> raw = new Gson().fromJson(content, Map.class);
                    ConfigUpgrader.upgradeConfig(deserialized, raw);
                    return deserialized;
                }
            } catch (JsonParseException e) {
                LOG.log(Level.WARNING, "Malformed config.", e);
            }
        }

        LOG.info("Creating an empty config");
        newlyCreated = true;
        return new Config();
    }

    private static final InvocationDispatcher<String> configWriter = InvocationDispatcher.runOn(Lang::thread, content -> {
        try {
            writeToConfig(content);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to save config", e);
        }
    });

    private static void writeToConfig(String content) throws IOException {
        LOG.info("Saving config");
        synchronized (configLocation) {
            FileUtils.saveSafely(configLocation, content);
        }
    }

    static void markConfigDirty() {
        configWriter.accept(configInstance.toJson());
    }

    private static void saveConfigSync() throws IOException {
        writeToConfig(configInstance.toJson());
    }

    // Global Config

    private static GlobalConfig loadGlobalConfig() throws IOException {
        // Migrate from old directory
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Files.notExists(GLOBAL_CONFIG_PATH)) {
            Path oldHome;
            String xdgCache = System.getenv("XDG_CACHE_HOME");
            if (StringUtils.isNotBlank(xdgCache)) {
                oldHome = Paths.get(xdgCache, "hmcl");
            } else {
                oldHome = Paths.get(System.getProperty("user.home", "."), ".cache", "hmcl");
            }

            if (Files.exists(oldHome)) {
                Path oldConfigPath = oldHome.resolve("config.json");
                if (Files.isRegularFile(oldConfigPath)) {
                    try {
                        Files.copy(oldConfigPath, GLOBAL_CONFIG_PATH);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to migrate global config", e);
                    }
                }
            }
        }

        if (Files.exists(GLOBAL_CONFIG_PATH)) {
            try {
                String content = FileUtils.readText(GLOBAL_CONFIG_PATH);
                GlobalConfig deserialized = GlobalConfig.fromJson(content);
                if (deserialized == null) {
                    LOG.info("Config is empty");
                } else {
                    return deserialized;
                }
            } catch (JsonParseException e) {
                LOG.log(Level.WARNING, "Malformed config.", e);
            }
        }

        LOG.info("Creating an empty global config");
        return new GlobalConfig();
    }

    private static final InvocationDispatcher<String> globalConfigWriter = InvocationDispatcher.runOn(Lang::thread, content -> {
        try {
            writeToGlobalConfig(content);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to save config", e);
        }
    });

    private static void writeToGlobalConfig(String content) throws IOException {
        LOG.info("Saving global config");
        synchronized (GLOBAL_CONFIG_PATH) {
            FileUtils.saveSafely(GLOBAL_CONFIG_PATH, content);
        }
    }

    static void markGlobalConfigDirty() {
        globalConfigWriter.accept(globalConfigInstance.toJson());
    }

    private static void saveGlobalConfigSync() throws IOException {
        writeToConfig(globalConfigInstance.toJson());
    }
}
