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
package org.jackhuang.hmcl.ui.construct;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;

public class AdvancedListItemSkin extends SkinBase<AdvancedListItem> {
    private final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    public AdvancedListItemSkin(AdvancedListItem skinnable) {
        super(skinnable);

        StackPane stackPane = new StackPane();
        stackPane.getStyleClass().add("container");
        RipplerContainer container = new RipplerContainer(stackPane);

        FXUtils.onChangeAndOperate(skinnable.activeProperty(), active -> {
            skinnable.pseudoClassStateChanged(SELECTED, active);
        });

        BorderPane root = new BorderPane();
        root.setPickOnBounds(false);

        HBox left = new HBox();
        left.setAlignment(Pos.CENTER_LEFT);
        left.setMouseTransparent(true);

        VBox vbox = new VBox();
        root.setCenter(vbox);
        vbox.setMouseTransparent(true);
        vbox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label();
        title.textProperty().bind(skinnable.titleProperty());
        title.getStyleClass().add("title");
        vbox.getChildren().add(title);

        Label subtitle = new Label();
        subtitle.textProperty().bind(skinnable.subtitleProperty());
        subtitle.getStyleClass().add("subtitle");
        vbox.getChildren().add(subtitle);

        FXUtils.onChangeAndOperate(skinnable.subtitleProperty(), subtitleString -> {
            if (subtitleString == null) vbox.getChildren().setAll(title);
            else vbox.getChildren().setAll(title, subtitle);
        });

        FXUtils.onChangeAndOperate(skinnable.leftGraphicProperty(),
                newGraphic -> {
                    if (newGraphic == null) {
                        left.getChildren().clear();
                    } else {
                        left.getChildren().setAll(newGraphic);
                    }
                });
        root.setLeft(left);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER);
        right.setMouseTransparent(true);
        right.getStyleClass().add("toggle-icon4");
        FXUtils.setLimitWidth(right, 40);
        FXUtils.onChangeAndOperate(skinnable.rightGraphicProperty(),
                newGraphic -> {
                    if (newGraphic == null) {
                        right.getChildren().clear();
                    } else {
                        right.getChildren().setAll(newGraphic);
                    }
                });

        FXUtils.onChangeAndOperate(skinnable.actionButtonVisibleProperty(),
                visible -> root.setRight(visible ? right : null));

        stackPane.setPickOnBounds(false);
        stackPane.getChildren().setAll(root);

        getChildren().setAll(container);
    }
}
