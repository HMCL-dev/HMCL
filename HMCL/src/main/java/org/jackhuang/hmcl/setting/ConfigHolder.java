/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.io.JarUtils.thisJar;
import static org.jackhuang.hmcl.util.platform.OperatingSystem.CURRENT_OS;
import static org.jackhuang.hmcl.util.platform.OperatingSystem.WINDOWS;

public final class ConfigHolder {

    private ConfigHolder() {}

    public static final String CONFIG_FILENAME = "hmcl.json";
    public static final String CONFIG_FILENAME_LINUX = ".hmcl.json";

    private static Path configLocation;
    private static Config configInstance;
    private static boolean newlyCreated;

    public static Config config() {
        if (configInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return configInstance;
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
            // the config cannot be saved
            // throw up the error now to prevent further data loss
            throw new IOException("Config at " + configLocation + " is not writable");
        }
    }

    private static Path locateConfig() {
        // 1. Find JAR or EXE location, using current directory as fallback
        Path configDirectory = thisJar().map(Path::getParent)
                .orElseGet(() -> Paths.get("").toAbsolutePath());

        // 2. Try hmcl.json
        Path config = configDirectory.resolve(CONFIG_FILENAME);
        if (Files.exists(config))
            return config;

        // 3. Try .hmcl.json
        Path dotConfig = configDirectory.resolve(CONFIG_FILENAME_LINUX);
        if (Files.exists(dotConfig))
            return dotConfig;

        // 4. Create new config file
        //   - On Windows, we use hmcl.json
        //   - On Linux or OSX, we use .hmcl.json
        if (CURRENT_OS == WINDOWS) {
            return config;
        } else {
            return dotConfig;
        }
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

    private static InvocationDispatcher<String> configWriter = InvocationDispatcher.runOn(Lang::thread, content -> {
        try {
            writeToConfig(content);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to save config", e);
        }
    });

    private static void writeToConfig(String content) throws IOException {
        LOG.info("Saving config");
        synchronized (configLocation) {
            Files.write(configLocation, content.getBytes(UTF_8));
        }
    }

    static void markConfigDirty() {
        configWriter.accept(configInstance.toJson());
    }

    private static void saveConfigSync() throws IOException {
        writeToConfig(configInstance.toJson());
    }
}
