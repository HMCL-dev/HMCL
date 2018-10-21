/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.setting;

import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDownloader;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.MojangYggdrasilProvider;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.task.Schedulers;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import static java.util.stream.Collectors.toList;
import static javafx.collections.FXCollections.observableArrayList;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {}

    public static final OfflineAccountFactory FACTORY_OFFLINE = OfflineAccountFactory.INSTANCE;
    public static final YggdrasilAccountFactory FACTORY_YGGDRASIL = new YggdrasilAccountFactory(MojangYggdrasilProvider.INSTANCE);
    public static final AuthlibInjectorAccountFactory FACTORY_AUTHLIB_INJECTOR = new AuthlibInjectorAccountFactory(
            new AuthlibInjectorDownloader(Metadata.HMCL_DIRECTORY, DownloadProviders::getDownloadProvider)::getArtifactInfo,
            Accounts::getOrCreateAuthlibInjectorServer);

    private static final String TYPE_OFFLINE = "offline";
    private static final String TYPE_YGGDRASIL_ACCOUNT = "yggdrasil";
    private static final String TYPE_AUTHLIB_INJECTOR = "authlibInjector";

    private static Map<String, AccountFactory<?>> type2factory = mapOf(
            pair(TYPE_OFFLINE, FACTORY_OFFLINE),
            pair(TYPE_YGGDRASIL_ACCOUNT, FACTORY_YGGDRASIL),
            pair(TYPE_AUTHLIB_INJECTOR, FACTORY_AUTHLIB_INJECTOR));

    private static String accountType(Account account) {
        if (account instanceof OfflineAccount)
            return TYPE_OFFLINE;
        else if (account instanceof AuthlibInjectorAccount)
            return TYPE_AUTHLIB_INJECTOR;
        else if (account instanceof YggdrasilAccount)
            return TYPE_YGGDRASIL_ACCOUNT;
        else
            throw new IllegalArgumentException("Failed to determine account type: " + account);
    }

    public static AccountFactory<?> getAccountFactory(Account account) {
        return type2factory.get(accountType(account));
    }

    static String accountId(Account account) {
        return account.getUsername() + ":" + account.getCharacter();
    }

    private static ObservableList<Account> accounts = observableArrayList(account -> new Observable[] { account });
    private static ReadOnlyListWrapper<Account> accountsWrapper = new ReadOnlyListWrapper<>(Accounts.class, "accounts", accounts);

    private static ObjectProperty<Account> selectedAccount = new SimpleObjectProperty<Account>(Accounts.class, "selectedAccount") {
        {
            accounts.addListener(onInvalidating(this::invalidated));
        }

        @Override
        protected void invalidated() {
            // this methods first checks whether the current selection is valid
            // if it's valid, the underlying storage will be updated
            // otherwise, the first account will be selected as an alternative(or null if accounts is empty)
            Account selected = get();
            if (accounts.isEmpty()) {
                if (selected == null) {
                    // valid
                } else {
                    // the previously selected account is gone, we can only set it to null here
                    set(null);
                    return;
                }
            } else {
                if (accounts.contains(selected)) {
                    // valid
                } else {
                    // the previously selected account is gone
                    set(accounts.get(0));
                    return;
                }
            }
            // selection is valid, store it
            if (!initialized)
                return;
            config().setSelectedAccount(selected == null ? "" : accountId(selected));
        }
    };

    /**
     * True if {@link #init()} hasn't been called.
     */
    private static boolean initialized = false;

    static {
        accounts.addListener(onInvalidating(Accounts::updateAccountStorages));
    }

    static Map<Object, Object> getAccountStorage(Account account) {
        Map<Object, Object> storage = account.toStorage();
        storage.put("type", accountType(account));
        return storage;
    }

    private static void updateAccountStorages() {
        // don't update the underlying storage before data loading is completed
        // otherwise it might cause data loss
        if (!initialized)
            return;
        // update storage
        config().getAccountStorages().setAll(accounts.stream().map(Accounts::getAccountStorage).collect(toList()));
    }

    /**
     * Called when it's ready to load accounts from {@link ConfigHolder#config()}.
     */
    static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        // load accounts
        config().getAccountStorages().forEach(storage -> {
            AccountFactory<?> factory = type2factory.get(storage.get("type"));
            if (factory == null) {
                LOG.warning("Unrecognized account type: " + storage);
                return;
            }
            Account account;
            try {
                account = factory.fromStorage(storage);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to load account: " + storage, e);
                return;
            }
            accounts.add(account);
        });

        initialized = true;

        config().getAuthlibInjectorServers().addListener(onInvalidating(Accounts::removeDanglingAuthlibInjectorAccounts));

        // load selected account
        Account selected = accounts.stream()
                .filter(it -> accountId(it).equals(config().getSelectedAccount()))
                .findFirst()
                .orElse(null);
        selectedAccount.set(selected);

        Schedulers.io().schedule(() -> {
            if (selected != null)
                try {
                    selected.logIn();
                } catch (AuthenticationException e) {
                    LOG.log(Level.WARNING, "Failed to log " + selected + " in", e);
                }
        });
    }

    public static ObservableList<Account> getAccounts() {
        return accounts;
    }

    public static ReadOnlyListProperty<Account> accountsProperty() {
        return accountsWrapper.getReadOnlyProperty();
    }

    public static Account getSelectedAccount() {
        return selectedAccount.get();
    }

    public static void setSelectedAccount(Account selectedAccount) {
        Accounts.selectedAccount.set(selectedAccount);
    }

    public static ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    // ==== authlib-injector ====
    private static AuthlibInjectorServer getOrCreateAuthlibInjectorServer(String url) {
        return config().getAuthlibInjectorServers().stream()
                .filter(server -> url.equals(server.getUrl()))
                .findFirst()
                .orElseGet(() -> {
                    // this usually happens when migrating data from an older version
                    AuthlibInjectorServer server;
                    try {
                        server = AuthlibInjectorServer.fetchServerInfo(url);
                        LOG.info("Migrated authlib injector server " + server);
                    } catch (IOException e) {
                        server = new AuthlibInjectorServer(url, url);
                        LOG.log(Level.WARNING, "Failed to migrate authlib injector server " + url, e);
                    }

                    config().getAuthlibInjectorServers().add(server);
                    return server;
                });
    }

    /**
     * After an {@link AuthlibInjectorServer} is removed, the associated accounts should also be removed.
     * This method performs a check and removes the dangling accounts.
     */
    private static void removeDanglingAuthlibInjectorAccounts() {
        accounts.stream()
                .filter(AuthlibInjectorAccount.class::isInstance)
                .map(AuthlibInjectorAccount.class::cast)
                .filter(it -> !config().getAuthlibInjectorServers().contains(it.getServer()))
                .collect(toList())
                .forEach(accounts::remove);
    }
    // ====

    // ==== Login type name i18n ===
    private static Map<AccountFactory<?>, String> loginType2name = mapOf(
            pair(Accounts.FACTORY_OFFLINE, i18n("account.methods.offline")),
            pair(Accounts.FACTORY_YGGDRASIL, i18n("account.methods.yggdrasil")),
            pair(Accounts.FACTORY_AUTHLIB_INJECTOR, i18n("account.methods.authlib_injector")));

    public static String getAccountTypeName(AccountFactory<?> factory) {
        return Optional.ofNullable(loginType2name.get(factory))
                .orElseThrow(() -> new IllegalArgumentException("No corresponding login type name"));
    }

    public static String getAccountTypeName(Account account) {
        return getAccountTypeName(getAccountFactory(account));
    }
    // ====
}
