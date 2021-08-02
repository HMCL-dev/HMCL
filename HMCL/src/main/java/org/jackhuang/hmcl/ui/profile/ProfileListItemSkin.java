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
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.versions.VersionPage;

public class ProfileListItemSkin extends SkinBase<ProfileListItem> {
    private final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    public ProfileListItemSkin(ProfileListItem skinnable) {
        super(skinnable);


        BorderPane root = new BorderPane();
        root.setPickOnBounds(false);
        RipplerContainer container = new RipplerContainer(root);

        FXUtils.onChangeAndOperate(skinnable.selectedProperty(), active -> {
            skinnable.pseudoClassStateChanged(SELECTED, active);
        });

        getSkinnable().addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            getSkinnable().setSelected(true);
        });

        Node left = VersionPage.wrap(SVG.folderOutline(Theme.blackFillBinding(), 24, 24));
        root.setLeft(left);
        BorderPane.setAlignment(left, Pos.CENTER_LEFT);

        TwoLineListItem item = new TwoLineListItem();
        item.setPickOnBounds(false);
        BorderPane.setAlignment(item, Pos.CENTER);
        root.setCenter(item);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);

        JFXButton btnRemove = new JFXButton();
        btnRemove.setOnMouseClicked(e -> skinnable.remove());
        btnRemove.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        btnRemove.setGraphic(SVG.close(Theme.blackFillBinding(), 14, 14));
        right.getChildren().add(btnRemove);
        root.setRight(right);

        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());

        getChildren().setAll(container);
    }
}
