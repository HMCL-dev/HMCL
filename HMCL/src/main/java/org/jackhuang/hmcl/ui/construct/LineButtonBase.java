// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import org.jackhuang.hmcl.ui.FXUtils;

/// @author Glavo
public abstract class LineButtonBase extends LineComponent {
    private static final String DEFAULT_STYLE_CLASS = "line-button-base";

    protected final RipplerContainer ripplerContainer;

    public LineButtonBase() {
        this.getStyleClass().addAll(LineButtonBase.DEFAULT_STYLE_CLASS);

        this.ripplerContainer = new RipplerContainer(container);
        container.getChildren().addListener((ListChangeListener<Node>) change -> updateCursor());
        disabledProperty().addListener(observable -> updateCursor());
        FXUtils.setOverflowHidden(this);
        FXUtils.onClicked(this, this::fire);

        this.getChildren().setAll(ripplerContainer);
        updateCursor();
    }

    public void fire() {
        fireEvent(new ActionEvent(this, this));
    }

    /// Updates the cursor shown by the row rippler.
    private void updateCursor() {
        applyCursor(this, isDisabled() ? Cursor.DEFAULT : Cursor.HAND);
    }

    /// Applies the row cursor to every current child node that may receive mouse hover.
    private static void applyCursor(Node node, Cursor cursor) {
        node.setCursor(cursor);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyCursor(child, cursor);
            }
        }
    }
}
