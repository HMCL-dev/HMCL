// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3PasswordField;
import org.glavo.m3fx.controls.M3ProgressBar;
import org.glavo.m3fx.controls.M3TextInputLayout;
import org.glavo.m3fx.controls.M3TextInputVariant;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.ClassicAccount;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.LauncherDialog;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ClassicAccountLoginDialog extends LauncherDialog {
    private final ClassicAccount oldAccount;
    private final Consumer<AuthInfo> success;
    private final Runnable failed;

    /// Masked password submitted for reauthentication.
    private final M3PasswordField txtPassword;
    /// Displays the floating password label and required-field error.
    private final M3TextInputLayout passwordLayout;
    private final Label lblCreationWarning = new Label();
    /// Indicates that password authentication is in progress.
    private final M3ProgressBar progressBar = new M3ProgressBar();

    public ClassicAccountLoginDialog(ClassicAccount oldAccount, Consumer<AuthInfo> success, Runnable failed) {
        this.oldAccount = oldAccount;
        this.success = success;
        this.failed = failed;

        progressBar.managedProperty().bind(progressBar.visibleProperty());
        progressBar.setVisible(false);
        progressBar.setMouseTransparent(true);

        getDialogPane().setHeadline(i18n("login.enter_password"));

        {
            VBox body = new VBox(15);
            body.setPadding(new Insets(15, 0, 0, 0));

            Label usernameLabel = new Label(oldAccount.getLoginName());

            txtPassword = FXUtils.newPasswordField(M3TextInputVariant.FILLED);
            txtPassword.setOnAction(e -> onAccept());
            passwordLayout = new M3TextInputLayout(txtPassword);
            passwordLayout.setLabelText(i18n("account.password"));
            passwordLayout.setValidator(new RequiredValidator());

            lblCreationWarning.visibleProperty().bind(lblCreationWarning.textProperty().isNotEmpty());
            lblCreationWarning.managedProperty().bind(lblCreationWarning.visibleProperty());
            body.getChildren().setAll(usernameLabel, passwordLayout, lblCreationWarning, progressBar);
            setFormContent(body);
        }

        {
            M3Button acceptButton = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
            acceptButton.setOnAction(e -> {
                e.consume();
                onAccept();
            });

            M3Button cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
            cancelButton.setOnAction(e -> onCancel());

            cancelButton.setCancelButton(true);
            getDialogPane().getActions().setAll(cancelButton, acceptButton);
        }
    }

    /// Submits a non-blank password and displays the authentication result.
    private void onAccept() {
        if (!passwordLayout.validate())
            return;
        String password = txtPassword.getText();
        progressBar.setVisible(true);
        lblCreationWarning.setText("");
        Task.supplyAsync(() -> oldAccount.logInWithPassword(password))
                .whenComplete(Schedulers.javafx(), authInfo -> {
                    success.accept(authInfo);
                    closeDialog();
                    progressBar.setVisible(false);
                }, e -> {
                    LOG.info("Failed to login with password: " + oldAccount, e);
                    if (e instanceof NoSelectedCharacterException) {
                        onCancel();
                    } else {
                        lblCreationWarning.setText(Accounts.localizeErrorMessage(e));
                    }
                    progressBar.setVisible(false);
                }).start();
    }

    private void onCancel() {
        failed.run();
        closeDialog();
    }
}
