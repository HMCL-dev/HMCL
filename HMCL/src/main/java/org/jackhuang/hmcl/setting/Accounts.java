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

import com.google.gson.reflect.TypeToken;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.*;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccountFactory;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.skin.InvalidSkinException;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

    public static final OfflineAccountFactory FACTORY_OFFLINE = new OfflineAccountFactory(AUTHLIB_INJECTOR_DOWNLOADER);
    public static final YggdrasilAccountFactory FACTORY_MOJANG = YggdrasilAccountFactory.MOJANG;
    public static final AuthlibInjectorAccountFactory FACTORY_AUTHLIB_INJECTOR = new AuthlibInjectorAccountFactory(AUTHLIB_INJECTOR_DOWNLOADER, Accounts::getOrCreateAuthlibInjectorServer);
    public static final MicrosoftAccountFactory FACTORY_MICROSOFT = new MicrosoftAccountFactory(new MicrosoftService(OAUTH_CALLBACK));
    public static final List<AccountFactory<?>> FACTORIES = immutableListOf(FACTORY_OFFLINE, FACTORY_MOJANG, FACTORY_MICROSOFT, FACTORY_AUTHLIB_INJECTOR);

    // ==== login type / account factory mapping ====
    private static final Map<String, AccountFactory<?>> type2factory = new HashMap<>();
    private static final Map<AccountFactory<?>, String> factory2type = new HashMap<>();
    static {
        type2factory.put("offline", FACTORY_OFFLINE);
        type2factory.put("yggdrasil", FACTORY_MOJANG);
        type2factory.put("authlibInjector", FACTORY_AUTHLIB_INJECTOR);
        type2factory.put("microsoft", FACTORY_MICROSOFT);

        type2factory.forEach((type, factory) -> factory2type.put(factory, type));
    }

    public static String getLoginType(AccountFactory<?> factory) {
        String type = factory2type.get(factory);
        if (type != null) return type;

        if (factory instanceof BoundAuthlibInjectorAccountFactory) {
            return factory2type.get(FACTORY_AUTHLIB_INJECTOR);
        }

        throw new IllegalArgumentException("Unrecognized account factory");
    }

    public static AccountFactory<?> getAccountFactory(String loginType) {
        return Optional.ofNullable(type2factory.get(loginType))
                .orElseThrow(() -> new IllegalArgumentException("Unrecognized login type"));
    }

    public static BoundAuthlibInjectorAccountFactory getAccountFactoryByAuthlibInjectorServer(AuthlibInjectorServer server) {
        return new BoundAuthlibInjectorAccountFactory(AUTHLIB_INJECTOR_DOWNLOADER, server);
    }
    // ====

    public static AccountFactory<?> getAccountFactory(Account account) {
        if (account instanceof OfflineAccount)
            return FACTORY_OFFLINE;
        else if (account instanceof AuthlibInjectorAccount)
            return FACTORY_AUTHLIB_INJECTOR;
        else if (account instanceof YggdrasilAccount)
            return FACTORY_MOJANG;
        else if (account instanceof MicrosoftAccount)
            return FACTORY_MICROSOFT;
        else
            throw new IllegalArgumentException("Failed to determine account type: " + account);
    }

    private static final String GLOBAL_PREFIX = "$GLOBAL:";
    private static final ObservableList<Map<Object, Object>> globalAccountStorages = FXCollections.observableArrayList();

    private static final ObservableList<Account> accounts = observableArrayList(account -> new Observable[] { account });
    private static final ObjectProperty<Account> selectedAccount = new SimpleObjectProperty<>(Accounts.class, "selectedAccount");

    /**
     * True if {@link #init()} hasn't been called.
     */
    private static boolean initialized = false;

    private static Map<Object, Object> getAccountStorage(Account account) {
        Map<Object, Object> storage = account.toStorage();
        storage.put("type", getLoginType(getAccountFactory(account)));
        return storage;
    }

    private static void updateAccountStorages() {
        // don't update the underlying storage before data loading is completed
        // otherwise it might cause data loss
        if (!initialized)
            return;
        // update storage

        ArrayList<Map<Object, Object>> global = new ArrayList<>();
        ArrayList<Map<Object, Object>> portable = new ArrayList<>();

        for (Account account : accounts) {
            Map<Object, Object> storage = getAccountStorage(account);
            if (account.isPortable())
                portable.add(storage);
            else
                global.add(storage);
        }

        if (!global.equals(globalAccountStorages))
            globalAccountStorages.setAll(global);
        if (!portable.equals(config().getAccountStorages()))
            config().getAccountStorages().setAll(portable);
    }

    @SuppressWarnings("unchecked")
    private static void loadGlobalAccountStorages() {
        Path globalAccountsFile = Metadata.HMCL_DIRECTORY.resolve("accounts.json");
        if (Files.exists(globalAccountsFile)) {
            try (Reader reader = Files.newBufferedReader(globalAccountsFile)) {
                globalAccountStorages.setAll((List<Map<Object, Object>>)
                        Config.CONFIG_GSON.fromJson(reader, new TypeToken<List<Map<Object, Object>>>() {
                        }.getType()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load global accounts", e);
            }
        }

        InvocationDispatcher<String> dispatcher = InvocationDispatcher.runOn(Lang::thread, json -> {
            LOG.info("Saving global accounts");
            synchronized (globalAccountsFile) {
                try {
                    synchronized (globalAccountsFile) {
                        FileUtils.saveSafely(globalAccountsFile, json);
                    }
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to save global accounts", e);
                }
            }
        });

        globalAccountStorages.addListener(onInvalidating(() ->
                dispatcher.accept(Config.CONFIG_GSON.toJson(globalAccountStorages))));
    }

    private static Account parseAccount(Map<Object, Object> storage) {
        AccountFactory<?> factory = type2factory.get(storage.get("type"));
        if (factory == null) {
            LOG.warning("Unrecognized account type: " + storage);
            return null;
        }

        try {
            return factory.fromStorage(storage);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load account: " + storage, e);
            return null;
        }
    }

    /**
     * Called when it's ready to load accounts from {@link ConfigHolder#config()}.
     */
    static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        if (!config().isAddedLittleSkin()) {
            AuthlibInjectorServer littleSkin = new AuthlibInjectorServer("https://littleskin.cn/api/yggdrasil/");

            if (config().getAuthlibInjectorServers().stream().noneMatch(it -> littleSkin.getUrl().equals(it.getUrl()))) {
                config().getAuthlibInjectorServers().add(0, littleSkin);
            }

            config().setAddedLittleSkin(true);
        }

        loadGlobalAccountStorages();

        // load accounts
        Account selected = null;
        for (Map<Object, Object> storage : config().getAccountStorages()) {
            Account account = parseAccount(storage);
            if (account != null) {
                account.setPortable(true);
                accounts.add(account);
                if (Boolean.TRUE.equals(storage.get("selected"))) {
                    selected = account;
                }
            }
        }

        for (Map<Object, Object> storage : globalAccountStorages) {
            Account account = parseAccount(storage);
            if (account != null) {
                accounts.add(account);
            }
        }

        String selectedAccountIdentifier = config().getSelectedAccount();
        if (selected == null && selectedAccountIdentifier != null) {
            boolean portable = true;
            if (selectedAccountIdentifier.startsWith(GLOBAL_PREFIX)) {
                portable = false;
                selectedAccountIdentifier = selectedAccountIdentifier.substring(GLOBAL_PREFIX.length());
            }

            for (Account account : accounts) {
                if (selectedAccountIdentifier.equals(account.getIdentifier())) {
                    if (portable == account.isPortable()) {
                        selected = account;
                        break;
                    } else if (selected == null) {
                        selected = account;
                    }
                }
            }
        }

        if (selected == null && !accounts.isEmpty()) {
            selected = accounts.get(0);
        }

        selectedAccount.set(selected);

        InvalidationListener listener = o -> {
            // this method first checks whether the current selection is valid
            // if it's valid, the underlying storage will be updated
            // otherwise, the first account will be selected as an alternative(or null if accounts is empty)
            Account account = selectedAccount.get();
            if (accounts.isEmpty()) {
                if (account == null) {
                    // valid
                } else {
                    // the previously selected account is gone, we can only set it to null here
                    selectedAccount.set(null);
                }
            } else {
                if (accounts.contains(account)) {
                    // valid
                } else {
                    // the previously selected account is gone
                    selectedAccount.set(accounts.get(0));
                }
            }
        };
        selectedAccount.addListener(listener);
        selectedAccount.addListener(onInvalidating(() -> {
            Account account = selectedAccount.get();
            if (account != null)
                config().setSelectedAccount(account.isPortable() ? account.getIdentifier() : GLOBAL_PREFIX + account.getIdentifier());
            else
                config().setSelectedAccount(null);
        }));
        accounts.addListener(listener);
        accounts.addListener(onInvalidating(Accounts::updateAccountStorages));

        initialized = true;

        config().getAuthlibInjectorServers().addListener(onInvalidating(Accounts::removeDanglingAuthlibInjectorAccounts));

        if (selected != null) {
            Account finalSelected = selected;
            Schedulers.io().execute(() -> {
                try {
                    finalSelected.logIn();
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "Failed to log " + finalSelected + " in", e);
                }
            });
        }

        triggerAuthlibInjectorUpdateCheck();

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
                    Metadata.HMCL_DIRECTORY.resolve("authlib-injector.jar"),
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
    private static final Map<AccountFactory<?>, String> unlocalizedLoginTypeNames = mapOf(
            pair(Accounts.FACTORY_OFFLINE, "account.methods.offline"),
            pair(Accounts.FACTORY_MOJANG, "account.methods.yggdrasil"),
            pair(Accounts.FACTORY_AUTHLIB_INJECTOR, "account.methods.authlib_injector"),
            pair(Accounts.FACTORY_MICROSOFT, "account.methods.microsoft"));

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
        } else if (exception instanceof MicrosoftService.XboxAuthorizationException) {
            long errorCode = ((MicrosoftService.XboxAuthorizationException) exception).getErrorCode();
            if (errorCode == MicrosoftService.XboxAuthorizationException.ADD_FAMILY) {
                return i18n("account.methods.microsoft.error.add_family");
            } else if (errorCode == MicrosoftService.XboxAuthorizationException.COUNTRY_UNAVAILABLE) {
                return i18n("account.methods.microsoft.error.country_unavailable");
            } else if (errorCode == MicrosoftService.XboxAuthorizationException.MISSING_XBOX_ACCOUNT) {
                return i18n("account.methods.microsoft.error.missing_xbox_account");
            } else {
                return i18n("account.methods.microsoft.error.unknown", errorCode);
            }
        } else if (exception instanceof MicrosoftService.NoMinecraftJavaEditionProfileException) {
            return i18n("account.methods.microsoft.error.no_character");
        } else if (exception instanceof MicrosoftService.NoXuiException) {
            return i18n("account.methods.microsoft.error.add_family_probably");
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
