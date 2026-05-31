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
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.effects.JFXDepthManager;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Locale;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.createSelectedItemPropertyFor;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class AccountListPage extends DecoratorAnimatedPage implements DecoratorPage {
    static final BooleanProperty RESTRICTED = new SimpleBooleanProperty(true);

    static {
        String property = System.getProperty("hmcl.offline.auth.restricted", "auto");

        if ("false".equals(property)
                || "auto".equals(property) && LocaleUtils.IS_CHINA_MAINLAND
                || globalConfig().isEnableOfflineAccount())
            RESTRICTED.set(false);
        else
            globalConfig().enableOfflineAccountProperty().addListener(new ChangeListener<>() {
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
    private final ObservableList<AccountListItem> displayedItems;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.manage")));
    private final ListProperty<Account> accounts = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());
    private final ListProperty<AuthlibInjectorServer> authServers = new SimpleListProperty<>(this, "authServers", FXCollections.observableArrayList());
    private final ObjectProperty<Account> selectedAccount;

    private final StringProperty searchingText = new SimpleStringProperty(this, "searchingText", "");
    private final BooleanBinding isSearching = Bindings.createBooleanBinding(() -> StringUtils.isNotBlank(searchingText.get()), searchingText);

    public AccountListPage() {
        items = MappedObservableList.create(accounts, (account) -> new AccountListItem(account, this));
        displayedItems = FXCollections.observableArrayList(items);
        selectedAccount = createSelectedItemPropertyFor(items, Account.class);

        InvalidationListener listener = (observable) -> {
            String text = searchingText.get().toLowerCase(Locale.ROOT);
            if (StringUtils.isBlank(text)) {
                displayedItems.setAll(items);
                return;
            }
            displayedItems.setAll(
                    items.stream().filter(item -> {
                        Account account = item.getAccount();
                        String type = "";
                        if (account instanceof MicrosoftAccount) type = "microsoft";
                        else if (account instanceof OfflineAccount) type = "offline";
                        else if (account instanceof AuthlibInjectorAccount) type = ((AuthlibInjectorAccount) account).getServer().getUrl().toLowerCase(Locale.ROOT);
                        return account.getCharacter().toLowerCase(Locale.ROOT).contains(text)
                                || account.getUsername().toLowerCase(Locale.ROOT).contains(text)
                                || account.getUUID().toString().contains(text)
                                || type.contains(text);
                    }).toList()
            );
        };
        items.addListener(listener);
        searchingText.addListener(listener);
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

    public BooleanBinding isSearching() {
        return isSearching;
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
                    microsoftItem.setTitle(i18n("account.methods.microsoft"));
                    microsoftItem.setLeftIcon(SVG.MICROSOFT);
                    microsoftItem.setOnAction(e -> Controllers.dialog(new MicrosoftAccountLoginPane()));

                    AdvancedListItem offlineItem = new AdvancedListItem();
                    offlineItem.getStyleClass().add("navigation-drawer-item");
                    offlineItem.setTitle(i18n("account.methods.offline"));
                    offlineItem.setLeftIcon(SVG.PERSON);
                    offlineItem.setOnAction(e -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));

                    VBox boxAuthServers = new VBox();
                    authServerItems = MappedObservableList.create(skinnable.authServersProperty(), server -> {
                        AdvancedListItem item = new AdvancedListItem();
                        item.getStyleClass().add("navigation-drawer-item");
                        item.setLeftIcon(SVG.DRESSER);
                        item.setOnAction(e -> Controllers.dialog(new CreateAccountPane(server)));
                        item.setRightAction(SVG.CLOSE, () -> Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                            skinnable.authServersProperty().remove(server);
                        }, null));

                        ObservableValue<String> title = BindingMapping.of(server, AuthlibInjectorServer::getName);
                        item.titleProperty().bind(title);
                        String host = "";
                        try {
                            host = NetworkUtils.toURI(server.getUrl()).getHost();
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
                    addAuthServerItem.setLeftIcon(SVG.ADD_CIRCLE);
                    addAuthServerItem.setOnAction(e -> Controllers.dialog(new AddAuthlibInjectorServerPane()));
                    VBox.setMargin(addAuthServerItem, new Insets(0, 0, 12, 0));
                }

                ScrollPane scrollPane = new ScrollPane(boxMethods);
                VBox.setVgrow(scrollPane, Priority.ALWAYS);
                setLeft(scrollPane, addAuthServerItem);
            }

            HBox searchBar = new HBox();
            {
                JFXTextField searchField = new JFXTextField();
                searchField.setPromptText(i18n("search"));
                HBox.setHgrow(searchField, Priority.ALWAYS);
                PauseTransition pause = new PauseTransition(Duration.millis(100));
                pause.setOnFinished(e -> skinnable.searchingText.set(searchField.getText()));
                searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                    pause.setRate(1);
                    pause.playFromStart();
                });
                JFXButton btnClearSearch = createToolbarButton2(null, SVG.CLOSE, searchField::clear);
                onEscPressed(searchField, btnClearSearch::fire);

                searchBar.getChildren().setAll(searchField, btnClearSearch);
                searchBar.getStyleClass().add("card");
                searchBar.setSpacing(1);
                VBox.setMargin(searchBar, new Insets(10, 10, 5, 10));
                JFXDepthManager.setDepth(searchBar, 1);
            }

            ScrollPane scrollPane = new ScrollPane();
            VBox list = new VBox();
            {
                scrollPane.setFitToWidth(true);

                list.maxWidthProperty().bind(scrollPane.widthProperty());
                list.setSpacing(10);
                list.getStyleClass().add("card-list");

                list.setOnDragOver((event) -> {
                    if (event.getGestureSource() != list && event.getDragboard().hasString()) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                    event.consume();
                });

                list.setOnDragDropped((event) -> {
                    Dragboard db = event.getDragboard();
                    boolean success = false;
                    if (db.hasString()) {
                        String accountId = db.getString();
                        int targetIndex = getTargetIndex(list, event.getY());

                        // Find the account in the original list
                        Account draggedAccount = null;
                        int sourceIndex = -1;
                        for (int i = 0; i < Accounts.getAccounts().size(); i++) {
                            if (Accounts.getAccounts().get(i).getIdentifier().equals(accountId)) {
                                draggedAccount = Accounts.getAccounts().get(i);
                                sourceIndex = i;
                                break;
                            }
                        }

                        boolean selected = skinnable.selectedAccountProperty().get() == draggedAccount;
                        if (draggedAccount != null && sourceIndex != targetIndex) {
                            // Remove from old position
                            Accounts.skipSelectionCheckFlag = true;
                            try {
                                Accounts.getAccounts().remove(sourceIndex);
                                // Insert at new position
                                int newIndex = targetIndex > sourceIndex ? targetIndex - 1 : targetIndex;
                                if (newIndex < 0) newIndex = 0;
                                if (newIndex > Accounts.getAccounts().size()) newIndex = Accounts.getAccounts().size();
                                Accounts.getAccounts().add(newIndex, draggedAccount);
                                if (selected) skinnable.selectedAccountProperty().set(draggedAccount);
                            } finally {
                                Accounts.skipSelectionCheckFlag = false;
                            }
                            success = true;
                        }
                    }
                    event.setDropCompleted(success);
                    event.consume();
                });

                Bindings.bindContent(list.getChildren(), skinnable.displayedItems);

                scrollPane.setContent(list);
                FXUtils.smoothScrolling(scrollPane);
            }

            setCenter(new VBox(searchBar, scrollPane));
        }

        private int getTargetIndex(VBox list, double y) {
            int index = 0;
            for (int i = 0; i < list.getChildren().size(); i++) {
                javafx.scene.Node child = list.getChildren().get(i);
                if (child.getLayoutY() + child.getBoundsInParent().getHeight() / 2 > y) {
                    return i;
                }
                index = i + 1;
            }
            return index;
        }
    }
}
