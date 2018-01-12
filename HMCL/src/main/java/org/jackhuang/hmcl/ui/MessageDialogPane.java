/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public final class MessageDialogPane extends StackPane {
    private final String text;
    private final JFXDialog dialog;

    @FXML
    private JFXButton acceptButton;
    @FXML
    private Label content;

    public MessageDialogPane(String text, JFXDialog dialog) {
        this.text = text;
        this.dialog = dialog;

        FXUtilsKt.loadFXML(this, "/assets/fxml/message-dialog.fxml");
        content.setText(text);
        acceptButton.setOnMouseClicked(e -> dialog.close());
    }
}
