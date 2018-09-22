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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

public class AdvancedListItemSkin extends SkinBase<AdvancedListItem> {

    public AdvancedListItemSkin(AdvancedListItem skinnable) {
        super(skinnable);

        StackPane stackPane = new StackPane();

        BorderPane root = new BorderPane();
        root.setPickOnBounds(false);

        HBox left = new HBox();
        left.setAlignment(Pos.CENTER);
        left.setMouseTransparent(true);

        StackPane imageViewContainer = new StackPane();
        FXUtils.setLimitWidth(imageViewContainer, 32);
        FXUtils.setLimitHeight(imageViewContainer, 32);

        ImageView imageView = new ImageView();
        FXUtils.limitSize(imageView, 32, 32);
        imageView.setPreserveRatio(true);
        imageView.imageProperty().bind(skinnable.imageProperty());
        imageView.viewportProperty().bind(skinnable.viewportProperty());
        imageViewContainer.getChildren().setAll(imageView);

        VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER_LEFT);
        vbox.setPadding(new Insets(0, 0, 0, 10));

        Label title = new Label();
        title.textProperty().bind(skinnable.titleProperty());
        title.setMaxWidth(90);
        title.setStyle("-fx-font-size: 15;");
        title.setTextAlignment(TextAlignment.JUSTIFY);
        vbox.getChildren().add(title);

        Label subtitle = new Label();
        subtitle.textProperty().bind(skinnable.subtitleProperty());
        subtitle.setMaxWidth(90);
        subtitle.setStyle("-fx-font-size: 10;");
        subtitle.setTextAlignment(TextAlignment.JUSTIFY);
        vbox.getChildren().add(subtitle);

        FXUtils.onChangeAndOperate(skinnable.subtitleProperty(), subtitleString -> {
            if (subtitleString == null) vbox.getChildren().setAll(title);
            else vbox.getChildren().setAll(title, subtitle);
        });

        left.getChildren().setAll(imageViewContainer, vbox);
        root.setLeft(left);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER);
        right.setPickOnBounds(false);

        JFXButton settings = new JFXButton();
        FXUtils.setLimitWidth(settings, 40);
        settings.getStyleClass().setAll("toggle-icon4");
        settings.setGraphic(SVG.gear(Theme.blackFillBinding(), -1, -1));
        right.getChildren().setAll(settings);
        root.setRight(right);

        stackPane.setStyle("-fx-padding: 10 16 10 16;");
        stackPane.getStyleClass().setAll("transparent");
        stackPane.setPickOnBounds(false);
        stackPane.getChildren().setAll(root);

        getChildren().setAll(stackPane);
    }
}
