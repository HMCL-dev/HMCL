// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

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
