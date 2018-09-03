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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AccountAdvancedListItem extends AdvancedListItem {
    private ObjectProperty<Account> account = new SimpleObjectProperty<Account>() {

        @Override
        protected void invalidated() {
            Account account = get();
            if (account == null) {
                titleProperty().set(i18n("account.missing"));
                subtitleProperty().set(i18n("account.missing.add"));
                imageProperty().set(new Image("/assets/img/craft_table.png"));
                viewportProperty().set(null);
            } else {
                titleProperty().set(account.getCharacter());
                subtitleProperty().set(accountSubtitle(account));

                imageProperty().set(AccountHelper.getDefaultSkin(account.getUUID(), 4));
                viewportProperty().set(AccountHelper.getViewport(4));

                if (account instanceof YggdrasilAccount) {
                    AccountHelper.loadSkinAsync((YggdrasilAccount) account).subscribe(Schedulers.javafx(), () -> {
                        Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
                        imageProperty().set(image);
                    });
                }
            }
        }
    };

    public AccountAdvancedListItem() {
    }

    public ObjectProperty<Account> accountProperty() {
        return account;
    }

    private static String accountSubtitle(Account account) {
        if (account instanceof OfflineAccount)
            return i18n("account.methods.offline");
        else if (account instanceof YggdrasilAccount)
            return account.getUsername();
        else
            return "";
    }
}
