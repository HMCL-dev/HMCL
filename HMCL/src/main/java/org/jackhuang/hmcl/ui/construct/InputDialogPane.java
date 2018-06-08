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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.function.Consumer;

public class InputDialogPane extends StackPane {

    @FXML
    private JFXButton acceptButton;
    @FXML
    private JFXButton cancelButton;
    @FXML
    private JFXTextField textField;
    @FXML
    private Label content;

    public InputDialogPane(String text, Consumer<Region> closeConsumer, Consumer<String> onResult) {
        FXUtils.loadFXML(this, "/assets/fxml/input-dialog.fxml");
        content.setText(text);
        cancelButton.setOnMouseClicked(e -> closeConsumer.accept(this));
        acceptButton.setOnMouseClicked(e -> {
            onResult.accept(textField.getText());
            closeConsumer.accept(this);
        });
    }
}
