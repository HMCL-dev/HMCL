/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.control.Tooltip;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AccountAdvancedListItem extends AdvancedListItem {
    private final Tooltip tooltip;

    private ObjectProperty<Account> account = new SimpleObjectProperty<Account>() {

        @Override
        protected void invalidated() {
            Account account = get();
            if (account == null) {
                titleProperty().unbind();
                setTitle(i18n("account.missing"));
                setSubtitle(i18n("account.missing.add"));
                imageProperty().unbind();
                setImage(newImage("/assets/img/craft_table.png"));
                tooltip.setText("");
            } else {
                titleProperty().bind(Bindings.createStringBinding(account::getCharacter, account));
                setSubtitle(accountSubtitle(account));
                imageProperty().bind(TexturesLoader.fxAvatarBinding(account, 32));
                tooltip.setText(account.getCharacter() + " " + accountTooltip(account));
            }
        }
    };

    public AccountAdvancedListItem() {
        setRightGraphic(SVG.viewList(Theme.blackFillBinding(), -1, -1));
        tooltip = new Tooltip();
        FXUtils.installFastTooltip(this, tooltip);

        setOnScroll(event -> {
            Account current = account.get();
            if (current == null) return;
            ObservableList<Account> accounts = Accounts.getAccounts();
            int currentIndex = accounts.indexOf(account.get());
            if (event.getDeltaY() > 0) { // up
                currentIndex--;
            } else { // down
                currentIndex++;
            }
            Accounts.setSelectedAccount(accounts.get((currentIndex + accounts.size()) % accounts.size()));
        });
    }

    public ObjectProperty<Account> accountProperty() {
        return account;
    }

    private static String accountSubtitle(Account account) {
        String loginTypeName = Accounts.getLocalizedLoginTypeName(Accounts.getAccountFactory(account));
        if (account instanceof AuthlibInjectorAccount) {
            return ((AuthlibInjectorAccount) account).getServer().getName();
        } else {
            return loginTypeName;
        }
    }

    private static String accountTooltip(Account account) {
        if (account instanceof AuthlibInjectorAccount) {
            AuthlibInjectorServer server = ((AuthlibInjectorAccount) account).getServer();
            return account.getUsername() + ", " + i18n("account.injector.server") + ": " + server.getName();
        } else if (account instanceof YggdrasilAccount) {
            return account.getUsername();
        } else {
            return "";
        }
    }
}
