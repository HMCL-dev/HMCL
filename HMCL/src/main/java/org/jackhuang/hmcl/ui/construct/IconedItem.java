/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class IconedItem extends RipplerContainer {

    private Label label;

    public IconedItem(Node icon, String text) {
        this(icon);
        label.setText(text);
    }

    public IconedItem(Node icon) {
        super(createHBox(icon));
        label = ((Label) lookup("#label"));
        getStyleClass().setAll("iconed-item");
    }

    private static HBox createHBox(Node icon) {
        HBox hBox = new HBox();

        if (icon != null) {
            icon.setMouseTransparent(true);
            hBox.getChildren().add(icon);
        }

        hBox.getStyleClass().add("iconed-item-container");
        Label textLabel = new Label();
        textLabel.setId("label");
        textLabel.setMouseTransparent(true);
        hBox.getChildren().addAll(textLabel);
        return hBox;
    }

    public Label getLabel() {
        return label;
    }
}
