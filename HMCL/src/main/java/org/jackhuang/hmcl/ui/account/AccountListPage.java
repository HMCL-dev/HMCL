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

import com.jfoenix.controls.JFXScrollPane;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public class AccountListPage extends ListPageBase<AccountListItem> implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage"), -1));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ObjectProperty<Account> selectedAccount;

    public AccountListPage() {
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
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AccountListPageSkin(this);
    }

    private static class AccountListPageSkin extends SkinBase<AccountListPage> {
        public AccountListPageSkin(AccountListPage skinnable) {
            super(skinnable);

            BorderPane root = new BorderPane();

            {
                AdvancedListBox sideBar = new AdvancedListBox()
                        .addNavigationDrawerItem(settingsItem -> {
                            settingsItem.setTitle(i18n("account.create"));
                            settingsItem.setLeftGraphic(wrap(SVG.plusCircleOutline(null, 20, 20)));
                            settingsItem.setOnAction(e -> Controllers.dialog(new AddAccountPane()));
                        });
                FXUtils.setLimitWidth(sideBar, 200);
                root.setLeft(sideBar);
            }

            ScrollPane scrollPane = new ScrollPane();
            VBox list = new VBox();
            {
                scrollPane.setFitToWidth(true);

                list.maxWidthProperty().bind(scrollPane.widthProperty());
                list.setSpacing(10);
                list.getStyleClass().add("card-list");

                Bindings.bindContent(list.getChildren(), skinnable.itemsProperty());

                scrollPane.setContent(list);
                JFXScrollPane.smoothScrolling(scrollPane);

                root.setCenter(scrollPane);
            }

            getChildren().setAll(root);
        }
    }
}
