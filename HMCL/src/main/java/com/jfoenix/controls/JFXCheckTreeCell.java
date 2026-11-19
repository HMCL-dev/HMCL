// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.layout.HBox;
import org.glavo.m3fx.controls.M3CheckBox;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Displays a [CheckBoxTreeItem] with a material-design checkbox.
///
/// The checkbox's selected and indeterminate states are bidirectionally bound to
/// the current tree item. Reusing or clearing the cell removes the old bindings.
/// Installing a checkbox skin or switching tree items applies the selection state
/// immediately, without playing a selection animation.
/// Ordinary tree items retain the display provided by [JFXTreeCell].
/// Empty cells and `null` values display neither content nor a checkbox.
///
/// @param <T> the type of value stored in each tree item
@NotNullByDefault
public class JFXCheckTreeCell<T> extends JFXTreeCell<T> {

    /// The checkbox bound to the currently displayed checkable tree item.
    private final M3CheckBox checkBox = new M3CheckBox();

    /// Places the checkbox before the graphic supplied by the superclass.
    private final HBox graphic = new HBox();

    /// The tree item whose selection properties are currently bound, if any.
    private @Nullable CheckBoxTreeItem<?> boundTreeItem;

    /// Creates an empty cell whose checkbox toggles the current tree item's selection.
    public JFXCheckTreeCell() {
        checkBox.setAllowIndeterminate(false);
        checkBox.setFocusTraversable(false);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPickOnBounds(false);
        treeItemProperty().addListener(observable -> updateDisplay(getItem(), isEmpty()));
    }

    /// Adds the checkbox to the inherited display and updates its selection bindings.
    @Override
    protected void updateDisplay(@Nullable T item, boolean empty) {
        // Release graphics before the superclass reuses or reparents them.
        graphic.getChildren().clear();
        super.updateDisplay(item, empty);

        @Nullable CheckBoxTreeItem<?> treeItem = !empty && item != null
                && getTreeItem() instanceof CheckBoxTreeItem<?> checkTreeItem ? checkTreeItem : null;
        if (boundTreeItem != treeItem) {
            // Settle selection marks while the recycled checkbox is hidden.
            checkBox.setVisible(false);
            if (boundTreeItem != null) {
                checkBox.selectedProperty().unbindBidirectional(boundTreeItem.selectedProperty());
                checkBox.indeterminateProperty().unbindBidirectional(boundTreeItem.indeterminateProperty());
            }
            boundTreeItem = treeItem;
            if (treeItem != null) {
                checkBox.selectedProperty().bindBidirectional(treeItem.selectedProperty());
                checkBox.indeterminateProperty().bindBidirectional(treeItem.indeterminateProperty());
            } else {
                checkBox.setSelected(false);
                checkBox.setIndeterminate(false);
            }
            checkBox.setVisible(treeItem != null);
        }

        if (treeItem != null) {
            @Nullable Node itemGraphic = getGraphic();
            graphic.getChildren().add(checkBox);
            if (itemGraphic != null) {
                graphic.getChildren().add(itemGraphic);
            }
            setGraphic(graphic);
        }
    }
}
