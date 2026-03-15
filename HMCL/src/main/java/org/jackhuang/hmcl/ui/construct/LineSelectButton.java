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
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.jackhuang.hmcl.ui.SVG;

import java.util.Collection;
import java.util.function.Function;

/// @author Glavo
public final class LineSelectButton<T> extends LineButton {

    private static final String DEFAULT_STYLE_CLASS = "line-select-button";

    private final SelectPopup<T> selectPopup;

    public LineSelectButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.selectPopup = new SelectPopup<>();

        InvalidationListener updateTrailingText = observable -> {
            T value = getValue();
            if (value != null) {
                Function<T, String> converter = getConverter();
                setTrailingText(converter != null ? converter.apply(value) : value.toString());
            } else {
                setTrailingText(null);
            }
        };
        converterProperty().addListener(updateTrailingText);
        valueProperty().addListener(updateTrailingText);

        setTrailingIcon(SVG.UNFOLD_MORE);

        ripplerContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                if (selectPopup.isShowing()) {
                    selectPopup.hide();
                }
                event.consume();
            }
        });

        ripplerContainer.addEventFilter(ScrollEvent.ANY, ignored -> selectPopup.hide());

        selectPopup.showingProperty().addListener((observable, oldValue, newValue) ->
                ripplerContainer.getRippler().setRipplerDisabled(newValue));
    }

    @Override
    public void fire() {
        super.fire();
        if (selectPopup.isShowing()) {
            selectPopup.hide();
        } else {
            selectPopup.showRelativeTo(this);
        }
    }

    public ObjectProperty<T> valueProperty() {
        return selectPopup.valueProperty();
    }

    public T getValue() {
        return selectPopup.getValue();
    }

    public void setValue(T value) {
        selectPopup.setValue(value);
    }

    public ObjectProperty<Function<T, String>> converterProperty() {
        return selectPopup.converterProperty();
    }

    public Function<T, String> getConverter() {
        return selectPopup.getConverter();
    }

    public void setConverter(Function<T, String> value) {
        selectPopup.setConverter(value);
    }

    public ObjectProperty<Function<T, String>> descriptionConverterProperty() {
        return selectPopup.descriptionConverterProperty();
    }

    public Function<T, String> getDescriptionConverter() {
        return selectPopup.getDescriptionConverter();
    }

    public void setDescriptionConverter(Function<T, String> value) {
        selectPopup.setDescriptionConverter(value);
    }

    public ListProperty<T> itemsProperty() {
        return selectPopup.itemsProperty();
    }

    public ObservableList<T> getItems() {
        return selectPopup.getItems();
    }

    public void setItems(ObservableList<T> value) {
        selectPopup.setItems(value);
    }

    public void setItems(Collection<T> value) {
        selectPopup.setItems(value);
    }

    @SafeVarargs
    public final void setItems(T... values) {
        selectPopup.setItems(values);
    }
}
