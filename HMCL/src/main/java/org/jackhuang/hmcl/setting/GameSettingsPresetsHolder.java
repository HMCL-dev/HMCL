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

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns loading and saving of detached game settings presets.
///
/// The preset file is stored separately from the main `settings.json` file as
/// `game-settings-presets.json`. When the main config still contains embedded preset fields, this
/// holder moves them into the detached file and rewrites the main config without those fields.
///
/// @author Glavo
@NotNullByDefault
public final class GameSettingsPresetsHolder {
    /// The current per-workspace game settings preset path.
    private static final Path LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-settings-presets.json");

    /// Prevents instantiation.
    private GameSettingsPresetsHolder() {
    }

    /// Returns the current per-workspace game settings preset path.
    public static Path location() {
        return LOCATION;
    }

    /// Loads game settings presets and installs the save listener.
    static void init() throws IOException {
        LOG.info("Game settings presets location: " + LOCATION);

        Config config = config();
        boolean newlyCreated = load(config);
        if (!ConfigHolder.isUnsupportedVersion()) {
            config.gameSettingPresets().addListener(source ->
                    FileSaver.save(LOCATION, config.gameSettingPresets().toJson()));
        }

        if (newlyCreated) {
            LOG.info("Creating game settings presets file " + LOCATION);
            FileUtils.saveSafely(LOCATION, config.gameSettingPresets().toJson());
        }

        if (!ConfigHolder.isUnsupportedVersion()
                && !ConfigHolder.isNewlyCreated()
                && config.hasEmbeddedGameSettingsPresetsLoaded()) {
            LOG.info("Removing embedded game settings presets from config file " + ConfigHolder.configLocation());
            FileUtils.saveSafely(ConfigHolder.configLocation(), config.toJson());
        }

        checkWritable(LOCATION);
    }

    /// Loads the detached game settings preset file.
    ///
    /// Returns `true` when the preset file is missing and should be created from any presets that
    /// were embedded in the main config or produced by legacy migration.
    private static boolean load(Config config) throws IOException {
        if (Files.exists(LOCATION)) {
            try {
                JsonObject jsonObject = readJsonObject(LOCATION);
                if (jsonObject == null) {
                    LOG.info("Game setting presets are empty");
                } else {
                    GameSettingsPresets deserialized = GameSettingsPresets.fromJson(jsonObject);
                    if (deserialized == null) {
                        LOG.info("Game setting presets are empty");
                    } else {
                        config.setGameSettingsPresets(deserialized);
                        return false;
                    }
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed game setting presets.", e);
            }

            config.setGameSettingsPresets(new GameSettingsPresets());
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

    /// Checks that the given preset file is writable.
    private static void checkWritable(Path location) throws IOException {
        if (!Files.isWritable(location)) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    && location.getFileSystem() == FileSystems.getDefault()
                    && location.toFile().canWrite()) {
                LOG.warning("Config at " + location
                        + " is not writable, but it seems to be a Samba share or OpenJDK bug");
                // There are some serious problems with the implementation of Samba or OpenJDK
                throw new SambaException();
            } else {
                // the config cannot be saved
                // throw up the error now to prevent further data loss
                throw new IOException("Config at " + location + " is not writable");
            }
        }
    }
}
