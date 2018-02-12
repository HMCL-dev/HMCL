/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 4017  huangyuhui <huanghongxun4008@126.com>
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
import com.jfoenix.controls.JFXDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

import java.util.Optional;

public final class MessageDialogPane extends StackPane {
    private boolean closingDialog = true;

    @FXML
    private JFXButton acceptButton;
    @FXML
    private JFXButton cancelButton;
    @FXML
    private Label content;
    @FXML
    private Label graphic;
    @FXML
    private Label title;
    @FXML
    private HBox actions;

    public MessageDialogPane(String text, String title, JFXDialog dialog, int type, Runnable onAccept) {
        FXUtils.loadFXML(this, "/assets/fxml/message-dialog.fxml");

        if (title != null)
            this.title.setText(title);

        content.setText(text);
        acceptButton.setOnMouseClicked(e -> {
            if (closingDialog)
                dialog.close();
            Optional.ofNullable(onAccept).ifPresent(Runnable::run);
        });

        actions.getChildren().remove(cancelButton);

        switch (type) {
            case MessageBox.INFORMATION_MESSAGE:
                graphic.setGraphic(SVG.infoCircle("black", 40, 40));
                break;
            case MessageBox.ERROR_MESSAGE:
                graphic.setGraphic(SVG.closeCircle("black", 40, 40));
                break;
            case MessageBox.FINE_MESSAGE:
                graphic.setGraphic(SVG.checkCircle("black", 40, 40));
                break;
            case MessageBox.WARNING_MESSAGE:
                graphic.setGraphic(SVG.alert("black", 40, 40));
                break;
            case MessageBox.QUESTION_MESSAGE:
                graphic.setGraphic(SVG.helpCircle("black", 40, 40));
                break;
        }
    }

    public MessageDialogPane(String text, String title, JFXDialog dialog, Runnable onAccept, Runnable onCancel) {
        this(text, title, dialog, MessageBox.QUESTION_MESSAGE, onAccept);

        cancelButton.setVisible(true);
        cancelButton.setOnMouseClicked(e -> {
            if (closingDialog)
                dialog.close();
            Optional.ofNullable(onCancel).ifPresent(Runnable::run);
        });

        acceptButton.setText(Main.i18n("button.yes"));
        cancelButton.setText(Main.i18n("button.no"));

        actions.getChildren().add(cancelButton);
    }

    public void disableClosingDialog() {
        closingDialog = false;
    }
}
