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
package org.jackhuang.hmcl.ui.profile;

import com.jfoenix.controls.JFXPopup;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.PopupMenu;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Popup menu for ProfileListItem.
public final class ProfileListPopupMenu extends StackPane {

    /// Shows the popup menu for the given profile item.
    public static void show(Node owner, Profile profile) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);
        menu.getContent().add(new IconedMenuItem(
                org.jackhuang.hmcl.ui.SVG.EDIT,
                i18n("button.edit"),
                () -> Controllers.navigate(new ProfilePage(profile)),
                popup));
        popup.show(owner, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT, 0, 0);
    }

    public ProfileListPopupMenu() {
        getStyleClass().add("popup-menu-content");
    }
}
