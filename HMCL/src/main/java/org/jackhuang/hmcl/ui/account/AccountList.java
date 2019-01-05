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

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public class AccountList extends ListPage<AccountListItem> implements DecoratorPage {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", i18n("account.manage"));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ObjectProperty<Account> selectedAccount;

    public AccountList() {
        setItems(MappedObservableList.create(accounts, AccountListItem::new));
        selectedAccount = createSelectedItemPropertyFor(getItems(), Account.class);
    }

    public ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    public ListProperty<Account> accountsProperty() {
        return accounts;
    }

    @Override
    public void add() {
        Controllers.dialog(new AddAccountPane());
    }

    @Override
    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }
}
