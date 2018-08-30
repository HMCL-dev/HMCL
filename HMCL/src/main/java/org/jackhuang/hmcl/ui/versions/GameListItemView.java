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
import com.jfoenix.controls.*;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.TwoLineListItem;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListItemView extends BorderPane {

    public GameListItemView(GameListItemViewModel viewModel) {
        JFXRadioButton chkSelected = new JFXRadioButton();
        BorderPane.setAlignment(chkSelected, Pos.CENTER);
        chkSelected.setUserData(viewModel);
        chkSelected.selectedProperty().bindBidirectional(viewModel.selectedProperty());
        chkSelected.setToggleGroup(viewModel.getToggleGroup());
        setLeft(chkSelected);

        HBox center = new HBox();
        center.setSpacing(8);
        center.setAlignment(Pos.CENTER_LEFT);

        StackPane imageViewContainer = new StackPane();
        FXUtils.setLimitWidth(imageViewContainer, 32);
        FXUtils.setLimitHeight(imageViewContainer, 32);

        ImageView imageView = new ImageView();
        FXUtils.limitSize(imageView, 32, 32);
        imageView.imageProperty().bind(viewModel.imageProperty());
        imageViewContainer.getChildren().setAll(imageView);

        TwoLineListItem item = new TwoLineListItem();
        BorderPane.setAlignment(item, Pos.CENTER);
        center.getChildren().setAll(imageView, item);
        setCenter(center);

        JFXListView<String> menu = new JFXListView<>();
        menu.getItems().setAll(
                i18n("settings"),
                i18n("version.manage.rename"),
                i18n("version.manage.remove"),
                i18n("modpack.export"),
                i18n("folder.game"),
                i18n("version.launch"),
                i18n("version.launch_script"));
        JFXPopup popup = new JFXPopup(menu);
        menu.setOnMouseClicked(e -> {
            popup.hide();
            switch (menu.getSelectionModel().getSelectedIndex()) {
                case 0:
                    viewModel.modifyGameSettings();
                    break;
                case 1:
                    viewModel.rename();
                    break;
                case 2:
                    viewModel.remove();
                    break;
                case 3:
                    viewModel.export();
                    break;
                case 4:
                    viewModel.browse();
                    break;
                case 5:
                    viewModel.launch();
                    break;
                case 6:
                    viewModel.generateLaunchScript();
                    break;
            }
        });

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);
        if (viewModel.canUpdate()) {
            JFXButton btnUpgrade = new JFXButton();
            btnUpgrade.setOnMouseClicked(e -> viewModel.update());
            btnUpgrade.getStyleClass().add("toggle-icon4");
            btnUpgrade.setGraphic(SVG.update(Theme.blackFillBinding(), -1, -1));
            JFXUtilities.runInFX(() -> FXUtils.installTooltip(btnUpgrade, i18n("version.update")));
            right.getChildren().add(btnUpgrade);
        }

        JFXButton btnManage = new JFXButton();
        btnManage.setOnMouseClicked(e -> {
            menu.getSelectionModel().select(-1);
            popup.show(this, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, this.getHeight());
        });
        btnManage.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnManage, Pos.CENTER);
        btnManage.setGraphic(SVG.dotsVertical(Theme.blackFillBinding(), -1, -1));
        right.getChildren().add(btnManage);
        setRight(right);

        setStyle("-fx-background-color: white; -fx-padding: 8 8 8 0;");
        JFXDepthManager.setDepth(this, 1);
        item.titleProperty().bind(viewModel.titleProperty());
        item.subtitleProperty().bind(viewModel.subtitleProperty());
    }
}
