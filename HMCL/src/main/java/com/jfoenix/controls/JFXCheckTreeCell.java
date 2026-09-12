/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package com.jfoenix.controls;

import com.jfoenix.skins.JFXCheckBoxSkin;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.layout.HBox;
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
    private final JFXCheckBox checkBox = new JFXCheckBox();

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
        checkBox.skinProperty().addListener(observable -> finishSelectionAnimation());
        treeItemProperty().addListener(observable -> updateDisplay(getItem(), isEmpty()));
    }

    /// Finishes animations from the previous item and immediately displays the current state.
    private void finishSelectionAnimation() {
        if (checkBox.getSkin() instanceof JFXCheckBoxSkin skin) {
            skin.finishSelectionAnimation();
        }
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
            finishSelectionAnimation();
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
