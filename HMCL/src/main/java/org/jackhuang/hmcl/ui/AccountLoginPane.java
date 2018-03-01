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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXProgressBar;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;

import java.util.function.Consumer;

public class AccountLoginPane extends StackPane {
    private final Account oldAccount;
    private final Consumer<AuthInfo> success;
    private final Runnable failed;

    @FXML
    private Label lblUsername;
    @FXML private JFXPasswordField txtPassword;
    @FXML private Label lblCreationWarning;
    @FXML private JFXProgressBar progressBar;
    private JFXDialog dialog;

    public AccountLoginPane(Account oldAccount, Consumer<AuthInfo> success, Runnable failed) {
        this.oldAccount = oldAccount;
        this.success = success;
        this.failed = failed;

        FXUtils.loadFXML(this, "/assets/fxml/account-login.fxml");

        lblUsername.setText(oldAccount.getUsername());
        txtPassword.setOnAction(e -> onAccept());
    }

    @FXML
    private void onAccept() {
        String password = txtPassword.getText();
        progressBar.setVisible(true);
        lblCreationWarning.setText("");
        Task.ofResult("login", () -> {
            try {
                return oldAccount.logInWithPassword(password);
            } catch (Exception e) {
                return e;
            }
        }).subscribe(Schedulers.javafx(), variable -> {
            Object account = variable.get("login");
            if (account instanceof AuthInfo) {
                success.accept(((AuthInfo) account));
                dialog.close();
            } else if (account instanceof NoSelectedCharacterException) {
                dialog.close();
            } else if (account instanceof Exception) {
                lblCreationWarning.setText(AddAccountPane.accountException((Exception) account));
            }

            progressBar.setVisible(false);
        });
    }

    @FXML
    private void onCancel() {
        failed.run();
        dialog.close();
    }

    public void setDialog(JFXDialog dialog) {
        this.dialog = dialog;
    }
}
