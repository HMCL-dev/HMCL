/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXPopup;
import javafx.scene.Node;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Context menu for game directory list items.
@NotNullByDefault
public final class GameDirectoryListPopupMenu {

    private GameDirectoryListPopupMenu() {}

    /// Shows a context popup with an edit action for the given game directory.
    ///
    /// @param owner the UI node used as the popup anchor
    /// @param gameDirectory the game directory to edit when the action is triggered
    public static void show(Node owner, GameDirectory gameDirectory) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);
        menu.getContent().add(new IconedMenuItem(
                SVG.EDIT,
                i18n("button.edit"),
                () -> Controllers.navigate(new GameDirectoryPage(gameDirectory)),
                popup));
        JFXPopup.PopupVPosition vPosition = FXUtils.determineOptimalPopupPosition(owner, popup);
        popup.show(owner, vPosition, JFXPopup.PopupHPosition.LEFT, 0, 0);
    }
}
