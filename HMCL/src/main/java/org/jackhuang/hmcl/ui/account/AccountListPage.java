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
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
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
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;

import java.net.URI;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

public final class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {
    static final BooleanProperty RESTRICTED = new SimpleBooleanProperty(true);

    private static boolean isExemptedRegion() {
        String zoneId = ZoneId.systemDefault().getId();
        if (Arrays.asList(
                "Asia/Shanghai",
                // Although Asia/Beijing is not a legal name, Deepin uses it
                "Asia/Beijing",
                "Asia/Chongqing",
                "Asia/Chungking",
                "Asia/Harbin"
        ).contains(zoneId))
            return true;

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && NativeUtils.USE_JNA) {
            Kernel32 kernel32 = Kernel32.INSTANCE;

            // https://learn.microsoft.com/windows/win32/intl/table-of-geographical-locations
            if (kernel32 != null && kernel32.GetUserGeoID(WinConstants.GEOCLASS_NATION) == 45) // China
                return true;
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && "GMT+08:00".equals(zoneId))
            // Some Linux distributions may use invalid time zone ids (e.g., Asia/Beijing)
            // Java may not be able to resolve this name and use GMT+08:00 instead.
            return true;

        return false;
    }

    static {
        String property = System.getProperty("hmcl.offline.auth.restricted", "auto");

        if ("false".equals(property)
                || "auto".equals(property) && isExemptedRegion()
                || globalConfig().isEnableOfflineAccount())
            RESTRICTED.set(false);
        else
            globalConfig().enableOfflineAccountProperty().addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> o, Boolean oldValue, Boolean newValue) {
                    if (newValue) {
                        globalConfig().enableOfflineAccountProperty().removeListener(this);
                        RESTRICTED.set(false);
                    }
                }
            });
    }

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
        private ChangeListener<Boolean> holder;

        public AccountListPageSkin(AccountListPage skinnable) {
            super(skinnable);

            {
                VBox boxMethods = new VBox();
                {
                    boxMethods.getStyleClass().add("advanced-list-box-content");
                    FXUtils.setLimitWidth(boxMethods, 200);

                    AdvancedListItem microsoftItem = new AdvancedListItem();
                    microsoftItem.getStyleClass().add("navigation-drawer-item");
                    microsoftItem.setActionButtonVisible(false);
                    microsoftItem.setTitle(i18n("account.methods.microsoft"));
                    microsoftItem.setLeftGraphic(wrap(SVG.MICROSOFT));
                    microsoftItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_MICROSOFT)));

                    AdvancedListItem offlineItem = new AdvancedListItem();
                    offlineItem.getStyleClass().add("navigation-drawer-item");
                    offlineItem.setActionButtonVisible(false);
                    offlineItem.setTitle(i18n("account.methods.offline"));
                    offlineItem.setLeftGraphic(wrap(SVG.PERSON));
                    offlineItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));

                    VBox boxAuthServers = new VBox();
                    authServerItems = MappedObservableList.create(skinnable.authServersProperty(), server -> {
                        AdvancedListItem item = new AdvancedListItem();
                        item.getStyleClass().add("navigation-drawer-item");
                        item.setLeftGraphic(wrap(SVG.DRESSER));
                        item.setOnAction(e -> Controllers.dialog(new CreateAccountPane(server)));

                        JFXButton btnRemove = new JFXButton();
                        btnRemove.setOnAction(e -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                                skinnable.authServersProperty().remove(server);
                            }, null);
                            e.consume();
                        });
                        btnRemove.getStyleClass().add("toggle-icon4");
                        btnRemove.setGraphic(SVG.CLOSE.createIcon(Theme.blackFill(), 14));
                        item.setRightGraphic(btnRemove);

                        ObservableValue<String> title = BindingMapping.of(server, AuthlibInjectorServer::getName);
                        item.titleProperty().bind(title);
                        String host = "";
                        try {
                            host = URI.create(server.getUrl()).getHost();
                        } catch (IllegalArgumentException e) {
                            LOG.warning("Unparsable authlib-injector server url " + server.getUrl(), e);
                        }
                        item.subtitleProperty().set(host);
                        Tooltip tooltip = new Tooltip();
                        tooltip.textProperty().bind(Bindings.format("%s (%s)", title, server.getUrl()));
                        FXUtils.installFastTooltip(item, tooltip);

                        return item;
                    });
                    Bindings.bindContent(boxAuthServers.getChildren(), authServerItems);

                    ClassTitle title = new ClassTitle(i18n("account.create").toUpperCase(Locale.ROOT));
                    if (RESTRICTED.get()) {
                        VBox wrapper = new VBox(offlineItem, boxAuthServers);
                        wrapper.setPadding(Insets.EMPTY);
                        FXUtils.installFastTooltip(wrapper, i18n("account.login.restricted"));

                        offlineItem.setDisable(true);
                        boxAuthServers.setDisable(true);

                        boxMethods.getChildren().setAll(title, microsoftItem, wrapper);

                        holder = FXUtils.onWeakChange(RESTRICTED, value -> {
                            if (!value) {
                                holder = null;
                                offlineItem.setDisable(false);
                                boxAuthServers.setDisable(false);
                                boxMethods.getChildren().setAll(title, microsoftItem, offlineItem, boxAuthServers);
                            }
                        });
                    } else {
                        boxMethods.getChildren().setAll(title, microsoftItem, offlineItem, boxAuthServers);
                    }
                }

                AdvancedListItem addAuthServerItem = new AdvancedListItem();
                {
                    addAuthServerItem.getStyleClass().add("navigation-drawer-item");
                    addAuthServerItem.setTitle(i18n("account.injector.add"));
                    addAuthServerItem.setSubtitle(i18n("account.methods.authlib_injector"));
                    addAuthServerItem.setActionButtonVisible(false);
                    addAuthServerItem.setLeftGraphic(wrap(SVG.ADD_CIRCLE));
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
                FXUtils.smoothScrolling(scrollPane);

                setCenter(scrollPane);
            }
        }
    }
}
