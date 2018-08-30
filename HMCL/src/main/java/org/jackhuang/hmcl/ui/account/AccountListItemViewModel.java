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
package org.jackhuang.hmcl.ui.account;

import javafx.beans.property.*;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AccountListItemViewModel {
    private final Account account;
    private final ToggleGroup toggleGroup;
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();
    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();
    private final ObjectProperty<Rectangle2D> viewport = new SimpleObjectProperty<>();

    public AccountListItemViewModel(ToggleGroup toggleGroup, Account account) {
        this.account = account;
        this.toggleGroup = toggleGroup;

        StringBuilder subtitleString = new StringBuilder(Accounts.getAccountTypeName(account));
        if (account instanceof AuthlibInjectorAccount) {
            AuthlibInjectorServer server = ((AuthlibInjectorAccount) account).getServer();
            subtitleString.append(", ").append(i18n("account.injector.server")).append(": ").append(server.getName());
        }

        if (account instanceof OfflineAccount)
            title.set(account.getCharacter());
        else
            title.set(account.getUsername() + " - " + account.getCharacter());
        subtitle.set(subtitleString.toString());
        selected.set(Accounts.selectedAccountProperty().get() == account);

        viewport.set(AccountHelper.getViewport(4));
        if (account instanceof YggdrasilAccount) {
            Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
            this.image.set(image);
        } else {
            this.image.set(AccountHelper.getDefaultSkin(account.getUUID(), 4));
        }
    }

    public ToggleGroup getToggleGroup() {
        return toggleGroup;
    }

    public Account getAccount() {
        return account;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    public ObjectProperty<Rectangle2D> viewportProperty() {
        return viewport;
    }

    public void refresh() {
        if (account instanceof YggdrasilAccount) {
            // progressBar.setVisible(true);
            AccountHelper.refreshSkinAsync((YggdrasilAccount) account)
                    .finalized(Schedulers.javafx(), (variables, isDependentsSucceeded) -> {
                        // progressBar.setVisible(false);

                        if (isDependentsSucceeded) {
                            Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
                            this.image.set(image);
                        }
                    }).start();
        }
    }

    public void remove() {
        Accounts.getAccounts().remove(account);
    }
}
