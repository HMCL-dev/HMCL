// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.jackhuang.hmcl.util.Lang;

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
        vbox.getChildren().add(rectangle);
        getChildren().setAll(vbox);
        getStyleClass().add("class-title");
    }

    public ClassTitle(String text, Node rightNode) {
        this(Lang.apply(new BorderPane(), borderPane -> {
            borderPane.setLeft(Lang.apply(new VBox(), vBox -> vBox.getChildren().setAll(new Text(text))));
            borderPane.setRight(rightNode);
        }));
    }

    public Node getContent() {
        return content;
    }
}
