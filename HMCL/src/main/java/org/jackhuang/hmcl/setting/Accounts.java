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
package org.jackhuang.hmcl.setting;

import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.*;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.skin.InvalidSkinException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import static java.util.stream.Collectors.toList;
import static javafx.collections.FXCollections.observableArrayList;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.Lang.immutableListOf;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {}

    private static final AuthlibInjectorArtifactProvider AUTHLIB_INJECTOR_DOWNLOADER = createAuthlibInjectorArtifactProvider();
    private static void triggerAuthlibInjectorUpdateCheck() {
        if (AUTHLIB_INJECTOR_DOWNLOADER instanceof AuthlibInjectorDownloader) {
            Schedulers.io().execute(() -> {
                try {
                    ((AuthlibInjectorDownloader) AUTHLIB_INJECTOR_DOWNLOADER).checkUpdate();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to check update for authlib-injector", e);
                }
            });
        }
    }

    public static final OAuthServer.Factory OAUTH_CALLBACK = new OAuthServer.Factory();

    public static final AuthlibInjectorAccountFactory FACTORY_AUTHLIB_INJECTOR = new AuthlibInjectorAccountFactory(AUTHLIB_INJECTOR_DOWNLOADER, Accounts::getOrCreateAuthlibInjectorServer);
    public static final List<AccountFactory<?>> FACTORIES = immutableListOf(FACTORY_AUTHLIB_INJECTOR);

    // ==== login type / account factory mapping ====
    private static final Map<String, AccountFactory<?>> type2factory = new HashMap<>();
    private static final Map<AccountFactory<?>, String> factory2type = new HashMap<>();
    static {
        type2factory.put("authlibInjector", FACTORY_AUTHLIB_INJECTOR);

        type2factory.forEach((type, factory) -> factory2type.put(factory, type));
    }

    public static String getLoginType(AccountFactory<?> factory) {
        return Optional.ofNullable(factory2type.get(factory))
                .orElseThrow(() -> new IllegalArgumentException("Unrecognized account factory"));
    }

    public static AccountFactory<?> getAccountFactory(String loginType) {
        return Optional.ofNullable(type2factory.get(loginType))
                .orElseThrow(() -> new IllegalArgumentException("Unrecognized login type"));
    }
    // ====

    public static AccountFactory<?> getAccountFactory(Account account) {
        if (account instanceof AuthlibInjectorAccount)
            return FACTORY_AUTHLIB_INJECTOR;
        else
            throw new IllegalArgumentException("Failed to determine account type: " + account);
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
            updateAccountStorages();
        }
    };

    /**
     * True if {@link #init()} hasn't been called.
     */
    private static boolean initialized = false;

    static {
        accounts.addListener(onInvalidating(Accounts::updateAccountStorages));
    }

    private static Map<Object, Object> getAccountStorage(Account account) {
        Map<Object, Object> storage = account.toStorage();
        storage.put("type", getLoginType(getAccountFactory(account)));
        if (account == selectedAccount.get()) {
            storage.put("selected", true);
        }
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

            if (Boolean.TRUE.equals(storage.get("selected"))) {
                selectedAccount.set(account);
            }
        });

        initialized = true;

        config().getAuthlibInjectorServers().addListener(onInvalidating(Accounts::removeDanglingAuthlibInjectorAccounts));

        Account selected = selectedAccount.get();
        if (selected != null) {
            Schedulers.io().execute(() -> {
                try {
                    selected.logIn();
                } catch (AuthenticationException e) {
                    LOG.log(Level.WARNING, "Failed to log " + selected + " in", e);
                }
            });
        }

        if (!config().getAuthlibInjectorServers().isEmpty()) {
            triggerAuthlibInjectorUpdateCheck();
        }

        for (AuthlibInjectorServer server : config().getAuthlibInjectorServers()) {
            if (selected instanceof AuthlibInjectorAccount && ((AuthlibInjectorAccount) selected).getServer() == server)
                continue;
            Schedulers.io().execute(() -> {
                try {
                    server.fetchMetadataResponse();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to fetch authlib-injector server metdata: " + server, e);
                }
            });
        }
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
    private static AuthlibInjectorArtifactProvider createAuthlibInjectorArtifactProvider() {
        String authlibinjectorLocation = System.getProperty("hmcl.authlibinjector.location");
        if (authlibinjectorLocation == null) {
            return new AuthlibInjectorDownloader(
                    Metadata.PTL_DIRECTORY.resolve("authlib-injector.jar"),
                    DownloadProviders::getDownloadProvider) {
                @Override
                public Optional<AuthlibInjectorArtifactInfo> getArtifactInfoImmediately() {
                    Optional<AuthlibInjectorArtifactInfo> local = super.getArtifactInfoImmediately();
                    if (local.isPresent()) {
                        return local;
                    }
                    // search authlib-injector.jar in current directory, it's used as a fallback
                    return parseArtifact(Paths.get("authlib-injector.jar"));
                }
            };
        } else {
            LOG.info("Using specified authlib-injector: " + authlibinjectorLocation);
            return new SimpleAuthlibInjectorArtifactProvider(Paths.get(authlibinjectorLocation));
        }
    }

    private static AuthlibInjectorServer getOrCreateAuthlibInjectorServer(String url) {
        return config().getAuthlibInjectorServers().stream()
                .filter(server -> url.equals(server.getUrl()))
                .findFirst()
                .orElseGet(() -> {
                    AuthlibInjectorServer server = new AuthlibInjectorServer(url);
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
    private static Map<AccountFactory<?>, String> unlocalizedLoginTypeNames = mapOf(
            pair(Accounts.FACTORY_AUTHLIB_INJECTOR, "account.methods.authlib_injector"));

    public static String getLocalizedLoginTypeName(AccountFactory<?> factory) {
        return i18n(Optional.ofNullable(unlocalizedLoginTypeNames.get(factory))
                .orElseThrow(() -> new IllegalArgumentException("Unrecognized account factory")));
    }
    // ====

    public static String localizeErrorMessage(Exception exception) {
        if (exception instanceof NoCharacterException) {
            return i18n("account.failed.no_character");
        } else if (exception instanceof ServerDisconnectException) {
            return i18n("account.failed.connect_authentication_server");
        } else if (exception instanceof ServerResponseMalformedException) {
            return i18n("account.failed.server_response_malformed");
        } else if (exception instanceof RemoteAuthenticationException) {
            RemoteAuthenticationException remoteException = (RemoteAuthenticationException) exception;
            String remoteMessage = remoteException.getRemoteMessage();
            if ("ForbiddenOperationException".equals(remoteException.getRemoteName()) && remoteMessage != null) {
                if (remoteMessage.contains("Invalid credentials")) {
                    return i18n("account.failed.invalid_credentials");
                } else if (remoteMessage.contains("Invalid token")) {
                    return i18n("account.failed.invalid_token");
                } else if (remoteMessage.contains("Invalid username or password")) {
                    return i18n("account.failed.invalid_password");
                } else {
                    return remoteMessage;
                }
            } else if ("ResourceException".equals(remoteException.getRemoteName()) && remoteMessage != null) {
                if (remoteMessage.contains("The requested resource is no longer available")) {
                    return i18n("account.failed.migration");
                } else {
                    return remoteMessage;
                }
            }
            return exception.getMessage();
        } else if (exception instanceof AuthlibInjectorDownloadException) {
            return i18n("account.failed.injector_download_failure");
        } else if (exception instanceof CharacterDeletedException) {
            return i18n("account.failed.character_deleted");
        } else if (exception instanceof InvalidSkinException) {
            return i18n("account.skin.invalid_skin");
        } else if (exception instanceof OAuthServer.MicrosoftAuthenticationNotSupportedException) {
            return i18n("account.methods.microsoft.snapshot");
        } else if (exception instanceof OAuthAccount.WrongAccountException) {
            return i18n("account.failed.wrong_account");
        } else if (exception.getClass() == AuthenticationException.class) {
            return exception.getLocalizedMessage();
        } else {
            return exception.getClass().getName() + ": " + exception.getLocalizedMessage();
        }
    }
}
