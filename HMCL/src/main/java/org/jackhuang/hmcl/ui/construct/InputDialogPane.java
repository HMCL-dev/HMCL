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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FutureCallback;

import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InputDialogPane extends JFXDialogLayout implements DialogAware {
    private final CompletableFuture<String> future = new CompletableFuture<>();

    private final JFXTextField textField;
    private final Label lblCreationWarning;
    private final SpinnerPane acceptPane;
    private final JFXButton acceptButton;

    public InputDialogPane(String text, String initialValue, FutureCallback<String> onResult, ValidatorBase... validators) {
        this(text, initialValue, onResult);
        if (validators != null && validators.length > 0) {
            textField.getValidators().addAll(validators);
            FXUtils.setValidateWhileTextChanged(textField, true);
            acceptButton.disableProperty().bind(textField.activeValidatorProperty().isNotNull());
        }
    }

    public InputDialogPane(String text, String initialValue, FutureCallback<String> onResult) {
        textField = new JFXTextField(initialValue);

        this.setHeading(new HBox(new Label(text)));
        this.setBody(new VBox(textField));

        lblCreationWarning = new Label();

        acceptPane = new SpinnerPane();
        acceptPane.getStyleClass().add("small-spinner-pane");
        acceptButton = new JFXButton(i18n("button.ok"));
        acceptButton.getStyleClass().add("dialog-accept");
        acceptPane.setContent(acceptButton);

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");

        this.setActions(lblCreationWarning, acceptPane, cancelButton);

        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        acceptButton.setOnAction(e -> {
            acceptPane.showSpinner();

            onResult.call(textField.getText(), new FutureCallback.ResultHandler() {
                @Override
                public void resolve() {
                    acceptPane.hideSpinner();
                    future.complete(textField.getText());
                    fireEvent(new DialogCloseEvent());
                }

                @Override
                public void reject(String reason) {
                    acceptPane.hideSpinner();
                    lblCreationWarning.setText(reason);
                }
            });
        });
        textField.setOnAction(event -> acceptButton.fire());
        onEscPressed(this, cancelButton::fire);
    }

    @Override
    public void onDialogShown() {
        textField.requestFocus();
    }

    public CompletableFuture<String> getCompletableFuture() {
        return future;
    }
}
