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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.AdvancedListItemViewModel;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AccountAdvancedListItemViewModel extends AdvancedListItemViewModel {
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();
    private final ObjectProperty<Rectangle2D> viewport = new SimpleObjectProperty<>(AccountHelper.getViewport(4));
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();

    public AccountAdvancedListItemViewModel() {

        FXUtils.onChangeAndOperate(Accounts.selectedAccountProperty(), account -> {
            if (account == null) {
                title.set(i18n("account.missing"));
                subtitle.set(i18n("account.missing.add"));
                image.set(new Image("/assets/img/craft_table.png"));
            } else {
                title.set(account.getCharacter());
                subtitle.set(accountSubtitle(account));

                this.image.set(AccountHelper.getDefaultSkin(account.getUUID(), 4));

                if (account instanceof YggdrasilAccount) {
                    AccountHelper.loadSkinAsync((YggdrasilAccount) account).subscribe(Schedulers.javafx(), () -> {
                        Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
                        this.image.set(image);
                    });
                }
            }
        });
    }

    @Override
    public void action() {
        Controllers.navigate(Controllers.getAccountListView());
    }

    @Override
    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    @Override
    public ObjectProperty<Rectangle2D> viewportProperty() {
        return viewport;
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    @Override
    public StringProperty subtitleProperty() {
        return subtitle;
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
