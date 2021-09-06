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
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;

public class AdvancedListItemSkin extends SkinBase<AdvancedListItem> {
    private final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    public AdvancedListItemSkin(AdvancedListItem skinnable) {
        super(skinnable);

        FXUtils.onChangeAndOperate(skinnable.activeProperty(), active -> {
            skinnable.pseudoClassStateChanged(SELECTED, active);
        });

        BorderPane root = new BorderPane();
        root.getStyleClass().add("container");
        root.setPickOnBounds(false);

        RipplerContainer container = new RipplerContainer(root);

        HBox left = new HBox();
        left.setAlignment(Pos.CENTER_LEFT);
        left.setMouseTransparent(true);

        TwoLineListItem item = new TwoLineListItem();
        root.setCenter(item);
        item.setMouseTransparent(true);
        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());

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

        getChildren().setAll(container);
    }
}
