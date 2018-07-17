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

import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.text.Font;

import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.Locales;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class Settings {

    public static final Settings INSTANCE = new Settings();

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    private final boolean firstLaunch;

    private InvalidationListener accountChangeListener =
            source -> ConfigHolder.CONFIG.accounts.setAll(
                    accounts.values().stream()
                            .map(account -> {
                                Map<Object, Object> storage = account.toStorage();
                                storage.put("type", Accounts.getAccountType(account));
                                return storage;
                            })
                            .collect(toList()));

    private Settings() {
        firstLaunch = ConfigHolder.CONFIG.firstLaunch.get();
        ConfigHolder.CONFIG.firstLaunch.set(false);

        loadProxy();

        for (Iterator<Map<Object, Object>> iterator = ConfigHolder.CONFIG.accounts.iterator(); iterator.hasNext();) {
            Map<Object, Object> settings = iterator.next();
            AccountFactory<?> factory = Accounts.ACCOUNT_FACTORY.get(tryCast(settings.get("type"), String.class).orElse(""));
            if (factory == null) {
                LOG.warning("Unrecognized account type, removing: " + settings);
                iterator.remove();
                continue;
            }

            Account account;
            try {
                account = factory.fromStorage(settings, getProxy());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Malformed account storage, removing: " + settings, e);
                iterator.remove();
                continue;
            }

            accounts.put(Accounts.getAccountId(account), account);
            account.addListener(accountChangeListener);
        }

        ConfigHolder.CONFIG.authlibInjectorServers.addListener(onInvalidating(this::removeDanglingAuthlibInjectorAccounts));

        this.selectedAccount.set(accounts.get(ConfigHolder.CONFIG.selectedAccount.get()));

        checkProfileMap();

        save();

        for (Map.Entry<String, Profile> profileEntry : getProfileMap().entrySet()) {
            profileEntry.getValue().setName(profileEntry.getKey());
            profileEntry.getValue().nameProperty().setChangedListener(this::profileNameChanged);
            profileEntry.getValue().addPropertyChangedListener(e -> save());
        }

        Lang.ignoringException(() -> Runtime.getRuntime().addShutdownHook(new Thread(this::save)));

        ConfigHolder.CONFIG.addListener(source -> save());
    }

    private void save() {
        ConfigHolder.saveConfig(ConfigHolder.CONFIG);
    }

    public boolean isFirstLaunch() {
        return firstLaunch;
    }

    private Locales.SupportedLocale locale = Locales.getLocaleByName(ConfigHolder.CONFIG.localization.get());

    public Locales.SupportedLocale getLocale() {
        return locale;
    }

    public void setLocale(Locales.SupportedLocale locale) {
        this.locale = locale;
        ConfigHolder.CONFIG.localization.set(Locales.getNameByLocale(locale));
    }

    private Proxy proxy = Proxy.NO_PROXY;

    public Proxy getProxy() {
        return proxy;
    }

    private Proxy.Type proxyType = Proxies.getProxyType(ConfigHolder.CONFIG.proxyType.get());

    public Proxy.Type getProxyType() {
        return proxyType;
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType = proxyType;
        ConfigHolder.CONFIG.proxyType.set(Proxies.PROXIES.indexOf(proxyType));
        loadProxy();
    }

    private void loadProxy() {
        String host = ConfigHolder.CONFIG.proxyHost.get();
        Integer port = Lang.toIntOrNull(ConfigHolder.CONFIG.proxyPort.get());
        if (!ConfigHolder.CONFIG.hasProxy.get() || StringUtils.isBlank(host) || port == null || getProxyType() == Proxy.Type.DIRECT)
            proxy = Proxy.NO_PROXY;
        else {
            System.setProperty("http.proxyHost", ConfigHolder.CONFIG.proxyHost.get());
            System.setProperty("http.proxyPort", ConfigHolder.CONFIG.proxyPort.get());
            proxy = new Proxy(proxyType, new InetSocketAddress(host, port));

            String user = ConfigHolder.CONFIG.proxyUser.get();
            String pass = ConfigHolder.CONFIG.proxyPass.get();
            if (ConfigHolder.CONFIG.hasProxyAuth.get() && StringUtils.isNotBlank(user) && StringUtils.isNotBlank(pass)) {
                System.setProperty("http.proxyUser", user);
                System.setProperty("http.proxyPassword", pass);

                Authenticator.setDefault(new Authenticator() {
                    @Override
                    public PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass.toCharArray());
                    }
                });
            }
        }
    }

    public Font getFont() {
        return Font.font(ConfigHolder.CONFIG.fontFamily.get(), ConfigHolder.CONFIG.fontSize.get());
    }

    public void setFont(Font font) {
        ConfigHolder.CONFIG.fontFamily.set(font.getFamily());
        ConfigHolder.CONFIG.fontSize.set(font.getSize());
    }

    public int getLogLines() {
        return Math.max(ConfigHolder.CONFIG.logLines.get(), 100);
    }

    public void setLogLines(int logLines) {
        ConfigHolder.CONFIG.logLines.set(logLines);
    }

    /****************************************
     *           AUTHLIB INJECTORS          *
     ****************************************/

    /**
     * After an {@link AuthlibInjectorServer} is removed, the associated accounts should also be removed.
     * This method performs a check and removes the dangling accounts.
     * Don't call this before {@link #migrateAuthlibInjectorServers()} is called, otherwise old data would be lost.
     */
    private void removeDanglingAuthlibInjectorAccounts() {
        accounts.values().stream()
                .filter(AuthlibInjectorAccount.class::isInstance)
                .filter(it -> !ConfigHolder.CONFIG.authlibInjectorServers.contains(((AuthlibInjectorAccount) it).getServer()))
                .collect(toList())
                .forEach(this::deleteAccount);
    }

    /****************************************
     *        DOWNLOAD PROVIDERS            *
     ****************************************/

    public DownloadProvider getDownloadProvider() {
        return DownloadProviders.getDownloadProvider(ConfigHolder.CONFIG.downloadType.get());
    }

    public void setDownloadProvider(DownloadProvider downloadProvider) {
        int index = DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(downloadProvider);
        if (index == -1)
            throw new IllegalArgumentException("Unknown download provider: " + downloadProvider);
        ConfigHolder.CONFIG.downloadType.set(index);
    }

    /****************************************
     *               ACCOUNTS               *
     ****************************************/

    private final ImmediateObjectProperty<Account> selectedAccount = new ImmediateObjectProperty<Account>(this, "selectedAccount", null) {
        @Override
        public Account get() {
            Account a = super.get();
            if (a == null || !accounts.containsKey(Accounts.getAccountId(a))) {
                Account acc = accounts.values().stream().findAny().orElse(null);
                set(acc);
                return acc;
            } else return a;
        }

        @Override
        public void set(Account newValue) {
            if (newValue == null || accounts.containsKey(Accounts.getAccountId(newValue))) {
                super.set(newValue);
            }
        }

        @Override
        public void invalidated() {
            super.invalidated();

            ConfigHolder.CONFIG.selectedAccount.set(getValue() == null ? "" : Accounts.getAccountId(getValue()));
        }
    };

    public Account getSelectedAccount() {
        return selectedAccount.get();
    }

    public ObjectProperty<Account> selectedAccountProperty() {
        return selectedAccount;
    }

    public void setSelectedAccount(Account selectedAccount) {
        this.selectedAccount.set(selectedAccount);
    }

    public void addAccount(Account account) {
        accounts.put(Accounts.getAccountId(account), account);
        account.addListener(accountChangeListener);
        accountChangeListener.invalidated(account);

        onAccountLoading();

        EventBus.EVENT_BUS.fireEvent(new AccountAddedEvent(this, account));
    }

    public Account getAccount(String name, String character) {
        return accounts.get(Accounts.getAccountId(name, character));
    }

    public Collection<Account> getAccounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public void deleteAccount(String name, String character) {
        Account removed = accounts.remove(Accounts.getAccountId(name, character));
        if (removed != null) {
            removed.removeListener(accountChangeListener);
            accountChangeListener.invalidated(removed);

            onAccountLoading();
            selectedAccount.get();
        }
    }

    public void deleteAccount(Account account) {
        accounts.remove(Accounts.getAccountId(account));
        account.removeListener(accountChangeListener);
        accountChangeListener.invalidated(account);

        onAccountLoading();
        selectedAccount.get();
    }

    /****************************************
     *                THEME                 *
     ****************************************/

    private final ImmediateObjectProperty<Theme> theme = new ImmediateObjectProperty<Theme>(this, "theme", Theme.getTheme(ConfigHolder.CONFIG.theme.get()).orElse(Theme.BLUE)) {
        @Override
        public void invalidated() {
            super.invalidated();

            ConfigHolder.CONFIG.theme.set(get().getName().toLowerCase());
        }
    };

    public Theme getTheme() {
        return theme.get();
    }

    public void setTheme(Theme theme) {
        this.theme.set(theme);
    }

    public ImmediateObjectProperty<Theme> themeProperty() {
        return theme;
    }

    /****************************************
     *               PROFILES               *
     ****************************************/

    public Profile getSelectedProfile() {
        checkProfileMap();

        if (!hasProfile(ConfigHolder.CONFIG.selectedProfile.get())) {
            getProfileMap().keySet().stream().findFirst().ifPresent(selectedProfile -> {
                ConfigHolder.CONFIG.selectedProfile.set(selectedProfile);
            });
            Schedulers.computation().schedule(this::onProfileChanged);
        }
        return getProfile(ConfigHolder.CONFIG.selectedProfile.get());
    }

    public void setSelectedProfile(Profile selectedProfile) {
        if (hasProfile(selectedProfile.getName()) && !Objects.equals(selectedProfile.getName(), ConfigHolder.CONFIG.selectedProfile.get())) {
            ConfigHolder.CONFIG.selectedProfile.set(selectedProfile.getName());
            Schedulers.computation().schedule(this::onProfileChanged);
        }
    }

    public Profile getProfile(String name) {
        checkProfileMap();

        Optional<Profile> p = name == null ? getProfileMap().values().stream().findFirst() : Optional.ofNullable(getProfileMap().get(name));
        return p.orElse(null);
    }

    public boolean hasProfile(String name) {
        return getProfileMap().containsKey(name);
    }

    public Map<String, Profile> getProfileMap() {
        return ConfigHolder.CONFIG.configurations;
    }

    public Collection<Profile> getProfiles() {
        return getProfileMap().values().stream().filter(t -> StringUtils.isNotBlank(t.getName())).collect(Collectors.toList());
    }

    public void putProfile(Profile ver) {
        if (StringUtils.isBlank(ver.getName()))
            throw new IllegalArgumentException("Profile's name is empty");

        getProfileMap().put(ver.getName(), ver);
        Schedulers.computation().schedule(this::onProfileLoading);

        ver.nameProperty().setChangedListener(this::profileNameChanged);
    }

    public void deleteProfile(Profile profile) {
        deleteProfile(profile.getName());
    }

    public void deleteProfile(String profileName) {
        getProfileMap().remove(profileName);
        checkProfileMap();
        Schedulers.computation().schedule(this::onProfileLoading);
    }

    private void checkProfileMap() {
        if (getProfileMap().isEmpty()) {
            Profile current = new Profile(Profiles.DEFAULT_PROFILE);
            current.setUseRelativePath(true);
            getProfileMap().put(Profiles.DEFAULT_PROFILE, current);

            Profile home = new Profile(Profiles.HOME_PROFILE, Launcher.MINECRAFT_DIRECTORY);
            getProfileMap().put(Profiles.HOME_PROFILE, home);
        }
    }

    private void onProfileChanged() {
        EventBus.EVENT_BUS.fireEvent(new ProfileChangedEvent(this, getSelectedProfile()));
        getSelectedProfile().getRepository().refreshVersionsAsync().start();
    }

    private void profileNameChanged(ObservableValue<? extends String> observableValue, String oldValue, String newValue) {
        getProfileMap().put(newValue, getProfileMap().remove(oldValue));
    }

    /**
     * Start profiles loading process.
     * Invoked by loading GUI phase.
     */
    public void onProfileLoading() {
        EventBus.EVENT_BUS.fireEvent(new ProfileLoadingEvent(this, getProfiles()));
        onProfileChanged();
    }

    public void onAccountLoading() {
        EventBus.EVENT_BUS.fireEvent(new AccountLoadingEvent(this, getAccounts()));
    }

    public Config getRawConfig() {
        return ConfigHolder.CONFIG.clone();
    }
}
