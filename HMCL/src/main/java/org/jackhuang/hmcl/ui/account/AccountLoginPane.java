/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXProgressBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.NoSelectedCharacterException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;

import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AccountLoginPane extends StackPane {
    private final Account oldAccount;
    private final Consumer<AuthInfo> success;
    private final Runnable failed;

    private final JFXPasswordField txtPassword;
    private final Label lblCreationWarning = new Label();
    private final JFXProgressBar progressBar;

    public AccountLoginPane(Account oldAccount, Consumer<AuthInfo> success, Runnable failed) {
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

            Label usernameLabel = new Label(oldAccount.getUsername());

            txtPassword = new JFXPasswordField();
            txtPassword.setOnAction(e -> onAccept());
            txtPassword.getValidators().add(new RequiredValidator());
            txtPassword.setLabelFloat(true);
            txtPassword.setPromptText(i18n("account.password"));

            body.getChildren().setAll(usernameLabel, txtPassword);

            if (oldAccount instanceof YggdrasilAccount && !(oldAccount instanceof AuthlibInjectorAccount)) {
                HBox linkPane = new HBox(8);
                body.getChildren().add(linkPane);

                JFXHyperlink migrationLink = new JFXHyperlink(i18n("account.methods.yggdrasil.migration"));
                migrationLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.PROFILE_URL));

                JFXHyperlink migrationHowLink = new JFXHyperlink(i18n("account.methods.yggdrasil.migration.how"));
                migrationHowLink.setOnAction(e -> FXUtils.openLink(YggdrasilService.MIGRATION_FAQ_URL));

                linkPane.getChildren().setAll(migrationLink, migrationLink);
            }

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
                    LOG.log(Level.INFO, "Failed to login with password: " + oldAccount, e);
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
