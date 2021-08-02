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

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

public class ProfileListItemSkin extends SkinBase<ProfileListItem> {

    public ProfileListItemSkin(ProfileListItem skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();

        TwoLineListItem item = new TwoLineListItem();
        BorderPane.setAlignment(item, Pos.CENTER);
        root.setCenter(item);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);

        JFXButton btnRemove = new JFXButton();
        btnRemove.setOnMouseClicked(e -> skinnable.remove());
        btnRemove.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        btnRemove.setGraphic(SVG.delete(Theme.blackFillBinding(), -1, -1));
        right.getChildren().add(btnRemove);
        root.setRight(right);

        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());

        getChildren().setAll(root);
    }
}
