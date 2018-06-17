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
import com.jfoenix.controls.JFXProgressBar;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;

import java.util.Optional;

public class AccountPage extends StackPane implements DecoratorPage {
    private final StringProperty title;
    private final ObjectProperty<Runnable> onDelete = new SimpleObjectProperty<>(this, "onDelete");
    private final VersionListItem item;
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
    private BorderPane paneEmail;
    @FXML
    private ComponentList componentList;
    @FXML
    private JFXButton btnDelete;
    @FXML
    private JFXButton btnRefresh;
    @FXML
    private JFXProgressBar progressBar;

    public AccountPage(Account account, VersionListItem item) {
        this.account = account;
        this.item = item;

        title = new SimpleStringProperty(this, "title", Launcher.i18n("account") + " - " + account.getCharacter());

        FXUtils.loadFXML(this, "/assets/fxml/account.fxml");

        FXUtils.setLimitWidth(this, 300);
        if (account instanceof AuthlibInjectorAccount) {
            lblServer.setText(((AuthlibInjectorAccount) account).getServer().getName());
            FXUtils.setLimitHeight(this, 182);
        } else {
            componentList.removeChildren(paneServer);

            if (account instanceof OfflineAccount) {
                componentList.removeChildren(paneEmail);
                FXUtils.setLimitHeight(this, 110);
            } else
                FXUtils.setLimitHeight(this, 145);
        }

        btnDelete.setGraphic(SVG.delete(Theme.blackFillBinding(), 15, 15));
        btnRefresh.setGraphic(SVG.refresh(Theme.blackFillBinding(), 15, 15));

        lblCharacter.setText(account.getCharacter());
        lblType.setText(AddAccountPane.accountType(account));
        lblEmail.setText(account.getUsername());

        btnRefresh.setVisible(account instanceof YggdrasilAccount);
    }

    @FXML
    private void onDelete() {
        Settings.INSTANCE.deleteAccount(account);
        Optional.ofNullable(onDelete.get()).ifPresent(Runnable::run);
    }

    @FXML
    private void onRefresh() {
        if (account instanceof YggdrasilAccount) {
            progressBar.setVisible(true);
            AccountHelper.refreshSkinAsync((YggdrasilAccount) account)
                    .finalized(Schedulers.javafx(), (variables, isDependentsSucceeded) -> {
                        progressBar.setVisible(false);

                        if (isDependentsSucceeded) {
                            Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
                            item.setImage(image, AccountHelper.getViewport(4));
                        }
                    }).start();
        }
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

    public Runnable getOnDelete() {
        return onDelete.get();
    }

    public ObjectProperty<Runnable> onDeleteProperty() {
        return onDelete;
    }

    public void setOnDelete(Runnable onDelete) {
        this.onDelete.set(onDelete);
    }
}
