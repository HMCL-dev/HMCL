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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXRadioButton;
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

public class AccountListItemView extends BorderPane {

    public AccountListItemView(AccountListItemViewModel viewModel) {
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
        imageView.viewportProperty().bind(viewModel.viewportProperty());
        imageViewContainer.getChildren().setAll(imageView);

        TwoLineListItem item = new TwoLineListItem();
        BorderPane.setAlignment(item, Pos.CENTER);
        center.getChildren().setAll(imageView, item);
        setCenter(center);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);
        JFXButton btnRefresh = new JFXButton();
        btnRefresh.setOnMouseClicked(e -> viewModel.refresh());
        btnRefresh.getStyleClass().add("toggle-icon4");
        btnRefresh.setGraphic(SVG.refresh(Theme.blackFillBinding(), -1, -1));
        JFXUtilities.runInFX(() -> FXUtils.installTooltip(btnRefresh, i18n("button.refresh")));
        right.getChildren().add(btnRefresh);

        JFXButton btnRemove = new JFXButton();
        btnRemove.setOnMouseClicked(e -> viewModel.remove());
        btnRemove.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        btnRemove.setGraphic(SVG.delete(Theme.blackFillBinding(), -1, -1));
        JFXUtilities.runInFX(() -> FXUtils.installTooltip(btnRefresh, i18n("button.delete")));
        right.getChildren().add(btnRemove);
        setRight(right);

        setStyle("-fx-background-color: white; -fx-padding: 8 8 8 0;");
        JFXDepthManager.setDepth(this, 1);
        item.titleProperty().bind(viewModel.titleProperty());
        item.subtitleProperty().bind(viewModel.subtitleProperty());
    }
}
