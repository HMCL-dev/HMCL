/*
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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FutureCallback;

public class InputDialogPane extends StackPane {

    @FXML
    private JFXButton acceptButton;
    @FXML
    private JFXButton cancelButton;
    @FXML
    private JFXTextField textField;
    @FXML
    private Label content;
    @FXML
    private Label lblCreationWarning;
    @FXML
    private SpinnerPane acceptPane;

    public InputDialogPane(String text, FutureCallback<String> onResult) {
        FXUtils.loadFXML(this, "/assets/fxml/input-dialog.fxml");
        content.setText(text);
        cancelButton.setOnMouseClicked(e -> fireEvent(new DialogCloseEvent()));
        acceptButton.setOnMouseClicked(e -> {
            acceptPane.showSpinner();
            onResult.call(textField.getText(), () -> {
                acceptPane.hideSpinner();
                fireEvent(new DialogCloseEvent());
            }, msg -> {
                acceptPane.hideSpinner();
                lblCreationWarning.setText(msg);
            });
        });

        acceptButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !textField.validate(),
                textField.textProperty()
        ));
    }

    public void setInitialText(String text) {
        textField.setText(text);
    }
}
