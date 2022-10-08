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
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.net.URI;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ObservableList<AccountListItem> items;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage")));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ListProperty<AuthlibInjectorServer> authServers = new SimpleListProperty<>(this, "authServers", FXCollections.observableArrayList());
    private final ObjectProperty<Account> selectedAccount;

    public AccountListPage() {
        items = MappedObservableList.create(accounts, AccountListItem::new);
        selectedAccount = createSelectedItemPropertyFor(items, Account.class);
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

    private static class AccountListPageSkin extends DecoratorAnimatedPageSkin<AccountListPage> {

        private final ObservableList<AdvancedListItem> authServerItems;

        public AccountListPageSkin(AccountListPage skinnable) {
            super(skinnable);

            {
                VBox boxMethods = new VBox();
                {
                    boxMethods.getStyleClass().add("advanced-list-box-content");
                    boxMethods.getChildren().add(new ClassTitle(i18n("account.create")));
                    FXUtils.setLimitWidth(boxMethods, 200);

                    AdvancedListItem offlineItem = new AdvancedListItem();
                    offlineItem.getStyleClass().add("navigation-drawer-item");
                    offlineItem.setActionButtonVisible(false);
                    offlineItem.setTitle(i18n("account.methods.offline"));
                    offlineItem.setLeftGraphic(wrap(SVG::account));
                    offlineItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));
                    boxMethods.getChildren().add(offlineItem);

                    AdvancedListItem mojangItem = new AdvancedListItem();
                    mojangItem.getStyleClass().add("navigation-drawer-item");
                    mojangItem.setActionButtonVisible(false);
                    mojangItem.setTitle(i18n("account.methods.yggdrasil"));
                    mojangItem.setLeftGraphic(wrap(SVG::mojang));
                    mojangItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_MOJANG)));
                    boxMethods.getChildren().add(mojangItem);

                    AdvancedListItem microsoftItem = new AdvancedListItem();
                    microsoftItem.getStyleClass().add("navigation-drawer-item");
                    microsoftItem.setActionButtonVisible(false);
                    microsoftItem.setTitle(i18n("account.methods.microsoft"));
                    microsoftItem.setLeftGraphic(wrap(SVG::microsoft));
                    microsoftItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_MICROSOFT)));
                    boxMethods.getChildren().add(microsoftItem);

                    AdvancedListItem littleSkinItem = new AdvancedListItem();
                    littleSkinItem.getStyleClass().add("navigation-drawer-item");
                    littleSkinItem.setActionButtonVisible(false);
                    littleSkinItem.setTitle(i18n("account.methods.little_skin"));
                    littleSkinItem.setLeftGraphic(wrap(SVG::server));
                    littleSkinItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_LITTLE_SKIN)));
                    boxMethods.getChildren().add(littleSkinItem);

                    VBox boxAuthServers = new VBox();
                    authServerItems = MappedObservableList.create(skinnable.authServersProperty(), server -> {
                        AdvancedListItem item = new AdvancedListItem();
                        item.getStyleClass().add("navigation-drawer-item");
                        item.setLeftGraphic(wrap(SVG::server));
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
                        item.titleProperty().bind(title);
                        String host = "";
                        try {
                            host = URI.create(server.getUrl()).getHost();
                        } catch (IllegalArgumentException e) {
                            LOG.log(Level.WARNING, "Unparsable authlib-injector server url " + server.getUrl(), e);
                        }
                        item.subtitleProperty().set(host);
                        Tooltip tooltip = new Tooltip();
                        tooltip.textProperty().bind(Bindings.format("%s (%s)", title, server.getUrl()));
                        FXUtils.installFastTooltip(item, tooltip);

                        return item;
                    });
                    Bindings.bindContent(boxAuthServers.getChildren(), authServerItems);
                    boxMethods.getChildren().add(boxAuthServers);
                }

                AdvancedListItem addAuthServerItem = new AdvancedListItem();
                {
                    addAuthServerItem.getStyleClass().add("navigation-drawer-item");
                    addAuthServerItem.setTitle(i18n("account.injector.add"));
                    addAuthServerItem.setSubtitle(i18n("account.methods.authlib_injector"));
                    addAuthServerItem.setActionButtonVisible(false);
                    addAuthServerItem.setLeftGraphic(wrap(SVG::plusCircleOutline));
                    addAuthServerItem.setOnAction(e -> Controllers.dialog(new AddAuthlibInjectorServerPane()));
                    VBox.setMargin(addAuthServerItem, new Insets(0, 0, 12, 0));
                }

                ScrollPane scrollPane = new ScrollPane(boxMethods);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);
                setLeft(scrollPane, addAuthServerItem);
            }

            ScrollPane scrollPane = new ScrollPane();
            VBox list = new VBox();
            {
                scrollPane.setFitToWidth(true);

                list.maxWidthProperty().bind(scrollPane.widthProperty());
                list.setSpacing(10);
                list.getStyleClass().add("card-list");

                Bindings.bindContent(list.getChildren(), skinnable.items);

                scrollPane.setContent(list);
                JFXScrollPane.smoothScrolling(scrollPane);

                setCenter(scrollPane);
            }
        }
    }
}
