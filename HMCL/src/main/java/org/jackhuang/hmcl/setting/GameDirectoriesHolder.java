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

    /// The detached game directory file helper.
    private static final JsonSettingFile<GameDirectories> FILE = new JsonSettingFile<>(
            LOCATION,
            "game directories",
            GameDirectories.class,
            GameDirectories.CURRENT_FORMAT,
            GameDirectories::new);

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
        JsonSettingFile.LoadResult<GameDirectories> result = FILE.load(migratedGameDirectories);
        gameDirectories = result.value();
        if (allowSave && result.allowSave()) {
            FILE.installAutoSave(gameDirectories);
        }

        if (newlyCreated && allowSave && result.allowSave()) {
            LOG.info("Creating game directories file " + LOCATION);
            FILE.save(gameDirectories);
        }
    }
}
