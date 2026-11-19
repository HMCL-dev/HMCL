// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3Dialog;
import org.glavo.m3fx.controls.M3DialogEvent;
import org.glavo.m3fx.controls.M3DialogHandle;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputValidator;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// A native Material text prompt with validation and asynchronous acceptance.
@NotNullByDefault
public final class InputDialog extends M3Dialog {
    /// Completes with the field's text when a submission resolves; cancellation leaves it pending.
    private final CompletableFuture<String> future = new CompletableFuture<>();

    /// The editable text and its existing launcher validators.
    private final M3TextField textField;

    /// A wrapping submission error displayed below the field.
    private final Label warningLabel = new Label();

    /// Prevents overlapping submissions while the callback is pending.
    private final BooleanProperty submitting;

    /// The native accepting action, also invoked by Enter in the field.
    private final M3Button acceptButton = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);

    /// The active presentation, or null while the dialog is not displayed.
    private @Nullable M3DialogHandle handle;

    /// Creates a prompt on the FX thread; null text values are empty and null validators mean none.
    /// Callback results may arrive on any thread. Rejection keeps the dialog open for another attempt.
    public InputDialog(@Nullable String title, @Nullable String initialValue, FutureCallback<String> onResult,
                       M3TextInputValidator @Nullable ... validators) {
        setDismissOnScrimClick(false);
        getDialogPane().setHeadline(title == null ? "" : title);
        textField = FXUtils.newTextField();
        textField.setText(initialValue == null ? "" : initialValue);
        var input = validators == null || validators.length == 0 ? textField : FXUtils.newValidatedTextField(textField, validators);

        warningLabel.setWrapText(true);
        warningLabel.setMinWidth(0);
        warningLabel.getStyleClass().add("input-dialog-error");
        warningLabel.visibleProperty().bind(warningLabel.textProperty().isNotEmpty());
        warningLabel.managedProperty().bind(warningLabel.visibleProperty());
        getDialogPane().setContent(new VBox(12, input, warningLabel));

        submitting = FXUtils.newButtonLoadingProperty(acceptButton);
        acceptButton.setDefaultButton(true);
        acceptButton.disableProperty().bind(submitting.or(textField.errorProperty()));
        acceptButton.setOnAction(event -> {
            // Native actions close by default; acceptance must wait for the callback instead.
            event.consume();
            submitting.set(true);
            warningLabel.setText("");
            onResult.call(textField.getText(), new FutureCallback.ResultHandler() {
                /// Completes the result and closes the current presentation on the FX thread.
                @Override
                public void resolve() {
                    FXUtils.runInFX(() -> {
                        submitting.set(false);
                        future.complete(textField.getText());
                        if (handle != null)
                            handle.requestClose();
                    });
                }

                /// Restores the accepting action and displays the failure on the FX thread.
                @Override
                public void reject(String reason) {
                    FXUtils.runInFX(() -> {
                        submitting.set(false);
                        warningLabel.setText(reason);
                    });
                }
            });
        });
        textField.setOnAction(event -> acceptButton.fire());

        M3Button cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
        cancelButton.setCancelButton(true);
        getDialogPane().getActions().setAll(cancelButton, acceptButton);
        addEventHandler(M3DialogEvent.SHOWN, event -> {
            handle = event.getHandle();
            // A pending submission may finish while the host window is hidden.
            if (future.isDone())
                Platform.runLater(event.getHandle()::requestClose);
        });
        addEventHandler(M3DialogEvent.HIDDEN, event -> handle = null);
    }

    /// Returns the accepted-text future; closing alone does not complete or cancel it.
    public CompletableFuture<String> getCompletableFuture() {
        return future;
    }
}
