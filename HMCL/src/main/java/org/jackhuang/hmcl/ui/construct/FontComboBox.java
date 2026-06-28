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

import static javafx.collections.FXCollections.emptyObservableList;
import static javafx.collections.FXCollections.singletonObservableList;

import java.util.List;

import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListCell;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.scene.text.Font;

public final class FontComboBox extends JFXComboBox<String> {
    private Thread loadingThread = null;

    private volatile boolean loaded = false;

    public FontComboBox() {
        setMinWidth(260);

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

        setOnHiding(event -> {
            if (loadingThread != null && loadingThread.isAlive()) {
                loadingThread.interrupt();
                loadingThread = null;
                loaded = false;
            }
        });

        FXUtils.onClicked(this, () -> {
            if (loaded || (loadingThread != null && loadingThread.isAlive())) return;

            itemsProperty().unbind();

            List<String> allFonts = Font.getFamilies();
            List<String> headFonts = allFonts.subList(0, Math.min(10, allFonts.size()));

            var currentItems = FXCollections.observableArrayList(headFonts);
            setItems(currentItems);
            show(); 

            loadingThread = new Thread(() -> {

                List<String> remainingFonts = allFonts.subList(Math.min(10, allFonts.size()), allFonts.size());

                int batchSize = 30;
                for (int i = 0; i < remainingFonts.size(); i += batchSize) {
                    
                    if (Thread.currentThread().isInterrupted()) return;

                    int start = i;
                    int end = Math.min(start + batchSize, remainingFonts.size());
                    List<String> batch = remainingFonts.subList(start, end);

                    Platform.runLater(() -> currentItems.addAll(batch));

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                loaded = true;
            });

            loadingThread.setDaemon(true);
            loadingThread.start();
        });   
    }
}
