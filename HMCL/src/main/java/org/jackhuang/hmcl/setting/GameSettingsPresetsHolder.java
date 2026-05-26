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

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.GUID;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Owns loading and saving of detached game settings presets.
///
/// The preset file is stored separately from the main `settings.json` file as
/// `game-settings.json`.
///
/// @author Glavo
@NotNullByDefault
public final class GameSettingsPresetsHolder {
    /// The current per-workspace game settings path.
    private static final Path LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-settings.json");

    /// The detached game settings file helper.
    private static final JsonSettingFile<GameSettingsPresets> FILE = new JsonSettingFile<>(
            LOCATION,
            "game settings",
            GameSettingsPresets.class,
            GameSettingsPresets.CURRENT_FORMAT,
            GameSettingsPresets::new);

    /// The loaded detached preset store.
    private static @UnknownNullability GameSettingsPresets gameSettingsPresets;

    /// Prevents instantiation.
    private GameSettingsPresetsHolder() {
    }

    /// Returns the current per-workspace game settings path.
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
    public static ObjectProperty<@Nullable GUID> defaultGameSettingsProperty() {
        return gameSettingsPresets().defaultGameSettingsProperty();
    }

    /// Returns the default game setting preset ID.
    public static @Nullable GUID getDefaultGameSettings() {
        return gameSettingsPresets().getDefaultGameSettings();
    }

    /// Sets the default game setting preset ID.
    public static void setDefaultGameSettings(@Nullable GUID defaultGameSettings) {
        gameSettingsPresets().setDefaultGameSettings(defaultGameSettings);
    }

    /// Returns the game setting preset with the given ID.
    public static GameSettings.@Nullable Preset getGameSettings(@Nullable GUID id) {
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

        LOG.info("Game settings location: " + LOCATION);

        boolean newlyCreated = !Files.exists(LOCATION);
        JsonSettingFile.LoadResult<GameSettingsPresets> result = FILE.load(migratedGameSettingsPresets);
        gameSettingsPresets = result.value();
        if (allowSave && result.allowSave()) {
            FILE.installAutoSave(gameSettingsPresets);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating game settings file " + LOCATION);
            FILE.save(gameSettingsPresets);
        }
    }
}
