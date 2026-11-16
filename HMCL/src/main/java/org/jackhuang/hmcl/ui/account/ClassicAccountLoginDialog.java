// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXProgressBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.ClassicAccount;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ClassicAccountLoginDialog extends StackPane {
    private final ClassicAccount oldAccount;
    private final Consumer<AuthInfo> success;
    private final Runnable failed;

    private final JFXPasswordField txtPassword;
    private final Label lblCreationWarning = new Label();
    private final JFXProgressBar progressBar;

    public ClassicAccountLoginDialog(ClassicAccount oldAccount, Consumer<AuthInfo> success, Runnable failed) {
        this.oldAccount = oldAccount;
        this.success = success;
        this.failed = failed;

        progressBar = new JFXProgressBar();
        StackPane.setAlignment(progressBar, Pos.TOP_CENTER);
        progressBar.setVisible(false);

        JFXDialogLayout dialogLayout = new JFXDialogLayout();

        {
            dialogLayout.setHeading(new Label(i18n("login.enter_password")));
        }

        {
            VBox body = new VBox(15);
            body.setPadding(new Insets(15, 0, 0, 0));

            Label usernameLabel = new Label(oldAccount.getLoginName());

            txtPassword = new JFXPasswordField();
            txtPassword.setOnAction(e -> onAccept());
            txtPassword.getValidators().add(new RequiredValidator());
            txtPassword.setLabelFloat(true);
            txtPassword.setPromptText(i18n("account.password"));

            body.getChildren().setAll(usernameLabel, txtPassword);
            dialogLayout.setBody(body);
        }

        {
            JFXButton acceptButton = new JFXButton(i18n("button.ok"));
            acceptButton.setOnAction(e -> onAccept());
            acceptButton.getStyleClass().add("dialog-accept");

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.setOnAction(e -> onCancel());
            cancelButton.getStyleClass().add("dialog-cancel");

            dialogLayout.setActions(lblCreationWarning, acceptButton, cancelButton);
        }

        getChildren().setAll(dialogLayout);

        onEscPressed(this, this::onCancel);
    }

    private void onAccept() {
        String password = txtPassword.getText();
        progressBar.setVisible(true);
        lblCreationWarning.setText("");
        Task.supplyAsync(() -> oldAccount.logInWithPassword(password))
                .whenComplete(Schedulers.javafx(), authInfo -> {
                    success.accept(authInfo);
                    fireEvent(new DialogCloseEvent());
                    progressBar.setVisible(false);
                }, e -> {
                    LOG.info("Failed to login with password: " + oldAccount, e);
                    if (e instanceof NoSelectedCharacterException) {
                        fireEvent(new DialogCloseEvent());
                    } else {
                        lblCreationWarning.setText(Accounts.localizeErrorMessage(e));
                    }
                    progressBar.setVisible(false);
                }).start();
    }

    private void onCancel() {
        failed.run();
        fireEvent(new DialogCloseEvent());
    }
}
