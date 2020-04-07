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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldListItemSkin extends SkinBase<WorldListItem> {

    public WorldListItemSkin(WorldListItem skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();

        HBox center = new HBox();
        center.setSpacing(8);
        center.setAlignment(Pos.CENTER_LEFT);

        StackPane imageViewContainer = new StackPane();
        FXUtils.setLimitWidth(imageViewContainer, 32);
        FXUtils.setLimitHeight(imageViewContainer, 32);

        ImageView imageView = new ImageView();
        FXUtils.limitSize(imageView, 32, 32);
        imageView.imageProperty().bind(skinnable.imageProperty());
        imageViewContainer.getChildren().setAll(imageView);

        TwoLineListItem item = new TwoLineListItem();
        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());
        BorderPane.setAlignment(item, Pos.CENTER);
        center.getChildren().setAll(imageView, item);
        root.setCenter(center);

        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);

        menu.getContent().setAll(
                new IconedMenuItem(FXUtils.limitingSize(SVG.gear(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("world.datapack"), FXUtils.withJFXPopupClosing(skinnable::manageDatapacks, popup)),
                new IconedMenuItem(FXUtils.limitingSize(SVG.export(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("world.export"), FXUtils.withJFXPopupClosing(skinnable::export, popup)),
                new IconedMenuItem(FXUtils.limitingSize(SVG.folderOpen(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("world.reveal"), FXUtils.withJFXPopupClosing(skinnable::reveal, popup)));

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);

        JFXButton btnManage = new JFXButton();
        btnManage.setOnMouseClicked(e -> {
            popup.show(root, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight());
        });
        btnManage.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnManage, Pos.CENTER);
        btnManage.setGraphic(SVG.dotsVertical(Theme.blackFillBinding(), -1, -1));
        right.getChildren().add(btnManage);
        root.setRight(right);

        root.getStyleClass().add("card");
        root.setStyle("-fx-padding: 8 8 8 0");
        JFXDepthManager.setDepth(root, 1);

        getChildren().setAll(root);
    }
}
