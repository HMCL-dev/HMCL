/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.setting;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Logging.LOG;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public final class ConfigHolder {

    private ConfigHolder() {}

    public static final String CONFIG_FILENAME = "hmcl.json";
    public static final Path CONFIG_PATH = Paths.get(CONFIG_FILENAME).toAbsolutePath();

    private static Config configInstance;
    private static boolean newlyCreated;

    public static Config config() {
        if (configInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return configInstance;
    }

    public static boolean isNewlyCreated() {
        if (configInstance == null) {
            throw new IllegalStateException("Configuration hasn't been loaded");
        }
        return newlyCreated;
    }

    public synchronized static void init() {
        if (configInstance != null) {
            throw new IllegalStateException("Configuration is already loaded");
        }

        configInstance = loadConfig();
        configInstance.addListener(source -> saveConfig());

        Settings.init();

        if (newlyCreated) {
            saveConfig();
        }
    }

    private static Config loadConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String content = new String(Files.readAllBytes(CONFIG_PATH), UTF_8);
                Config deserialized = Config.fromJson(content);
                if (deserialized == null) {
                    LOG.info("Config is empty");
                } else {
                    Map<?, ?> raw = new Gson().fromJson(content, Map.class);
                    return upgradeConfig(deserialized, raw);
                }
            } catch (IOException | JsonParseException e) {
                LOG.log(Level.WARNING, "Something went wrong when loading config.", e);
            }
        }

        LOG.info("Creating an empty config");
        newlyCreated = true;
        return new Config();
    }

    static void saveConfig() {
        LOG.info("Saving config");
        try {
            Files.write(CONFIG_PATH, configInstance.toJson().getBytes(UTF_8));
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Failed to save config", ex);
        }
    }

    private static Config upgradeConfig(Config deserialized, Map<?, ?> rawJson) {
        if (!rawJson.containsKey("commonDirType"))
            deserialized.setCommonDirType(deserialized.getCommonDirectory().equals(Settings.getDefaultCommonDirectory()) ? EnumCommonDirectory.DEFAULT : EnumCommonDirectory.CUSTOM);
        return deserialized;
    }
}
