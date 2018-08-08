/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
    }

    private static HBox createHBox(Node icon) {
        HBox hBox = new HBox();
        icon.setMouseTransparent(true);
        Label textLabel = new Label();
        textLabel.setId("label");
        textLabel.setAlignment(Pos.CENTER);
        textLabel.setMouseTransparent(true);
        hBox.getChildren().addAll(icon, textLabel);
        hBox.setStyle("-fx-padding: 10 16 10 16; -fx-spacing: 10; -fx-font-size: 14;");
        hBox.setAlignment(Pos.CENTER_LEFT);
        return hBox;
    }

    public Label getLabel() {
        return label;
    }
}
