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

/// Owns loading and saving of detached game directories.
///
/// The game directory file is stored separately from the main `settings.json` file as
/// `game-directories.json`.
///
/// @author Glavo
@NotNullByDefault
public final class GameDirectoriesHolder {
    /// The current per-workspace game directories path.
    private static final Path LOCATION = Metadata.HMCL_CURRENT_DIRECTORY.resolve("game-directories.json");

    /// The loaded detached game directory store.
    private static @UnknownNullability GameDirectories gameDirectories;

    /// Prevents instantiation.
    private GameDirectoriesHolder() {
    }

    /// Returns the current per-workspace game directories path.
    public static Path location() {
        return LOCATION;
    }

    /// Returns the loaded detached game directory store.
    public static GameDirectories gameDirectories() {
        if (gameDirectories == null) {
            throw new IllegalStateException("Game directories haven't been loaded");
        }
        return gameDirectories;
    }

    /// Returns the per-workspace game directories.
    public static ObservableList<Profile> getGameDirectories() {
        return gameDirectories().getGameDirectories();
    }

    /// Returns the selected game directory ID property.
    public static ObjectProperty<@Nullable GUID> selectedGameDirectoryProperty() {
        return gameDirectories().selectedGameDirectoryProperty();
    }

    /// Returns the selected game directory ID.
    public static @Nullable GUID getSelectedGameDirectory() {
        return gameDirectories().getSelectedGameDirectory();
    }

    /// Sets the selected game directory ID.
    public static void setSelectedGameDirectory(@Nullable GUID selectedGameDirectory) {
        gameDirectories().setSelectedGameDirectory(selectedGameDirectory);
    }

    /// Loads game directories and installs the save listener.
    ///
    /// @param migratedGameDirectories the game directory store migrated from a config file
    /// @param allowSave whether the detached game directory file may be overwritten
    static void init(@Nullable GameDirectories migratedGameDirectories, boolean allowSave) throws IOException {
        if (gameDirectories != null) {
            throw new IllegalStateException("Game directories are already loaded");
        }

        LOG.info("Game directories location: " + LOCATION);

        boolean newlyCreated = !Files.exists(LOCATION);
        LoadResult result = load(migratedGameDirectories);
        gameDirectories = result.gameDirectories();
        if (allowSave && result.allowSave()) {
            gameDirectories.addListener(source -> FileSaver.save(LOCATION, gameDirectories.toJson()));
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating game directories file " + LOCATION);
            FileSaver.save(LOCATION, gameDirectories.toJson());
        }
    }

    /// Loads the detached game directory file, falling back to migrated game directories when the file is absent.
    private static LoadResult load(@Nullable GameDirectories migratedGameDirectories) throws IOException {
        if (Files.exists(LOCATION)) {
            try {
                JsonObject jsonObject = JsonUtils.fromJsonFile(LOCATION, JsonObject.class);
                if (jsonObject == null) {
                    LOG.info("Game directories are empty");
                } else {
                    JsonFileFormat.CheckResult format =
                            JsonFileFormat.check(jsonObject, GameDirectories.CURRENT_FORMAT);
                    if (format.isMissing()) {
                        LOG.warning("Missing format in game directories: " + LOCATION);
                        return new LoadResult(new GameDirectories(), false);
                    } else if (format.isInvalid()) {
                        LOG.warning("Invalid format in game directories: "
                                + LOCATION + ", Actual: " + format.invalidValue());
                        return new LoadResult(new GameDirectories(), false);
                    } else if (format.isUnexpectedId()) {
                        LOG.warning("Unexpected game directories format. Expected: "
                                + GameDirectories.CURRENT_FORMAT + ", Actual: " + format.actual());
                        return new LoadResult(new GameDirectories(), false);
                    } else if (format.isNewerThanExpected()) {
                        LOG.warning("Unsupported game directories format. Expected: "
                                + GameDirectories.CURRENT_FORMAT + ", Actual: " + format.actual());
                        if (format.hasNewerMajorVersion()) {
                            return new LoadResult(new GameDirectories(), false);
                        }
                    }

                    GameDirectories deserialized = GameDirectories.fromJson(jsonObject);
                    if (deserialized != null) {
                        if (!GameDirectories.CURRENT_FORMAT.equals(deserialized.getFormat())) {
                            deserialized.setFormat(GameDirectories.CURRENT_FORMAT);
                        }

                        return new LoadResult(deserialized, !format.isNewerThanExpected());
                    }

                    LOG.info("Game directories are empty");
                }
            } catch (JsonParseException e) {
                LOG.warning("Malformed game directories.", e);
            }

            return new LoadResult(new GameDirectories(), true);
        }

        return new LoadResult(
                migratedGameDirectories != null ? migratedGameDirectories : new GameDirectories(), true);
    }

    /// Result of loading the detached game directory store.
    ///
    /// @param gameDirectories the loaded game directory store
    /// @param allowSave whether the game directory file may be overwritten
    private record LoadResult(GameDirectories gameDirectories, boolean allowSave) {
    }
}
