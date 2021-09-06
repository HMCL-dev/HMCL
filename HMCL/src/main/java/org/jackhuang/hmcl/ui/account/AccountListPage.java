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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXScrollPane;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public class AccountListPage extends ListPageBase<AccountListItem> implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage"), -1));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ListProperty<AuthlibInjectorServer> authServers = new SimpleListProperty<>(this, "authServers", FXCollections.observableArrayList());
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

    public ListProperty<AuthlibInjectorServer> authServersProperty() {
        return authServers;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AccountListPageSkin(this);
    }

    private static class AccountListPageSkin extends SkinBase<AccountListPage> {

        private final ObservableList<AdvancedListItem> authServerItems;

        public AccountListPageSkin(AccountListPage skinnable) {
            super(skinnable);

            BorderPane root = new BorderPane();

            {
                VBox left = new VBox();
                left.getStyleClass().add("advanced-list-box-content");

                left.getChildren().add(new ClassTitle(i18n("account.create")));

                AdvancedListItem offlineItem = new AdvancedListItem();
                offlineItem.getStyleClass().add("navigation-drawer-item");
                offlineItem.setActionButtonVisible(false);
                offlineItem.setTitle(i18n("account.methods.offline"));
                offlineItem.setLeftGraphic(wrap(SVG.account(Theme.blackFillBinding(), 24, 24)));
                offlineItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));
                left.getChildren().add(offlineItem);

                AdvancedListItem mojangItem = new AdvancedListItem();
                mojangItem.getStyleClass().add("navigation-drawer-item");
                mojangItem.setActionButtonVisible(false);
                mojangItem.setTitle(i18n("account.methods.yggdrasil"));
                mojangItem.setLeftGraphic(wrap(SVG.mojang(Theme.blackFillBinding(), 24, 24)));
                mojangItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_MOJANG)));
                left.getChildren().add(mojangItem);

                AdvancedListItem microsoftItem = new AdvancedListItem();
                microsoftItem.getStyleClass().add("navigation-drawer-item");
                microsoftItem.setActionButtonVisible(false);
                microsoftItem.setTitle(i18n("account.methods.microsoft"));
                microsoftItem.setLeftGraphic(wrap(SVG.microsoft(Theme.blackFillBinding(), 24, 24)));
                microsoftItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_MICROSOFT)));
                left.getChildren().add(microsoftItem);

                left.getChildren().add(new ClassTitle(i18n("account.methods.authlib_injector")));

                {
                    VBox wrapper = new VBox();
                    FXUtils.setLimitWidth(wrapper, 200);

                    VBox box = new VBox();
                    authServerItems = MappedObservableList.create(skinnable.authServersProperty(), server -> {
                        AdvancedListItem item = new AdvancedListItem();
                        item.getStyleClass().add("navigation-drawer-item");
                        item.setLeftGraphic(SVG.server(Theme.blackFillBinding(), 24, 24));
                        item.setOnAction(e -> Controllers.dialog(new CreateAccountPane(server)));

                        JFXButton btnRemove = new JFXButton();
                        btnRemove.setOnAction(e -> {
                            skinnable.authServersProperty().remove(server);
                            e.consume();
                        });
                        btnRemove.getStyleClass().add("toggle-icon4");
                        btnRemove.setGraphic(SVG.close(Theme.blackFillBinding(), 14, 14));
                        item.setRightGraphic(btnRemove);

                        ObservableValue<String> title = BindingMapping.of(server, AuthlibInjectorServer::getName);
                        String url = server.getUrl();
                        item.titleProperty().bind(title);
                        item.subtitleProperty().set(url);
                        Tooltip tooltip = new Tooltip();
                        tooltip.textProperty().bind(Bindings.format("%s (%s)", title, url));
                        FXUtils.installFastTooltip(item, tooltip);

                        return item;
                    });
                    Bindings.bindContent(box.getChildren(), authServerItems);

                    AdvancedListItem addAuthServerItem = new AdvancedListItem();
                    addAuthServerItem.getStyleClass().add("navigation-drawer-item");
                    addAuthServerItem.setTitle(i18n("account.injector.add"));
                    addAuthServerItem.setActionButtonVisible(false);
                    addAuthServerItem.setLeftGraphic(SVG.plusCircleOutline(Theme.blackFillBinding(), 24, 24));
                    addAuthServerItem.setOnAction(e -> Controllers.dialog(new AddAuthlibInjectorServerPane()));

                    wrapper.getChildren().addAll(box, addAuthServerItem);
                    left.getChildren().add(new ScrollPane(wrapper));
                }

                FXUtils.setLimitWidth(left, 200);
                root.setLeft(left);
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
