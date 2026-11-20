// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.value.ObservableNumberValue;
import javafx.css.PseudoClass;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

public abstract class MDListCell<T> extends ListCell<T> {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    private final StackPane container = new StackPane();
    private final StackPane root = new StackPane();
    private final RipplerContainer ripplerContainer = new RipplerContainer(container);

    public MDListCell(ListView<T> listView) {

        setText(null);
        setGraphic(null);

        root.getStyleClass().add("md-list-cell");
        root.getChildren().setAll(ripplerContainer);

        ObservableNumberValue width = listView.lookup(".clipped-container") instanceof Region viewport
                ? viewport.widthProperty().subtract(1) : listView.widthProperty();
        maxWidthProperty().bind(width);
        prefWidthProperty().bind(width);
        minWidthProperty().bind(width);
    }

    /// Resets feedback and updates the graphic when JavaFX reassigns this cell.
    @Override
    protected final void updateItem(@Nullable T item, boolean empty) {
        @Nullable T oldItem = getItem();
        boolean oldEmpty = isEmpty();

        super.updateItem(item, empty);

        if (oldItem == item && oldEmpty == empty) {
            updateLastPseudoClass(empty || item == null);
            return;
        }

        ripplerContainer.resetFeedback();

        updateControl(item, empty);
        if (empty || item == null) {
            setGraphic(null);
        } else {
            setGraphic(root);
        }
        updateLastPseudoClass(empty || item == null);
    }

    /// Updates the `:last` pseudo-class so the trailing divider can be suppressed in CSS.
    ///
    /// @param empty whether this cell is empty
    private void updateLastPseudoClass(boolean empty) {
        ListView<T> listView = getListView();
        boolean last = !empty
                && listView != null
                && getIndex() >= 0
                && getIndex() == listView.getItems().size() - 1;
        root.pseudoClassStateChanged(LAST, last);
    }

    /// Returns the feedback layer around the reusable content pane.
    public final RipplerContainer getFeedbackContainer() {
        return ripplerContainer;
    }

    protected StackPane getContainer() {
        return container;
    }

    protected void setSelectable() {
        FXUtils.onChangeAndOperate(selectedProperty(), selected -> {
            root.pseudoClassStateChanged(SELECTED, selected);
        });
    }

    protected void onClicked(Runnable action) {
        FXUtils.onClicked(ripplerContainer, action);
    }

    protected abstract void updateControl(T item, boolean empty);
}
