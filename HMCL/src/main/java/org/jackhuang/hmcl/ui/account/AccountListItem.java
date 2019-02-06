/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.binding.StringBinding;
import javafx.beans.property.*;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CredentialExpiredException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.DialogController;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import java.util.logging.Level;

public class AccountListItem extends RadioButton {

    private final Account account;
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty subtitle = new SimpleStringProperty();
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();

    public AccountListItem(Account account) {
        this.account = account;
        getStyleClass().clear();
        setUserData(account);

        String loginTypeName = Accounts.getLocalizedLoginTypeName(Accounts.getAccountFactory(account));
        if (account instanceof AuthlibInjectorAccount) {
            AuthlibInjectorServer server = ((AuthlibInjectorAccount) account).getServer();
            subtitle.bind(Bindings.concat(
                    loginTypeName, ", ", i18n("account.injector.server"), ": ",
                    Bindings.createStringBinding(server::getName, server)));
        } else {
            subtitle.set(loginTypeName);
        }

        StringBinding characterName = Bindings.createStringBinding(account::getCharacter, account);
        if (account instanceof OfflineAccount) {
            title.bind(characterName);
        } else {
            title.bind(Bindings.concat(account.getUsername(), " - ", characterName));
        }

        image.bind(TexturesLoader.fxAvatarBinding(account, 32));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AccountListItemSkin(this);
    }

    public void refresh() {
        account.clearCache();
        thread(() -> {
            try {
                account.logIn();
            } catch (CredentialExpiredException e) {
                try {
                    DialogController.logIn(account);
                } catch (Exception e1) {
                    LOG.log(Level.WARNING, "Failed to refresh " + account + " with password", e1);
                }
            } catch (AuthenticationException e) {
                LOG.log(Level.WARNING, "Failed to refresh " + account + " with token", e);
            }
        });
    }

    public void remove() {
        Accounts.getAccounts().remove(account);
    }

    public Account getAccount() {
        return account;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public void setSubtitle(String subtitle) {
        this.subtitle.set(subtitle);
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public Image getImage() {
        return image.get();
    }

    public void setImage(Image image) {
        this.image.set(image);
    }

    public ObjectProperty<Image> imageProperty() {
        return image;
    }
}
