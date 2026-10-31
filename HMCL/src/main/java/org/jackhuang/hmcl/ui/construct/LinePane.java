// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.scene.Node;
import org.jetbrains.annotations.Nullable;

/// @author Glavo
public class LinePane extends LineComponent {
    private static final String DEFAULT_STYLE_CLASS = "line-pane";

    public LinePane() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /// The optional right-side node, forwarded directly to the row.
    private @Nullable ObjectProperty<@Nullable Node> right;

    /// Returns the right-side node property; null removes the node.
    public ObjectProperty<@Nullable Node> rightProperty() {
        if (right == null) {
            right = new ObjectPropertyBase<>() {
                /// Returns the pane owning this property.
                @Override
                public Object getBean() {
                    return LinePane.this;
                }

                /// Returns the property name.
                @Override
                public String getName() {
                    return "right";
                }

                /// Replaces the row's trailing node without introducing a container.
                @Override
                protected void invalidated() {
                    container.setTrailing(get());
                }
            };
        }
        return right;
    }

    /// Returns the right-side node, or null if none is assigned.
    public @Nullable Node getRight() {
        return rightProperty().get();
    }

    /// Sets the right-side node; null removes it.
    public void setRight(@Nullable Node right) {
        rightProperty().set(right);
    }
}
