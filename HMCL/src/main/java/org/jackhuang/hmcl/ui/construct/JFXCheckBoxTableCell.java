/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
