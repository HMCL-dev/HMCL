// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.account;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.RegionUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Locale;

import static org.jackhuang.hmcl.setting.SettingsManager.userSettings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;

/// Displays account creation methods and the configured account list.
public final class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {
    static final BooleanProperty RESTRICTED = new SimpleBooleanProperty(true);

    static {
        String property = System.getProperty("hmcl.offline.auth.restricted", "auto");

        if ("false".equals(property)
                || "auto".equals(property) && "CN".equals(RegionUtils.MACHINE_REGION)
                || SettingsManager.userSettings().enableOfflineAccountProperty().get())
            RESTRICTED.set(false);
        else
            userSettings().enableOfflineAccountProperty().addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> o, Boolean oldValue, Boolean newValue) {
                    if (newValue) {
                        userSettings().enableOfflineAccountProperty().removeListener(this);
                        RESTRICTED.set(false);
                    }
                }
            });
    }

    /// The account entries derived from the configured accounts.
    private final @UnmodifiableView ObservableList<AccountListItem> items;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage")));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ListProperty<AuthlibInjectorServer> authServers = new SimpleListProperty<>(this, "authServers", FXCollections.observableArrayList());
    /// The selected account, or null when no entry is selected.
    private final ObjectProperty<@Nullable Account> selectedAccount;

    /// Keeps the mapped server entries alive while their content binding is active.
    private final @UnmodifiableView ObservableList<AdvancedListItem> authServerItems;
    /// Retains the weak restriction listener until account creation becomes available.
    private @Nullable ChangeListener<Boolean> holder;

    /// Creates the account lists, sidebar actions, and live view bindings.
    public AccountListPage() {
        items = MappedObservableList.create(accounts, AccountListItem::new);
        selectedAccount = createSelectedItemPropertyFor(items, Account.class);

        {
            VBox boxMethods = new VBox();
            {
                AdvancedListItem microsoftItem = new AdvancedListItem();
                microsoftItem.getStyleClass().add("navigation-drawer-item");
                microsoftItem.setTitle(i18n("account.methods.microsoft"));
                microsoftItem.setLeftIcon(SVG.MICROSOFT);
                microsoftItem.setOnAction(e -> {
                    if (SettingsManager.isUserGameAccountsReadOnly()) {
                        confirmOverwriteUserAccounts(() -> Controllers.dialog(new MicrosoftAccountLoginPane()));
                    } else {
                        Controllers.dialog(new MicrosoftAccountLoginPane());
                    }
                });

                AdvancedListItem offlineItem = new AdvancedListItem();
                offlineItem.getStyleClass().add("navigation-drawer-item");
                offlineItem.setTitle(i18n("account.methods.offline"));
                offlineItem.setLeftIcon(SVG.PERSON);
                offlineItem.setOnAction(e -> {
                    if (SettingsManager.isUserGameAccountsReadOnly()) {
                        confirmOverwriteUserAccounts(() -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));
                    } else {
                        Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE));
                    }
                });

                VBox boxAuthServers = new VBox();
                authServerItems = MappedObservableList.create(authServersProperty(), server -> {
                    AdvancedListItem item = new AdvancedListItem();
                    item.getStyleClass().add("navigation-drawer-item");
                    item.setLeftIcon(SVG.DRESSER);
                    item.setOnAction(e -> {
                        if (SettingsManager.isUserGameAccountsReadOnly()) {
                            confirmOverwriteUserAccounts(() -> Controllers.dialog(new CreateAccountPane(server)));
                        } else {
                            Controllers.dialog(new CreateAccountPane(server));
                        }
                    });
                    item.setRightAction(SVG.CLOSE, () -> {
                        if (SettingsManager.isAuthlibInjectorServersReadOnly()) {
                            confirmOverwriteAuthlibInjectorServers(() -> confirmRemoveAuthlibInjectorServer(server));
                        } else {
                            confirmRemoveAuthlibInjectorServer(server);
                        }
                    });

                    ObservableValue<String> title = BindingMapping.of(server, AuthlibInjectorServer::getName);
                    item.titleProperty().bind(title);
                    item.setSubtitle(server.getDisplayHostUrl());
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
                addAuthServerItem.setLeftIcon(SVG.ADD_CIRCLE);
                addAuthServerItem.setOnAction(e -> {
                    if (SettingsManager.isAuthlibInjectorServersReadOnly()) {
                        confirmOverwriteAuthlibInjectorServers(
                                () -> Controllers.dialog(new AddAuthlibInjectorServerPane()));
                    } else {
                        Controllers.dialog(new AddAuthlibInjectorServerPane());
                    }
                });
            }

            getSidebar().add(boxMethods);
            setSidebarBottom(addAuthServerItem);
        }

        ScrollPane scrollPane = new ScrollPane();
        VBox list = new VBox();
        scrollPane.setFitToWidth(true);

        list.maxWidthProperty().bind(scrollPane.widthProperty());
        list.setSpacing(10);
        list.getStyleClass().add("card-list");

        Bindings.bindContent(list.getChildren(), items);

        scrollPane.setContent(list);
        FXUtils.smoothScrolling(scrollPane);

        setContent(scrollPane);
    }

    /// Returns the account selection shared with the launcher.
    public ObjectProperty<@Nullable Account> selectedAccountProperty() {
        return selectedAccount;
    }

    /// Returns the configured accounts displayed by this page.
    public ListProperty<Account> accountsProperty() {
        return accounts;
    }

    /// Returns the account management navigation state.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Returns the authentication servers displayed in the sidebar.
    public ListProperty<AuthlibInjectorServer> authServersProperty() {
        return authServers;
    }

    /// Confirms overwriting the user account files before continuing the account operation.
    private static void confirmOverwriteUserAccounts(Runnable action) {
        Controllers.confirmBackupAndOverwrite(i18n("account.storage.read_only"), () -> {
            SettingsManager.forceOverwriteUserGameAccounts();
            action.run();
        });
    }

    /// Confirms overwriting the authlib-injector server list before continuing the server operation.
    private static void confirmOverwriteAuthlibInjectorServers(Runnable action) {
        Controllers.confirmBackupAndOverwrite(i18n("account.injector.server.storage.read_only"), () -> {
            SettingsManager.forceOverwriteAuthlibInjectorServers();
            action.run();
        });
    }

    /// Asks the user to confirm removing an authlib-injector server.
    private void confirmRemoveAuthlibInjectorServer(AuthlibInjectorServer server) {
        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
            authServers.remove(server);
        }, null);
    }
}
