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

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import static javafx.beans.binding.Bindings.createStringBinding;
import static org.jackhuang.hmcl.setting.Accounts.getAccountFactory;
import static org.jackhuang.hmcl.setting.Accounts.getLocalizedLoginTypeName;
import static org.jackhuang.hmcl.ui.FXUtils.toFXImage;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AccountAdvancedListItem extends AdvancedListItem {
    private final Tooltip tooltip;
    private final ImageView imageView;

    private ObjectProperty<Account> account = new SimpleObjectProperty<Account>() {

        @Override
        protected void invalidated() {
            Account account = get();
            if (account == null) {
                titleProperty().unbind();
                subtitleProperty().unbind();
                imageView.imageProperty().unbind();
                tooltip.textProperty().unbind();
                setTitle(i18n("account.missing"));
                setSubtitle(i18n("account.missing.add"));
                imageView.setImage(toFXImage(TexturesLoader.toAvatar(TexturesLoader.getDefaultSkin(TextureModel.STEVE).getImage(), 32)));
                tooltip.setText(i18n("account.create"));
            } else {
                titleProperty().bind(BindingMapping.of(account, Account::getCharacter));
                subtitleProperty().bind(accountSubtitle(account));
                imageView.imageProperty().bind(TexturesLoader.fxAvatarBinding(account, 32));
                tooltip.textProperty().bind(accountTooltip(account));
            }
        }
    };

    public AccountAdvancedListItem() {
        tooltip = new Tooltip();
        FXUtils.installFastTooltip(this, tooltip);

        Pair<Node, ImageView> view = createImageView(null);
        setLeftGraphic(view.getKey());
        imageView = view.getValue();

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

    private static ObservableValue<String> accountSubtitle(Account account) {
        if (account instanceof AuthlibInjectorAccount) {
            return BindingMapping.of(((AuthlibInjectorAccount) account).getServer(), AuthlibInjectorServer::getName);
        } else {
            return createStringBinding(() -> getLocalizedLoginTypeName(getAccountFactory(account)));
        }
    }

    private static ObservableValue<String> accountTooltip(Account account) {
        if (account instanceof AuthlibInjectorAccount) {
            AuthlibInjectorServer server = ((AuthlibInjectorAccount) account).getServer();
            return Bindings.format("%s (%s) (%s)",
                    BindingMapping.of(account, Account::getCharacter),
                    account.getUsername(),
                    BindingMapping.of(server, AuthlibInjectorServer::getName));
        } else if (account instanceof YggdrasilAccount) {
            return Bindings.format("%s (%s)",
                    BindingMapping.of(account, Account::getCharacter),
                    account.getUsername());
        } else {
            return BindingMapping.of(account, Account::getCharacter);
        }
    }

}
