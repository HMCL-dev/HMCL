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
import org.jackhuang.hmcl.util.GUID;
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.*;

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

        LOG.info("Game settings presets location: " + LOCATION);

        boolean newlyCreated = !Files.exists(LOCATION);
        LoadResult result = load(migratedGameSettingsPresets);
        gameSettingsPresets = result.presets();
        if (allowSave && result.allowSave()) {
            gameSettingsPresets.addListener(source -> FileSaver.save(LOCATION, gameSettingsPresets.toJson()));
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating game settings presets file " + LOCATION);
            FileSaver.save(LOCATION, gameSettingsPresets.toJson());
        }
    }

    /// Loads the detached game settings preset file, falling back to migrated presets when the file is absent.
    private static LoadResult load(@Nullable GameSettingsPresets migratedGameSettingsPresets) throws IOException {
        if (Files.exists(LOCATION)) {
            try {
                JsonObject jsonObject = JsonUtils.fromJsonFile(LOCATION, JsonObject.class);
                if (jsonObject == null) {
                    LOG.info("Game setting presets are empty");
                } else {
                    JsonFileFormat.CheckResult format =
                            JsonFileFormat.check(jsonObject, GameSettingsPresets.CURRENT_FORMAT);
                    if (format.isMissing()) {
                        LOG.warning("Missing format in game settings presets: " + LOCATION);
                        return new LoadResult(new GameSettingsPresets(), false);
                    } else if (format.isInvalid()) {
                        LOG.warning("Invalid format in game settings presets: "
                                + LOCATION + ", Actual: " + format.invalidValue());
                        return new LoadResult(new GameSettingsPresets(), false);
                    } else if (format.isUnexpectedId()) {
                        LOG.warning("Unexpected game settings presets format. Expected: "
                                + GameSettingsPresets.CURRENT_FORMAT + ", Actual: " + format.actual());
                        return new LoadResult(new GameSettingsPresets(), false);
                    } else if (format.isNewerThanExpected()) {
                        LOG.warning("Unsupported game settings presets format. Expected: "
                                + GameSettingsPresets.CURRENT_FORMAT + ", Actual: " + format.actual());
                        if (format.hasNewerMajorVersion()) {
                            return new LoadResult(new GameSettingsPresets(), false);
                        }
                    }

                    GameSettingsPresets deserialized = GameSettingsPresets.fromJson(jsonObject);
                    if (deserialized != null) {
                        if (!GameSettingsPresets.CURRENT_FORMAT.equals(deserialized.getFormat())) {
                            deserialized.setFormat(GameSettingsPresets.CURRENT_FORMAT);
                        }

                        return new LoadResult(deserialized, !format.isNewerThanExpected());
                    }

                    LOG.info("Game setting presets are empty");
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed game setting presets.", e);
            }

            return new LoadResult(new GameSettingsPresets(), true);
        }

        return new LoadResult(
                migratedGameSettingsPresets != null ? migratedGameSettingsPresets : new GameSettingsPresets(), true);
    }

    /// Result of loading the detached preset store.
    ///
    /// @param presets the loaded preset store
    /// @param allowSave whether the preset file may be overwritten
    private record LoadResult(GameSettingsPresets presets, boolean allowSave) {
    }
}
