// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

/// @author Glavo
public final class JFXCheckBoxTableCell<S, T> extends TableCell<S, T> {
    public static <S> Callback<TableColumn<S, Boolean>, TableCell<S, Boolean>> forTableColumn(
            final TableColumn<S, Boolean> column) {
        return list -> new JFXCheckBoxTableCell<>();
    }

    private final JFXCheckBox checkBox = new JFXCheckBox();
    private BooleanProperty booleanProperty;

    public JFXCheckBoxTableCell() {
        this.getStyleClass().add("jfx-checkbox-table-cell");
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
            checkBox.disableProperty().unbind();
        } else {
            setGraphic(checkBox);

            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            if (getTableColumn().getCellObservableValue(getIndex()) instanceof BooleanProperty obsValue) {
                booleanProperty = obsValue;
                checkBox.selectedProperty().bindBidirectional(booleanProperty);
            }

            checkBox.disableProperty().bind(Bindings.not(
                    getTableView().editableProperty().and(
                            getTableColumn().editableProperty()).and(
                            editableProperty())
            ));
        }
    }

}
