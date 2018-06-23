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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.scene.text.Font;

import org.hildan.fxgson.creators.ObservableListCreator;
import org.hildan.fxgson.creators.ObservableMapCreator;
import org.hildan.fxgson.creators.ObservableSetCreator;
import org.hildan.fxgson.factories.JavaFxPropertyTypeAdapterFactory;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.io.IOException;
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
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(VersionSetting.class, VersionSetting.Serializer.INSTANCE)
            .registerTypeAdapter(Profile.class, Profile.Serializer.INSTANCE)
            .registerTypeAdapter(File.class, FileTypeAdapter.INSTANCE)
            .registerTypeAdapter(ObservableList.class, new ObservableListCreator())
            .registerTypeAdapter(ObservableSet.class, new ObservableSetCreator())
            .registerTypeAdapter(ObservableMap.class, new ObservableMapCreator())
            .registerTypeAdapterFactory(new JavaFxPropertyTypeAdapterFactory(true, true))
            .setPrettyPrinting()
            .create();

    public static final String SETTINGS_FILE_NAME = "hmcl.json";
    public static final File SETTINGS_FILE = new File(SETTINGS_FILE_NAME).getAbsoluteFile();

    public static final Config SETTINGS = initSettings();

    public static final Settings INSTANCE = new Settings();

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    private final boolean firstLaunch;

    private Settings() {
        firstLaunch = SETTINGS.firstLaunch.get();

        loadProxy();

        for (Iterator<Map<Object, Object>> iterator = SETTINGS.accounts.iterator(); iterator.hasNext();) {
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
        }

        SETTINGS.authlibInjectorServers.addListener(onInvalidating(this::removeDanglingAuthlibInjectorAccounts));

        checkProfileMap();

        save();

        for (Map.Entry<String, Profile> entry2 : getProfileMap().entrySet()) {
            entry2.getValue().setName(entry2.getKey());
            entry2.getValue().nameProperty().setChangedListener(this::profileNameChanged);
            entry2.getValue().addPropertyChangedListener(e -> save());
        }

        Lang.ignoringException(() -> Runtime.getRuntime().addShutdownHook(new Thread(this::save)));
    }

    private static Config initSettings() {
        Config c = new Config();
        if (SETTINGS_FILE.exists())
            try {
                String str = FileUtils.readText(SETTINGS_FILE);
                if (StringUtils.isBlank(str))
                    Logging.LOG.finer("Settings file is empty, use the default settings.");
                else {
                    Config d = GSON.fromJson(str, Config.class);
                    if (d != null)
                        c = d;
                }
                Logging.LOG.finest("Initialized settings.");
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Something happened wrongly when load settings.", e);
            }
        return c;
    }

    public void save() {
        try {
            SETTINGS.accounts.clear();
            SETTINGS.firstLaunch.set(false);
            for (Account account : accounts.values()) {
                Map<Object, Object> storage = account.toStorage();
                storage.put("type", Accounts.getAccountType(account));
                SETTINGS.accounts.add(storage);
            }

            FileUtils.writeText(SETTINGS_FILE, GSON.toJson(SETTINGS));
        } catch (IOException ex) {
            Logging.LOG.log(Level.SEVERE, "Failed to save config", ex);
        }
    }

    public boolean isFirstLaunch() {
        return firstLaunch;
    }

    private final StringProperty commonPath = new ImmediateStringProperty(this, "commonPath", SETTINGS.commonDirectory.get()) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.commonDirectory.set(get());
            save();
        }
    };

    public String getCommonPath() {
        return commonPath.get();
    }

    public StringProperty commonPathProperty() {
        return commonPath;
    }

    public void setCommonPath(String commonPath) {
        this.commonPath.set(commonPath);
    }

    private Locales.SupportedLocale locale = Locales.getLocaleByName(SETTINGS.localization.get());

    public Locales.SupportedLocale getLocale() {
        return locale;
    }

    public void setLocale(Locales.SupportedLocale locale) {
        this.locale = locale;
        SETTINGS.localization.set(Locales.getNameByLocale(locale));
        save();
    }

    private Proxy proxy = Proxy.NO_PROXY;

    public Proxy getProxy() {
        return proxy;
    }

    private Proxy.Type proxyType = Proxies.getProxyType(SETTINGS.proxyType.get());

    public Proxy.Type getProxyType() {
        return proxyType;
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType = proxyType;
        SETTINGS.proxyType.set(Proxies.PROXIES.indexOf(proxyType));
        save();
        loadProxy();
    }

    public String getProxyHost() {
        return SETTINGS.proxyHost.get();
    }

    public void setProxyHost(String proxyHost) {
        SETTINGS.proxyHost.set(proxyHost);
        save();
    }

    public String getProxyPort() {
        return SETTINGS.proxyPort.get();
    }

    public void setProxyPort(String proxyPort) {
        SETTINGS.proxyPort.set(proxyPort);
        save();
    }

    public String getProxyUser() {
        return SETTINGS.proxyUser.get();
    }

    public void setProxyUser(String proxyUser) {
        SETTINGS.proxyUser.set(proxyUser);
        save();
    }

    public String getProxyPass() {
        return SETTINGS.proxyPass.get();
    }

    public void setProxyPass(String proxyPass) {
        SETTINGS.proxyPass.set(proxyPass);
        save();
    }

    public boolean hasProxy() {
        return SETTINGS.hasProxy.get();
    }

    public void setHasProxy(boolean hasProxy) {
        SETTINGS.hasProxy.set(hasProxy);
        save();
    }

    public boolean hasProxyAuth() {
        return SETTINGS.hasProxyAuth.get();
    }

    public void setHasProxyAuth(boolean hasProxyAuth) {
        SETTINGS.hasProxyAuth.set(hasProxyAuth);
        save();
    }

    private void loadProxy() {
        String host = getProxyHost();
        Integer port = Lang.toIntOrNull(getProxyPort());
        if (!hasProxy() || StringUtils.isBlank(host) || port == null || getProxyType() == Proxy.Type.DIRECT)
            proxy = Proxy.NO_PROXY;
        else {
            System.setProperty("http.proxyHost", getProxyHost());
            System.setProperty("http.proxyPort", getProxyPort());
            proxy = new Proxy(proxyType, new InetSocketAddress(host, port));

            String user = getProxyUser();
            String pass = getProxyPass();
            if (hasProxyAuth() && StringUtils.isNotBlank(user) && StringUtils.isNotBlank(pass)) {
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
        return Font.font(SETTINGS.fontFamily.get(), SETTINGS.fontSize.get());
    }

    public void setFont(Font font) {
        SETTINGS.fontFamily.set(font.getFamily());
        SETTINGS.fontSize.set(font.getSize());
        save();
    }

    public int getLogLines() {
        return Math.max(SETTINGS.logLines.get(), 100);
    }

    public void setLogLines(int logLines) {
        SETTINGS.logLines.set(logLines);
        save();
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
                .filter(it -> !SETTINGS.authlibInjectorServers.contains(((AuthlibInjectorAccount) it).getServer()))
                .collect(toList())
                .forEach(this::deleteAccount);
    }

    /****************************************
     *        DOWNLOAD PROVIDERS            *
     ****************************************/

    public DownloadProvider getDownloadProvider() {
        return DownloadProviders.getDownloadProvider(SETTINGS.downloadType.get());
    }

    public void setDownloadProvider(DownloadProvider downloadProvider) {
        int index = DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(downloadProvider);
        if (index == -1)
            throw new IllegalArgumentException("Unknown download provider: " + downloadProvider);
        SETTINGS.downloadType.set(index);
        save();
    }

    /****************************************
     *               ACCOUNTS               *
     ****************************************/

    private final ImmediateObjectProperty<Account> selectedAccount = new ImmediateObjectProperty<Account>(this, "selectedAccount", accounts.get(SETTINGS.selectedAccount.get())) {
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

            SETTINGS.selectedAccount.set(getValue() == null ? "" : Accounts.getAccountId(getValue()));
            save();
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
        accounts.remove(Accounts.getAccountId(name, character));

        onAccountLoading();
        selectedAccount.get();
    }

    public void deleteAccount(Account account) {
        accounts.remove(Accounts.getAccountId(account));

        onAccountLoading();
        selectedAccount.get();
    }

    /****************************************
     *              BACKGROUND              *
     ****************************************/

    private final ImmediateStringProperty backgroundImage = new ImmediateStringProperty(this, "backgroundImage", SETTINGS.backgroundImage.get()) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.backgroundImage.set(get());
            save();
        }
    };

    public String getBackgroundImage() {
        return backgroundImage.get();
    }

    public ImmediateStringProperty backgroundImageProperty() {
        return backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage.set(backgroundImage);
    }

    private final ImmediateObjectProperty<EnumBackgroundImage> backgroundImageType = new ImmediateObjectProperty<EnumBackgroundImage>(this, "backgroundImageType", EnumBackgroundImage.indexOf(SETTINGS.backgroundImageType.get())) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.backgroundImageType.set(get().ordinal());
            save();
        }
    };

    public EnumBackgroundImage getBackgroundImageType() {
        return backgroundImageType.get();
    }

    public ImmediateObjectProperty<EnumBackgroundImage> backgroundImageTypeProperty() {
        return backgroundImageType;
    }

    public void setBackgroundImageType(EnumBackgroundImage backgroundImageType) {
        this.backgroundImageType.set(backgroundImageType);
    }

    /****************************************
     *                THEME                 *
     ****************************************/

    private final ImmediateObjectProperty<Theme> theme = new ImmediateObjectProperty<Theme>(this, "theme", Theme.getTheme(SETTINGS.theme.get()).orElse(Theme.BLUE)) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.theme.set(get().getName().toLowerCase());
            save();
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

        if (!hasProfile(SETTINGS.selectedProfile.get())) {
            getProfileMap().keySet().stream().findFirst().ifPresent(selectedProfile -> {
                SETTINGS.selectedProfile.set(selectedProfile);
                save();
            });
            Schedulers.computation().schedule(this::onProfileChanged);
        }
        return getProfile(SETTINGS.selectedProfile.get());
    }

    public void setSelectedProfile(Profile selectedProfile) {
        if (hasProfile(selectedProfile.getName()) && !Objects.equals(selectedProfile.getName(), SETTINGS.selectedProfile.get())) {
            SETTINGS.selectedProfile.set(selectedProfile.getName());
            save();
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
        return SETTINGS.configurations;
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

        save();
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
            getProfileMap().put(Profiles.DEFAULT_PROFILE, new Profile(Profiles.DEFAULT_PROFILE));
            getProfileMap().put(Profiles.HOME_PROFILE, new Profile(Profiles.HOME_PROFILE, Launcher.MINECRAFT_DIRECTORY));
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
        return SETTINGS.clone();
    }
}
