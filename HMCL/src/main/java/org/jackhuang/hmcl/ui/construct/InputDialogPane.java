// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
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
    private final M3Button acceptButton;

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
        lblCreationWarning.setPadding(new Insets(0, 5, 0, 0));

        acceptPane = new SpinnerPane();
        acceptPane.getStyleClass().add("small-spinner-pane");
        acceptButton = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
        acceptPane.setContent(acceptButton);

        M3Button cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);

        this.setActions(lblCreationWarning, cancelButton, acceptPane);

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
