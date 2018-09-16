/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.IconedItem;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

import java.util.function.Consumer;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListItemSkin extends SkinBase<GameListItem> {

    public GameListItemSkin(GameListItem skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();

        JFXRadioButton chkSelected = new JFXRadioButton();
        BorderPane.setAlignment(chkSelected, Pos.CENTER);
        chkSelected.setUserData(skinnable);
        chkSelected.selectedProperty().bindBidirectional(skinnable.selectedProperty());
        chkSelected.setToggleGroup(skinnable.getToggleGroup());
        root.setLeft(chkSelected);

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
        BorderPane.setAlignment(item, Pos.CENTER);
        center.getChildren().setAll(imageView, item);
        root.setCenter(center);

        VBox menu = new VBox();
        JFXPopup popup = new JFXPopup(menu);

        Function<Runnable, Runnable> wrap = r -> () -> {
            r.run();
            popup.hide();
        };

        Function<Node, Node> limitWidth = node -> {
            StackPane pane = new StackPane(node);
            pane.setAlignment(Pos.CENTER);
            FXUtils.setLimitWidth(pane, 14);
            FXUtils.setLimitHeight(pane, 14);
            return pane;
        };

        menu.getChildren().setAll(
                new IconedMenuItem(limitWidth.apply(SVG.gear(Theme.blackFillBinding(), 14, 14)), i18n("version.manage.manage"), wrap.apply(skinnable::modifyGameSettings)),
                new IconedMenuItem(limitWidth.apply(SVG.pencil(Theme.blackFillBinding(), 14, 14)), i18n("version.manage.rename"), wrap.apply(skinnable::rename)),
                new IconedMenuItem(limitWidth.apply(SVG.delete(Theme.blackFillBinding(), 14, 14)), i18n("version.manage.remove"), wrap.apply(skinnable::remove)),
                new IconedMenuItem(limitWidth.apply(SVG.export(Theme.blackFillBinding(), 14, 14)), i18n("modpack.export"), wrap.apply(skinnable::export)),
                new IconedMenuItem(limitWidth.apply(SVG.folderOpen(Theme.blackFillBinding(), 14, 14)), i18n("folder.game"), wrap.apply(skinnable::browse)),
                new IconedMenuItem(limitWidth.apply(SVG.launch(Theme.blackFillBinding(), 14, 14)), i18n("version.launch"), wrap.apply(skinnable::launch)),
                new IconedMenuItem(limitWidth.apply(SVG.script(Theme.blackFillBinding(), 14, 14)), i18n("version.launch_script"), wrap.apply(skinnable::generateLaunchScript)));

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);
        if (skinnable.canUpdate()) {
            JFXButton btnUpgrade = new JFXButton();
            btnUpgrade.setOnMouseClicked(e -> skinnable.update());
            btnUpgrade.getStyleClass().add("toggle-icon4");
            btnUpgrade.setGraphic(SVG.update(Theme.blackFillBinding(), -1, -1));
            JFXUtilities.runInFX(() -> FXUtils.installTooltip(btnUpgrade, i18n("version.update")));
            right.getChildren().add(btnUpgrade);
        }

        JFXButton btnManage = new JFXButton();
        btnManage.setOnMouseClicked(e -> {
            popup.show(root, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight());
        });
        btnManage.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnManage, Pos.CENTER);
        btnManage.setGraphic(SVG.dotsVertical(Theme.blackFillBinding(), -1, -1));
        right.getChildren().add(btnManage);
        root.setRight(right);

        root.setStyle("-fx-background-color: white; -fx-padding: 8 8 8 0;");
        JFXDepthManager.setDepth(root, 1);
        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());

        getChildren().setAll(root);
    }
}
