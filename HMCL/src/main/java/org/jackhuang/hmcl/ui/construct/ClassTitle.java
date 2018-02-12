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

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 * @author huangyuhui
 */
public class ClassTitle extends StackPane {
    private final Node content;

    public ClassTitle(String text) {
        this(new Text(text));
    }

    public ClassTitle(Node content) {
        this.content = content;

        VBox vbox = new VBox();
        vbox.getChildren().addAll(content);
        Rectangle rectangle = new Rectangle();
        rectangle.widthProperty().bind(vbox.widthProperty());
        rectangle.setHeight(1.0);
        rectangle.setFill(Color.GRAY);
        vbox.getChildren().add(rectangle);
        getChildren().setAll(vbox);
        getStyleClass().add("class-title");
    }

    public Node getContent() {
        return content;
    }
}
