/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeTableCell;
import javafx.util.Callback;
import javafx.util.StringConverter;

public class JFXCheckBoxTreeTableCell<S,T> extends TreeTableCell<S,T> {

    private final CheckBox checkBox;
    private boolean showLabel;
    private ObservableValue<Boolean> booleanProperty;

    public JFXCheckBoxTreeTableCell() {
        this(null, null);
    }

    public JFXCheckBoxTreeTableCell(
            final Callback<Integer, ObservableValue<Boolean>> getSelectedProperty) {
        this(getSelectedProperty, null);
    }

    public JFXCheckBoxTreeTableCell(
            final Callback<Integer, ObservableValue<Boolean>> getSelectedProperty,
            final StringConverter<T> converter) {
        this.getStyleClass().add("check-box-tree-table-cell");
        this.checkBox = new JFXCheckBox();
        setGraphic(null);
        setSelectedStateCallback(getSelectedProperty);
        setConverter(converter);
    }

    private ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<StringConverter<T>>(this, "converter") {
                protected void invalidated() {
                    updateShowLabel();
                }
            };

    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }
    public final void setConverter(StringConverter<T> value) {
        converterProperty().set(value);
    }
    public final StringConverter<T> getConverter() {
        return converterProperty().get();
    }

    private ObjectProperty<Callback<Integer, ObservableValue<Boolean>>>
            selectedStateCallback =
            new SimpleObjectProperty<Callback<Integer, ObservableValue<Boolean>>>(
                    this, "selectedStateCallback");

    public final ObjectProperty<Callback<Integer, ObservableValue<Boolean>>> selectedStateCallbackProperty() {
        return selectedStateCallback;
    }

    public final void setSelectedStateCallback(Callback<Integer, ObservableValue<Boolean>> value) {
        selectedStateCallbackProperty().set(value);
    }

    public final Callback<Integer, ObservableValue<Boolean>> getSelectedStateCallback() {
        return selectedStateCallbackProperty().get();
    }

    @SuppressWarnings("unchecked")
    @Override public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            StringConverter<T> c = getConverter();

            if (showLabel) {
                setText(c.toString(item));
            }
            setGraphic(checkBox);

            if (booleanProperty instanceof BooleanProperty) {
                checkBox.selectedProperty().unbindBidirectional((BooleanProperty)booleanProperty);
            }
            ObservableValue<?> obsValue = getSelectedProperty();
            if (obsValue instanceof BooleanProperty) {
                booleanProperty = (ObservableValue<Boolean>) obsValue;
                checkBox.selectedProperty().bindBidirectional((BooleanProperty)booleanProperty);
            }

            checkBox.disableProperty().bind(Bindings.not(
                    getTreeTableView().editableProperty().and(
                            getTableColumn().editableProperty()).and(
                            editableProperty())
            ));
        }
    }

    private void updateShowLabel() {
        this.showLabel = converter != null;
        this.checkBox.setAlignment(showLabel ? Pos.CENTER_LEFT : Pos.CENTER);
    }

    private ObservableValue<?> getSelectedProperty() {
        return getSelectedStateCallback() != null ?
                getSelectedStateCallback().call(getIndex()) :
                getTableColumn().getCellObservableValue(getIndex());
    }
}