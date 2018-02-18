/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.AuthlibInjectorAccount;
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.MojangDownloadProvider;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.ProfileLoadingEvent;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Settings {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(VersionSetting.class, VersionSetting.Serializer.INSTANCE)
            .registerTypeAdapter(Profile.class, Profile.Serializer.INSTANCE)
            .registerTypeAdapter(File.class, FileTypeAdapter.INSTANCE)
            .setPrettyPrinting().create();

    public static final String DEFAULT_PROFILE = "Default";
    public static final String HOME_PROFILE = "Home";

    public static final File SETTINGS_FILE = new File("hmcl.json").getAbsoluteFile();

    public static final Settings INSTANCE = new Settings();

    private Settings() {}

    private final Config SETTINGS = initSettings();

    private Map<String, Account> accounts = new HashMap<>();

    {
        for (Map<Object, Object> settings : SETTINGS.getAccounts()) {
            Optional<String> characterName = Accounts.getCurrentCharacter(settings);
            AccountFactory<?> factory = Accounts.ACCOUNT_FACTORY.get(Lang.get(settings, "type", String.class).orElse(""));
            if (factory == null || !characterName.isPresent()) {
                // unrecognized account type, so remove it.
                SETTINGS.getAccounts().remove(settings);
                continue;
            }

            Account account;
            try {
                account = factory.fromStorage(settings);
            } catch (Exception e) {
                SETTINGS.getAccounts().remove(settings);
                // storage is malformed, delete.
                continue;
            }

            accounts.put(Accounts.getAccountId(account), account);
        }

        checkAuthlibInjectorServerURLs();
        checkAuthlibInjectorAccounts();
        checkProfileMap();

        save();

        for (Map.Entry<String, Profile> entry2 : getProfileMap().entrySet()) {
            entry2.getValue().setName(entry2.getKey());
            entry2.getValue().nameProperty().setChangedListener(this::profileNameChanged);
            entry2.getValue().addPropertyChangedListener(e -> {
                save();
            });
        }

        Lang.ignoringException(() -> {
            Runtime.getRuntime().addShutdownHook(new Thread(this::save));
        });

        loadProxy();
    }

    private Config initSettings() {
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
            SETTINGS.getAccounts().clear();
            for (Account account : accounts.values()) {
                Map<Object, Object> storage = account.toStorage();
                storage.put("type", Accounts.getAccountType(account));
                SETTINGS.getAccounts().add(storage);
            }

            FileUtils.writeText(SETTINGS_FILE, GSON.toJson(SETTINGS));
        } catch (IOException ex) {
            Logging.LOG.log(Level.SEVERE, "Failed to save config", ex);
        }
    }

    private final StringProperty commonPath = new ImmediateStringProperty(this, "commonPath", SETTINGS.getCommonDirectory()) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.setCommonDirectory(get());
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

    private Locales.SupportedLocale locale = Locales.getLocaleByName(SETTINGS.getLocalization());

    public Locales.SupportedLocale getLocale() {
        return locale;
    }

    public void setLocale(Locales.SupportedLocale locale) {
        this.locale = locale;
        SETTINGS.setLocalization(Locales.getNameByLocale(locale));
    }

    private Proxy proxy = Proxy.NO_PROXY;

    public Proxy getProxy() {
        return proxy;
    }

    private Proxy.Type proxyType = Proxies.getProxyType(SETTINGS.getProxyType());

    public Proxy.Type getProxyType() {
        return proxyType;
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType = proxyType;
        SETTINGS.setProxyType(Proxies.PROXIES.indexOf(proxyType));
        loadProxy();
    }

    public String getProxyHost() {
        return SETTINGS.getProxyHost();
    }

    public void setProxyHost(String proxyHost) {
        SETTINGS.setProxyHost(proxyHost);
    }

    public String getProxyPort() {
        return SETTINGS.getProxyPort();
    }

    public void setProxyPort(String proxyPort) {
        SETTINGS.setProxyPort(proxyPort);
    }

    public String getProxyUser() {
        return SETTINGS.getProxyUser();
    }

    public void setProxyUser(String proxyUser) {
        SETTINGS.setProxyUser(proxyUser);
    }

    public String getProxyPass() {
        return SETTINGS.getProxyPass();
    }

    public void setProxyPass(String proxyPass) {
        SETTINGS.setProxyPass(proxyPass);
    }

    public boolean hasProxy() { return SETTINGS.isHasProxy(); }

    public void setHasProxy(boolean hasProxy) { SETTINGS.setHasProxy(hasProxy); }

    public boolean hasProxyAuth() { return SETTINGS.isHasProxyAuth(); }

    public void setHasProxyAuth(boolean hasProxyAuth) { SETTINGS.setHasProxyAuth(hasProxyAuth); }

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
        return Font.font(SETTINGS.getFontFamily(), SETTINGS.getFontSize());
    }

    public void setFont(Font font) {
        SETTINGS.setFontFamily(font.getFamily());
        SETTINGS.setFontSize(font.getSize());
    }

    public int getLogLines() {
        return Math.max(SETTINGS.getLogLines(), 100);
    }

    public void setLogLines(int logLines) {
        SETTINGS.setLogLines(logLines);
    }

    public Set<String> getAuthlibInjectorServerURLs() {
        return SETTINGS.getAuthlibInjectorServerURLs();
    }

    public void removeAuthlibInjectorServerURL(String serverURL) {
        checkAuthlibInjectorServerURLs();

        SETTINGS.getAuthlibInjectorServerURLs().remove(serverURL);

        checkAuthlibInjectorAccounts();
        save();
    }

    public void addAuthlibInjectorServerURL(String serverURL) {
        checkAuthlibInjectorServerURLs();

        SETTINGS.getAuthlibInjectorServerURLs().add(serverURL);
        save();
    }

    private void checkAuthlibInjectorServerURLs() {
        if (SETTINGS.getAuthlibInjectorServerURLs() == null)
            SETTINGS.setAuthlibInjectorServerURLs(new HashSet<>());
    }

    private void checkAuthlibInjectorAccounts() {
        for (Account account : getAccounts()) {
            if (account instanceof AuthlibInjectorAccount) {
                AuthlibInjectorAccount injectorAccount = (AuthlibInjectorAccount) account;
                if (!SETTINGS.getAuthlibInjectorServerURLs().contains(injectorAccount.getServerBaseURL()))
                    deleteAccount(account);
            }
        }
    }

    public DownloadProvider getDownloadProvider() {
        switch (SETTINGS.getDownloadType()) {
            case 0:
                return MojangDownloadProvider.INSTANCE;
            case 1:
                return BMCLAPIDownloadProvider.INSTANCE;
            default:
                return MojangDownloadProvider.INSTANCE;
        }
    }

    public void setDownloadProvider(DownloadProvider downloadProvider) {
        if (downloadProvider == MojangDownloadProvider.INSTANCE)
            SETTINGS.setDownloadType(0);
        else if (downloadProvider == BMCLAPIDownloadProvider.INSTANCE)
            SETTINGS.setDownloadType(1);
        else
            throw new IllegalArgumentException("Unknown download provider: " + downloadProvider);
    }

    /****************************************
     *               ACCOUNTS               *
     ****************************************/

    private final ImmediateObjectProperty<Account> selectedAccount = new ImmediateObjectProperty<Account>(this, "selectedAccount", accounts.get(SETTINGS.getSelectedAccount())) {
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

            SETTINGS.setSelectedAccount(getValue() == null ? "" : Accounts.getAccountId(getValue()));
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
    }

    public Account getAccount(String name, String character) {
        return accounts.get(Accounts.getAccountId(name, character));
    }

    public Collection<Account> getAccounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public void deleteAccount(String name, String character) {
        accounts.remove(Accounts.getAccountId(name, character));

        selectedAccount.get();
    }

    public void deleteAccount(Account account) {
        accounts.remove(Accounts.getAccountId(account));

        selectedAccount.get();
    }

    /****************************************
     *              BACKGROUND              *
     ****************************************/

    private final ImmediateStringProperty backgroundImage = new ImmediateStringProperty(this, "backgroundImage", SETTINGS.getBackgroundImage()) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.setBackgroundImage(get());
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

    private final ImmediateObjectProperty<EnumBackgroundImage> backgroundImageType = new ImmediateObjectProperty<EnumBackgroundImage>(this, "backgroundImageType", EnumBackgroundImage.indexOf(SETTINGS.getBackgroundImageType())) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.setBackgroundImageType(get().ordinal());
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

    private final ImmediateObjectProperty<Theme> theme = new ImmediateObjectProperty<Theme>(this, "theme", Theme.getTheme(SETTINGS.getTheme()).orElse(Theme.BLUE)) {
        @Override
        public void invalidated() {
            super.invalidated();

            SETTINGS.setTheme(get().name().toLowerCase());
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

    private Profile selectedProfile;

    public Profile getSelectedProfile() {
        checkProfileMap();

        if (!hasProfile(SETTINGS.getSelectedProfile())) {
            SETTINGS.setSelectedProfile(getProfileMap().keySet().stream().findFirst().get());
            Schedulers.computation().schedule(this::onProfileChanged);
        }
        return getProfile(SETTINGS.getSelectedProfile());
    }

    public void setSelectedProfile(Profile selectedProfile) {
        if (hasProfile(selectedProfile.getName()) && !Objects.equals(selectedProfile.getName(), SETTINGS.getSelectedProfile())) {
            SETTINGS.setSelectedProfile(selectedProfile.getName());
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
        return SETTINGS.getConfigurations();
    }

    public Collection<Profile> getProfiles() {
        return getProfileMap().values().stream().filter(t -> StringUtils.isNotBlank(t.getName())).collect(Collectors.toList());
    }

    public void putProfile(Profile ver) {
        if (StringUtils.isBlank(ver.getName()))
            throw new IllegalArgumentException("Profile's name is empty");

        getProfileMap().put(ver.getName(), ver);

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
            getProfileMap().put(DEFAULT_PROFILE, new Profile(DEFAULT_PROFILE));
            getProfileMap().put(HOME_PROFILE, new Profile(HOME_PROFILE, Main.MINECRAFT_DIRECTORY));
        }
    }

    private void onProfileChanged() {
        getSelectedProfile().getRepository().refreshVersionsAsync().start();
        EventBus.EVENT_BUS.fireEvent(new ProfileChangedEvent(SETTINGS, getSelectedProfile()));
    }

    private void profileNameChanged(ObservableValue<? extends String> observableValue, String oldValue, String newValue) {
        getProfileMap().put(newValue, getProfileMap().remove(oldValue));
    }

    /**
     * Start profiles loading process.
     * Invoked by loading GUI phase.
     */
    public void onProfileLoading() {
        EventBus.EVENT_BUS.fireEvent(new ProfileLoadingEvent(SETTINGS));
        onProfileChanged();
    }
}
