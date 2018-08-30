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

import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ToggleGroup;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.util.MappedObservableList;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;

public class AccountListViewModel {
    private final ListProperty<AccountListItemViewModel> items = new SimpleListProperty<>(FXCollections.observableArrayList());
    private ObjectProperty<Account> selectedAccount = new SimpleObjectProperty<Account>() {
        {
            items.addListener(onInvalidating(this::invalidated));
        }

        @Override
        protected void invalidated() {
            Account selected = get();
            items.forEach(item -> item.selectedProperty().set(item.getAccount() == selected));
        }
    };

    private ToggleGroup toggleGroup;

    public AccountListViewModel() {
        toggleGroup = new ToggleGroup();

        items.bindContent(MappedObservableList.create(
                Accounts.accountsProperty(),
                account -> new AccountListItemViewModel(toggleGroup, account)));

        selectedAccount.bindBidirectional(Accounts.selectedAccountProperty());
        toggleGroup.selectedToggleProperty().addListener((o, a, toggle) -> {
            if (toggle == null || toggle.getUserData() == null) return;
            selectedAccount.set(((AccountListItemViewModel) toggle.getUserData()).getAccount());
        });
    }

    public void addNewAccount() {
        Controllers.dialog(new AddAccountPane());
    }

    public ListProperty<AccountListItemViewModel> itemsProperty() {
        return items;
    }
}
