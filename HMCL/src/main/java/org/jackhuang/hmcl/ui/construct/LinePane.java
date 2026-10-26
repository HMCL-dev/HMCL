// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.scene.Node;

/// @author Glavo
public class LinePane extends LineComponent {
    private static final String DEFAULT_STYLE_CLASS = "line-pane";

    public LinePane() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    private ObjectProperty<Node> right;

    public ObjectProperty<Node> rightProperty() {
        if (right == null) {
            right = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return LinePane.this;
                }

                @Override
                public String getName() {
                    return "right";
                }

                @Override
                protected void invalidated() {
                    setNode(IDX_TRAILING, get());
                }
            };
        }
        return right;
    }

    public Node getRight() {
        return rightProperty().get();
    }

    public void setRight(Node right) {
        rightProperty().set(right);
    }
}
