/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListCell;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import static javafx.collections.FXCollections.emptyObservableList;
import static javafx.collections.FXCollections.singletonObservableList;

public final class FontComboBox extends JFXComboBox<String> {

    private boolean loaded = false;
    private ObservableList<String> allFonts;

    public FontComboBox() {
        setMinWidth(260);
        setEditable(true);

        styleProperty().bind(Bindings.concat("-fx-font-family: \"", valueProperty(), "\""));

        setCellFactory(listView -> new JFXListCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    setText(item);
                    setGraphic(null);
                    setStyle("-fx-font-family: \"" + item + "\"");
                }
            }
        });

        itemsProperty().bind(BindingMapping.of(valueProperty())
                .map(value -> value == null ? emptyObservableList() : singletonObservableList(value)));

        FXUtils.onClicked(this, () -> {
            if (loaded)
                return;
            itemsProperty().unbind();
            allFonts = FXCollections.observableArrayList(Font.getFamilies());
            setItems(allFonts);
            loaded = true;
        });

        getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loaded || StringUtils.isBlank(newValue)) {
                if (loaded) {
                    setItems(allFonts);
                }
                return;
            }

            String lowerQuery = newValue.toLowerCase();
            ObservableList<String> filteredFonts = FXCollections.observableArrayList();
            for (String font : allFonts) {
                if (font.toLowerCase().contains(lowerQuery)) {
                    filteredFonts.add(font);
                }
            }
            setItems(filteredFonts);
            show();
        });
    }
}
