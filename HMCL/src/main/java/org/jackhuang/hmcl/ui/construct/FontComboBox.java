/**
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

import static javafx.collections.FXCollections.emptyObservableList;
import static javafx.collections.FXCollections.observableList;
import static javafx.collections.FXCollections.singletonObservableList;

import org.jackhuang.hmcl.util.javafx.MultiStepBinding;

import com.jfoenix.controls.JFXComboBox;

import javafx.beans.NamedArg;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ListCell;
import javafx.scene.text.Font;

public class FontComboBox extends JFXComboBox<String> {

    private boolean loaded = false;

    public FontComboBox(@NamedArg(value = "fontSize", defaultValue = "12.0") double fontSize,
                        @NamedArg(value = "enableStyle", defaultValue = "false") boolean enableStyle) {
        styleProperty().bind(Bindings.concat("-fx-font-family: \"", valueProperty(), "\""));

        setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null) {
                    setText(item);
                    setFont(new Font(item, fontSize));
                }
            }
        });

        itemsProperty().bind(MultiStepBinding.of(valueProperty())
                        .map(value -> value == null ? emptyObservableList() : singletonObservableList(value)));

        setOnMouseClicked(e -> {
            if (loaded)
                return;
            itemsProperty().unbind();
            setItems(observableList(Font.getFamilies()));
            loaded = true;
        });
    }
}
