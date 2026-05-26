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
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns loading and saving of detached game settings presets.
///
/// The preset file is stored separately from the main `settings.json` file as
/// `game-settings-presets.json`.
///
/// @author Glavo
@NotNullByDefault
public final class GameSettingsPresetsHolder {
    /// The current per-workspace game settings preset path.
    private static final Path LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-settings-presets.json");

    /// The loaded detached preset store.
    private static @UnknownNullability GameSettingsPresets gameSettingsPresets;

    /// Prevents instantiation.
    private GameSettingsPresetsHolder() {
    }

    /// Returns the current per-workspace game settings preset path.
    public static Path location() {
        return LOCATION;
    }

    /// Returns the loaded detached preset store.
    public static GameSettingsPresets gameSettingsPresets() {
        if (gameSettingsPresets == null) {
            throw new IllegalStateException("Game settings presets haven't been loaded");
        }
        return gameSettingsPresets;
    }

    /// Returns the reusable game setting presets.
    public static ObservableList<GameSettings.Preset> getGameSettings() {
        return gameSettingsPresets().getGameSettings();
    }

    /// Returns the default game setting preset ID property.
    public static ObjectProperty<@Nullable UUID> defaultGameSettingsProperty() {
        return gameSettingsPresets().defaultGameSettingsProperty();
    }

    /// Returns the default game setting preset ID.
    public static @Nullable UUID getDefaultGameSettings() {
        return gameSettingsPresets().getDefaultGameSettings();
    }

    /// Sets the default game setting preset ID.
    public static void setDefaultGameSettings(@Nullable UUID defaultGameSettings) {
        gameSettingsPresets().setDefaultGameSettings(defaultGameSettings);
    }

    /// Returns the game setting preset with the given ID.
    public static GameSettings.@Nullable Preset getGameSettings(@Nullable UUID id) {
        return gameSettingsPresets().getGameSettings(id);
    }

    /// Returns the default game setting preset, creating one when needed.
    public static GameSettings.Preset getDefaultGameSettingsOrCreate() {
        return gameSettingsPresets().getDefaultGameSettingsOrCreate();
    }

    /// Loads game settings presets and installs the save listener.
    ///
    /// @param migratedGameSettingsPresets the preset store migrated from a legacy config file
    /// @param allowSave whether the detached preset file may be overwritten
    static void init(@Nullable GameSettingsPresets migratedGameSettingsPresets, boolean allowSave) throws IOException {
        if (gameSettingsPresets != null) {
            throw new IllegalStateException("Game settings presets are already loaded");
        }

        LOG.info("Game settings presets location: " + LOCATION);

        boolean newlyCreated = !Files.exists(LOCATION);
        gameSettingsPresets = load(migratedGameSettingsPresets);
        if (allowSave) {
            gameSettingsPresets.addListener(source -> FileSaver.save(LOCATION, gameSettingsPresets.toJson()));
        }

        if (newlyCreated) {
            LOG.info("Creating game settings presets file " + LOCATION);
            FileUtils.saveSafely(LOCATION, gameSettingsPresets.toJson());
        }

        checkWritable(LOCATION);
    }

    /// Loads the detached game settings preset file, falling back to migrated presets when the file is absent.
    private static GameSettingsPresets load(@Nullable GameSettingsPresets migratedGameSettingsPresets) throws IOException {
        if (Files.exists(LOCATION)) {
            try {
                JsonObject jsonObject = JsonUtils.fromJsonFile(LOCATION, JsonObject.class);
                if (jsonObject == null) {
                    LOG.info("Game setting presets are empty");
                } else {
                    GameSettingsPresets deserialized = GameSettingsPresets.fromJson(jsonObject);
                    if (deserialized == null) {
                        LOG.info("Game setting presets are empty");
                    } else {
                        return deserialized;
                    }
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed game setting presets.", e);
            }

            return new GameSettingsPresets();
        }

        return migratedGameSettingsPresets != null ? migratedGameSettingsPresets : new GameSettingsPresets();
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
