/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionModel;

public class SelectionModelSelectedItemProperty<T> extends SimpleObjectProperty<T> {

    public static <T> SelectionModelSelectedItemProperty<T> selectedItemPropertyFor(ComboBox<T> comboBox) {
        return new SelectionModelSelectedItemProperty<>(comboBox.getSelectionModel());
    }

    private SelectionModel<T> model;

    public SelectionModelSelectedItemProperty(SelectionModel<T> model) {
        this.model = model;
        model.selectedItemProperty().addListener((observable, oldValue, newValue) -> set(newValue));
        set(model.getSelectedItem());
    }

    @Override
    protected void invalidated() {
        model.select(get());
    }
}
