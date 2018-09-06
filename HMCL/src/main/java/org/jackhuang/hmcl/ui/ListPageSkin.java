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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXScrollPane;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Theme;

public class ListPageSkin extends SkinBase<ListPage> {

    public ListPageSkin(ListPage<?> skinnable) {
        super(skinnable);

        StackPane root = new StackPane();

        ScrollPane scrollPane = new ScrollPane();
        {
            scrollPane.setFitToWidth(true);

            VBox accountList = new VBox();
            accountList.maxWidthProperty().bind(scrollPane.widthProperty());
            accountList.setSpacing(10);
            accountList.setStyle("-fx-padding: 10 10 10 10;");

            Bindings.bindContent(accountList.getChildren(), skinnable.itemsProperty());

            scrollPane.setContent(accountList);
            JFXScrollPane.smoothScrolling(scrollPane);
        }

        VBox vBox = new VBox();
        {
            vBox.setAlignment(Pos.BOTTOM_RIGHT);
            vBox.setPickOnBounds(false);
            vBox.setPadding(new Insets(15));
            vBox.setSpacing(15);

            JFXButton btnAdd = new JFXButton();
            FXUtils.setLimitWidth(btnAdd, 40);
            FXUtils.setLimitHeight(btnAdd, 40);
            btnAdd.getStyleClass().setAll("jfx-button-raised-round");
            btnAdd.setButtonType(JFXButton.ButtonType.RAISED);
            btnAdd.setGraphic(SVG.plus(Theme.whiteFillBinding(), -1, -1));
            btnAdd.setOnMouseClicked(e -> skinnable.add());

            vBox.getChildren().setAll(btnAdd);
        }

        root.getChildren().setAll(scrollPane, vBox);

        getChildren().setAll(root);
    }
}
