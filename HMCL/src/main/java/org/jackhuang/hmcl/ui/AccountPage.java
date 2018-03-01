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

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;

public class AccountPage extends StackPane implements DecoratorPage {
    private final StringProperty title;

    private final Account account;

    @FXML
    private Label lblType;
    @FXML
    private Label lblServer;
    @FXML
    private Label lblCharacter;
    @FXML
    private Label lblEmail;
    @FXML
    private BorderPane paneServer;
    @FXML
    private ComponentList componentList;
    @FXML
    private JFXButton btnRefresh;

    public AccountPage(Account account) {
        this.account = account;
        title = new SimpleStringProperty(this, "title", Launcher.i18n("account") + " - " + account.getCharacter());

        FXUtils.loadFXML(this, "/assets/fxml/account.fxml");

        if (account instanceof AuthlibInjectorAccount) {
            Accounts.getAuthlibInjectorServerNameAsync((AuthlibInjectorAccount) account)
                    .subscribe(Schedulers.javafx(), variables -> lblServer.setText(variables.get("serverName")));
        } else {
            componentList.removeChildren(paneServer);
        }

        lblCharacter.setText(account.getCharacter());
        lblType.setText(AddAccountPane.accountType(account));
        lblEmail.setText(account.getUsername());

        btnRefresh.setDisable(account instanceof OfflineAccount);

        if (account instanceof YggdrasilAccount) {
            btnRefresh.setOnMouseClicked(e -> {
                AccountHelper.refreshSkinAsync((YggdrasilAccount) account).start();
            });
        }
    }

    @FXML
    private void onDelete() {
        Settings.INSTANCE.deleteAccount(account);
        Controllers.navigate(null);
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
}
