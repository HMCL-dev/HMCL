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

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
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
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.skin.InvalidSkinException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static javafx.collections.FXCollections.observableArrayList;
import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.getAccountStorages;
import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.setting.SettingsManager.getUserAccountStorages;
import static org.jackhuang.hmcl.setting.SettingsManager.userSettings;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.Lang.immutableListOf;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class Accounts {
    private Accounts() {
    }

    private static final AuthlibInjectorArtifactProvider AUTHLIB_INJECTOR_DOWNLOADER = createAuthlibInjectorArtifactProvider();

    public static final OAuthServer.Factory OAUTH_CALLBACK = new OAuthServer.Factory();

    public static final OfflineAccountFactory FACTORY_OFFLINE = new OfflineAccountFactory(AUTHLIB_INJECTOR_DOWNLOADER);
    public static final AuthlibInjectorAccountFactory FACTORY_AUTHLIB_INJECTOR = new AuthlibInjectorAccountFactory(AUTHLIB_INJECTOR_DOWNLOADER, Accounts::getOrCreateAuthlibInjectorServer);
    public static final MicrosoftAccountFactory FACTORY_MICROSOFT = new MicrosoftAccountFactory(new MicrosoftService(OAUTH_CALLBACK));
    public static final List<AccountFactory<?>> FACTORIES = immutableListOf(FACTORY_OFFLINE, FACTORY_MICROSOFT, FACTORY_AUTHLIB_INJECTOR);

    // ==== login type / account factory mapping ====
    private static final Map<String, AccountFactory<?>> type2factory = new HashMap<>();
    private static final Map<AccountFactory<?>, String> factory2type = new HashMap<>();

    static {
        type2factory.put("offline", FACTORY_OFFLINE);
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
        else if (account instanceof MicrosoftAccount)
            return FACTORY_MICROSOFT;
        else
            throw new IllegalArgumentException("Failed to determine account type: " + account);
    }

    private static final ObservableList<Account> accounts = observableArrayList(account -> new Observable[]{account});
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

    /// Creates the selected-account reference stored in launcher settings.
    private static AccountID toSelectedAccountReference(Account account) {
        return account.toIdentifier();
    }

    /// Returns whether the given account is identified by a selected-account reference.
    private static boolean matchesSelectedAccountReference(Account account, AccountID reference) {
        return account.matchIdentifier(reference);
    }

    /// Ensures account IDs are unique across local and shared account storages before accounts are instantiated.
    private static void ensureUniqueAccountIDs() {
        Set<String> usedAccountIDs = new HashSet<>();
        LegacyConfigMigrator.assignAccountIDs(SettingsManager.gameAccounts(), usedAccountIDs, false);
        LegacyConfigMigrator.assignAccountIDs(SettingsManager.userGameAccounts(), usedAccountIDs, true);
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

        ObservableList<Map<Object, Object>> globalStorages = getUserAccountStorages();
        if (!SettingsManager.isUserGameAccountsReadOnly() && !global.equals(globalStorages))
            globalStorages.setAll(global);
        if (!SettingsManager.isGameAccountsReadOnly() && !portable.equals(getAccountStorages()))
            getAccountStorages().setAll(portable);
    }

    /// Returns whether the account metadata and credential files selected by the portability flag are read-only.
    ///
    /// @param portable whether the account is stored in the local account file
    public static boolean isAccountStorageReadOnly(boolean portable) {
        return portable ? SettingsManager.isGameAccountsReadOnly() : SettingsManager.isUserGameAccountsReadOnly();
    }

    /// Returns whether the storage file containing the given account is read-only.
    public static boolean isAccountStorageReadOnly(Account account) {
        return isAccountStorageReadOnly(account.isPortable());
    }

    /// Returns whether the given account may be removed from its current storage file.
    public static boolean canRemoveAccount(Account account) {
        return !isAccountStorageReadOnly(account);
    }

    /// Returns whether the given account may be moved between local and user storage files.
    public static boolean canMoveAccount(Account account) {
        return !SettingsManager.isGameAccountsReadOnly() && !SettingsManager.isUserGameAccountsReadOnly();
    }

    /// Backs up and overwrites the account metadata and credential files selected by the portability flag.
    ///
    /// @param portable whether the target storage is the local account file
    public static void forceOverwriteAccountStorage(boolean portable) {
        if (portable) {
            SettingsManager.forceOverwriteGameAccounts();
        } else {
            SettingsManager.forceOverwriteUserGameAccounts();
        }
    }

    /// Backs up and overwrites the storage file containing the given account.
    public static void forceOverwriteAccountStorage(Account account) {
        forceOverwriteAccountStorage(account.isPortable());
    }

    /// Backs up and overwrites both local and user account metadata and credential files.
    public static void forceOverwriteAccountStorages() {
        if (SettingsManager.isGameAccountsReadOnly()) {
            SettingsManager.forceOverwriteGameAccounts();
        }
        if (SettingsManager.isUserGameAccountsReadOnly()) {
            SettingsManager.forceOverwriteUserGameAccounts();
        }
    }

    private static Account parseAccount(Map<Object, Object> storage) {
        AccountFactory<?> factory = type2factory.get(storage.get("type"));
        if (factory == null) {
            LOG.warning("Unrecognized account type: " + accountIdentifier(storage));
            return null;
        }

        try {
            return factory.fromStorage(storage);
        } catch (Exception e) {
            LOG.warning("Failed to load account: " + accountIdentifier(storage), e);
            return null;
        }
    }

    /// Returns a safe account identifier string for diagnostics.
    private static String accountIdentifier(Map<Object, Object> storage) {
        AccountID identifier = Account.identifier(storage);
        if (identifier != null) {
            return identifier.toString();
        }

        Object type = storage.get("type");
        return type != null ? "{type=" + type + "}" : "<unknown>";
    }

    /// Called when it's ready to load accounts from [SettingsManager#settings()].
    public static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        ensureUniqueAccountIDs();

        // load accounts
        Account selected = null;
        for (Map<Object, Object> storage : getAccountStorages()) {
            Account account = parseAccount(storage);
            if (account != null) {
                account.setPortable(true);
                accounts.add(account);
                if (Boolean.TRUE.equals(storage.get("selected"))) {
                    selected = account;
                }
            }
        }

        for (Map<Object, Object> storage : getUserAccountStorages()) {
            Account account = parseAccount(storage);
            if (account != null) {
                accounts.add(account);
            }
        }

        AccountID selectedAccountReference = settings().selectedAccountProperty().get();
        if (selected == null && selectedAccountReference != null) {
            for (Account account : accounts) {
                if (matchesSelectedAccountReference(account, selectedAccountReference)) {
                    selected = account;
                    break;
                }
            }
        }

        if (selected == null && !accounts.isEmpty()) {
            selected = accounts.get(0);
        }

        if (!SettingsManager.isUserSettingsReadOnly()
                && !SettingsManager.userSettings().enableOfflineAccountProperty().get())
            for (Account account : accounts) {
                if (account instanceof MicrosoftAccount) {
                    UserSettings userSettings = userSettings();
                    userSettings.enableOfflineAccountProperty().set(true);
                    break;
                }
            }

        if (!SettingsManager.isUserSettingsReadOnly()
                && !SettingsManager.userSettings().enableOfflineAccountProperty().get())
            accounts.addListener(new ListChangeListener<Account>() {
                @Override
                public void onChanged(Change<? extends Account> change) {
                    while (change.next()) {
                        for (Account account : change.getAddedSubList()) {
                            if (account instanceof MicrosoftAccount) {
                                accounts.removeListener(this);
                                UserSettings userSettings = userSettings();
                                userSettings.enableOfflineAccountProperty().set(true);
                                return;
                            }
                        }
                    }
                }
            });

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
                settings().selectedAccountProperty().set(toSelectedAccountReference(account));
            else
                settings().selectedAccountProperty().set(null);
        }));
        accounts.addListener(listener);
        accounts.addListener(onInvalidating(Accounts::updateAccountStorages));

        initialized = true;

        getAuthlibInjectorServers().addListener(onInvalidating(Accounts::removeDanglingAuthlibInjectorAccounts));

        if (selected != null) {
            Account finalSelected = selected;
            Schedulers.io().execute(() -> {
                try {
                    finalSelected.logIn();
                } catch (Throwable e) {
                    LOG.warning("Failed to log " + finalSelected + " in", e);
                }
            });
        }

        for (AuthlibInjectorServer server : getAuthlibInjectorServers()) {
            if (selected instanceof AuthlibInjectorAccount && ((AuthlibInjectorAccount) selected).getServer() == server)
                continue;
            Schedulers.io().execute(() -> {
                try {
                    server.fetchMetadataResponse();
                } catch (IOException e) {
                    LOG.warning("Failed to fetch authlib-injector server metadata: " + server, e);
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
        if (authlibinjectorLocation != null) {
            LOG.info("Using specified authlib-injector: " + authlibinjectorLocation);
            return new SimpleAuthlibInjectorArtifactProvider(Paths.get(authlibinjectorLocation));
        }

        String authlibInjectorVersion = JarUtils.getAttribute("hmcl.authlib-injector.version", null);
        if (authlibInjectorVersion == null)
            throw new AssertionError("Missing hmcl.authlib-injector.version");

        String authlibInjectorFileName = "authlib-injector-" + authlibInjectorVersion + ".jar";
        return new AuthlibInjectorExtractor(Accounts.class.getResource("/assets/" + authlibInjectorFileName),
                Metadata.DEPENDENCIES_DIRECTORY.resolve("universal").resolve(authlibInjectorFileName));
    }

    private static AuthlibInjectorServer getOrCreateAuthlibInjectorServer(String url) {
        return getAuthlibInjectorServers().stream()
                .filter(server -> url.equals(server.getUrl()))
                .findFirst()
                .orElseGet(() -> {
                    AuthlibInjectorServer server = new AuthlibInjectorServer(url);
                    if (!SettingsManager.isAuthlibInjectorServersReadOnly()) {
                        getAuthlibInjectorServers().add(server);
                    }
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
                .filter(it -> !getAuthlibInjectorServers().contains(it.getServer()))
                .filter(Accounts::canRemoveAccount)
                .collect(toList())
                .forEach(accounts::remove);
    }
    // ====

    // ==== Login type name i18n ===
    private static final Map<AccountFactory<?>, String> unlocalizedLoginTypeNames = mapOf(
            pair(Accounts.FACTORY_OFFLINE, "account.methods.offline"),
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
            if (exception.getCause() instanceof SSLException) {
                if (exception.getCause().getMessage() != null && exception.getCause().getMessage().contains("Remote host terminated")) {
                    return i18n("account.failed.connect_authentication_server");
                }
                if (exception.getCause().getMessage() != null && (exception.getCause().getMessage().contains("No name matching") || exception.getCause().getMessage().contains("No subject alternative DNS name matching"))) {
                    return i18n("account.failed.dns");
                }
                return i18n("account.failed.ssl");
            } else {
                return i18n("account.failed.connect_authentication_server");
            }
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
            } else if (errorCode == MicrosoftService.XboxAuthorizationException.BANNED) {
                return i18n("account.methods.microsoft.error.banned");
            } else {
                return i18n("account.methods.microsoft.error.unknown", errorCode);
            }
        } else if (exception instanceof MicrosoftService.XBox400Exception) {
            return i18n("account.methods.microsoft.error.wrong_verify_method");
        } else if (exception instanceof MicrosoftService.MinecraftJavaEditionLicenseNotFoundException) {
            return i18n("account.methods.microsoft.error.no_license");
        } else if (exception instanceof MicrosoftService.MinecraftJavaEditionProfileNotFoundException) {
            return i18n("account.methods.microsoft.error.no_character");
        } else if (exception instanceof MicrosoftService.NoXuiException) {
            return i18n("account.methods.microsoft.error.add_family");
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
