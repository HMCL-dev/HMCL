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
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionModel;

/**
 * @author yushijinhun
 */
public final class SelectionModelSelectedItemProperty {

    private static final String NODE_PROPERTY = SelectionModelSelectedItemProperty.class.getName() + ".instance";

    @SuppressWarnings("unchecked")
    public static <T> ObjectProperty<T> selectedItemPropertyFor(ComboBox<T> comboBox) {
        return (ObjectProperty<T>) comboBox.getProperties().computeIfAbsent(
                NODE_PROPERTY,
                any -> createSelectedItemProperty(comboBox.selectionModelProperty()));
    }

    private static <T> ObjectProperty<T> createSelectedItemProperty(Property<? extends SelectionModel<T>> modelProperty) {
        return new ReadWriteComposedProperty<>(
                MultiStepBinding.of(modelProperty)
                        .flatMap(SelectionModel::selectedItemProperty),
                newValue -> modelProperty.getValue().select(newValue));
    }

    private SelectionModelSelectedItemProperty() {
    }
}
