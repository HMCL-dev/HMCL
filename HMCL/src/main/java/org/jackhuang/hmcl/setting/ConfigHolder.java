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
import java.util.logging.Level;

import com.google.gson.JsonParseException;

public final class ConfigHolder {

    private ConfigHolder() {}

    public static final String CONFIG_FILENAME = "hmcl.json";
    public static final Path CONFIG_PATH = Paths.get(CONFIG_FILENAME).toAbsolutePath();
    public static final Config CONFIG = initSettings();

    private static Config initSettings() {
        Config config = new Config();
        if (Files.exists(CONFIG_PATH)) {
            try {
                Config deserialized = Config.fromJson(new String(Files.readAllBytes(CONFIG_PATH), UTF_8));
                if (deserialized == null) {
                    LOG.finer("Settings file is empty, use the default settings.");
                } else {
                    config = deserialized;
                }
                LOG.finest("Initialized settings.");
            } catch (IOException | JsonParseException e) {
                LOG.log(Level.WARNING, "Something happened wrongly when load settings.", e);
            }
        }
        return config;
    }

    static void saveConfig(Config config) {
        try {
            Files.write(CONFIG_PATH, config.toJson().getBytes(UTF_8));
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Failed to save config", ex);
        }
    }

}
