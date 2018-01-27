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

import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.OfflineAccount;
import org.jackhuang.hmcl.auth.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.InvalidCredentialsException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.game.HMCLMultiCharacterSelector;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class AccountsPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", Main.i18n("account"));

    @FXML
    private ScrollPane scrollPane;
    @FXML private JFXMasonryPane masonryPane;
    @FXML private JFXDialog dialog;
    @FXML private JFXTextField txtUsername;
    @FXML private JFXPasswordField txtPassword;
    @FXML private Label lblCreationWarning;
    @FXML private JFXComboBox<String> cboType;
    @FXML private JFXProgressBar progressBar;

    {
        FXUtils.loadFXML(this, "/assets/fxml/account.fxml");

        getChildren().remove(dialog);
        dialog.setDialogContainer(this);

        FXUtils.smoothScrolling(scrollPane);
        FXUtils.setValidateWhileTextChanged(txtUsername);
        FXUtils.setValidateWhileTextChanged(txtPassword);

        cboType.getItems().setAll(Main.i18n("account.methods.offline"), Main.i18n("account.methods.yggdrasil"));
        cboType.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) -> {
            txtPassword.setVisible(newValue.intValue() != 0);
        });
        cboType.getSelectionModel().select(0);

        txtPassword.setOnAction(e -> onCreationAccept());
        txtUsername.setOnAction(e -> onCreationAccept());
        txtUsername.getValidators().add(new Validator(Main.i18n("input.email"), str -> !txtPassword.isVisible() || str.contains("@")));

        FXUtils.onChangeAndOperate(Settings.INSTANCE.selectedAccountProperty(), account -> {
            for (Node node : masonryPane.getChildren())
                if (node instanceof AccountItem)
                    ((AccountItem) node).setSelected(account == ((AccountItem) node).getAccount());
        });

        loadAccounts();

        if (Settings.INSTANCE.getAccounts().isEmpty())
            addNewAccount();
    }

    public void loadAccounts() {
        List<Node> children = new LinkedList<>();
        int i = 0;
        ToggleGroup group = new ToggleGroup();
        for (Map.Entry<String, Account> entry : Settings.INSTANCE.getAccounts().entrySet()) {
            children.add(buildNode(++i, entry.getValue(), group));
        }
        group.selectedToggleProperty().addListener((a, b, newValue) -> {
            if (newValue != null)
                Settings.INSTANCE.setSelectedAccount((Account) newValue.getProperties().get("account"));
        });
        FXUtils.resetChildren(masonryPane, children);
        Platform.runLater(() -> {
            masonryPane.requestLayout();
            scrollPane.requestLayout();
        });
    }

    private Node buildNode(int i, Account account, ToggleGroup group) {
        AccountItem item = new AccountItem(i, account, group);
        item.setOnDeleteButtonMouseClicked(e -> {
            Settings.INSTANCE.deleteAccount(account.getUsername());
            Platform.runLater(this::loadAccounts);
        });
        return item;
    }

    public void addNewAccount() {
        txtUsername.setText("");
        txtPassword.setText("");
        dialog.show();
    }

    public void onCreationAccept() {
        int type = cboType.getSelectionModel().getSelectedIndex();
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        progressBar.setVisible(true);
        lblCreationWarning.setText("");
        Task.ofResult("create_account", () -> {
            try {
                Account account;
                switch (type) {
                    case 0: account = OfflineAccountFactory.INSTANCE.fromUsername(username); break;
                    case 1: account = YggdrasilAccountFactory.INSTANCE.fromUsername(username, password); break;
                    default: throw new Error();
                }

                account.logIn(HMCLMultiCharacterSelector.INSTANCE, Settings.INSTANCE.getProxy());
                return account;
            } catch (Exception e) {
                return e;
            }
        }).subscribe(Schedulers.javafx(), variables -> {
            Object account = variables.get("create_account");
            if (account instanceof Account) {
                Settings.INSTANCE.addAccount((Account) account);
                dialog.close();
                loadAccounts();
            } else if (account instanceof InvalidCredentialsException) {
                lblCreationWarning.setText(Main.i18n("account.failed.wrong_password"));
            } else if (account instanceof Exception) {
                lblCreationWarning.setText(((Exception) account).getLocalizedMessage());
            }
            progressBar.setVisible(false);
        });
    }

    public void onCreationCancel() {
        dialog.close();
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public static String accountType(Account account) {
        if (account instanceof OfflineAccount) return Main.i18n("account.methods.offline");
        else if (account instanceof YggdrasilAccount) return Main.i18n("account.methods.yggdrasil");
        else throw new Error(Main.i18n("account.methods.no_method") + ": " + account);
    }
}
