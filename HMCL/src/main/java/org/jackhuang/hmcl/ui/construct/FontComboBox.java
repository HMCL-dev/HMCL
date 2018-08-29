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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXComboBox;
import javafx.beans.NamedArg;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import javafx.scene.text.Font;

public class FontComboBox extends JFXComboBox<String> {
    private boolean loaded = false;

    public FontComboBox(@NamedArg(value = "fontSize", defaultValue = "12.0") double fontSize,
                        @NamedArg(value = "enableStyle", defaultValue = "false") boolean enableStyle) {
        valueProperty().addListener((a, b, newValue) -> {
            if (enableStyle)
                setStyle("-fx-font-family: \"" + newValue + "\";");
        });

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

        setOnMouseClicked(e -> {
            if (loaded) return;
            getItems().setAll(Font.getFamilies());
        });
    }

    public void initValue(Font font) {
        getItems().setAll(font.getFamily());
        getSelectionModel().select(font.getFamily());
    }
}
