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
package org.jackhuang.hmcl.ui.directory;

import javafx.beans.binding.Bindings;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;

import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Navigation drawer item for one game directory entry.
@NotNullByDefault
public class GameDirectoryListItem extends RadioButton {
    /// Game directory represented by this item.
    private final GameDirectory gameDirectory;

    /// Creates a list item for the given game directory.
    ///
    /// @param gameDirectory the represented game directory
    public GameDirectoryListItem(GameDirectory gameDirectory) {
        this.gameDirectory = gameDirectory;
        getStyleClass().setAll("game-directory-list-item", "navigation-drawer-item");
        setUserData(gameDirectory);

        this.selectedProperty().bind(Bindings.equal(gameDirectory, GameDirectoryManager.selectedGameDirectoryProperty()));
    }

    /// Creates the JavaFX skin for this item.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameDirectoryListItemSkin(this);
    }

    /// Removes the represented game directory after handling read-only storage.
    public void remove() {
        if (!GameDirectoryManager.canRemoveGameDirectory(gameDirectory)) {
            Controllers.confirmBackupAndOverwrite(i18n("settings.game_directories.read_only"), () -> {
                GameDirectoryManager.forceOverwriteGameDirectoryFiles(gameDirectory);
                GameDirectoryManager.removeGameDirectory(gameDirectory);
            });
            return;
        }

        GameDirectoryManager.removeGameDirectory(gameDirectory);
    }

    /// Returns the represented game directory.
    public GameDirectory getGameDirectory() {
        return gameDirectory;
    }

}
