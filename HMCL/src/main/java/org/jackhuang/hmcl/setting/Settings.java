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

import javafx.beans.value.ObservableValue;
import javafx.scene.text.Font;

import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.Locales;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.CONFIG;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class Settings {

    public static final Settings INSTANCE = new Settings();

    private final boolean firstLaunch;

    private Settings() {
        firstLaunch = CONFIG.isFirstLaunch();

        ProxyManager.init();
        Accounts.init();

        checkProfileMap();

        for (Map.Entry<String, Profile> profileEntry : getProfileMap().entrySet()) {
            profileEntry.getValue().setName(profileEntry.getKey());
            profileEntry.getValue().nameProperty().setChangedListener(this::profileNameChanged);
            profileEntry.getValue().addPropertyChangedListener(e -> save());
        }

        CONFIG.addListener(source -> save());
        CONFIG.setFirstLaunch(false);
    }

    private void save() {
        LOG.info("Saving config");
        ConfigHolder.saveConfig(CONFIG);
    }

    public boolean isFirstLaunch() {
        return firstLaunch;
    }

    private Locales.SupportedLocale locale = Locales.getLocaleByName(CONFIG.getLocalization());

    public Locales.SupportedLocale getLocale() {
        return locale;
    }

    public void setLocale(Locales.SupportedLocale locale) {
        this.locale = locale;
        CONFIG.setLocalization(Locales.getNameByLocale(locale));
    }

    public Font getFont() {
        return Font.font(CONFIG.getFontFamily(), CONFIG.getFontSize());
    }

    public void setFont(Font font) {
        CONFIG.setFontFamily(font.getFamily());
        CONFIG.setFontSize(font.getSize());
    }

    public int getLogLines() {
        return Math.max(CONFIG.getLogLines(), 100);
    }

    public void setLogLines(int logLines) {
        CONFIG.setLogLines(logLines);
    }

    public boolean isCommonDirectoryDisabled() {
        return CONFIG.getCommonDirType() == EnumCommonDirectory.DISABLED;
    }

    public static String getDefaultCommonDirectory() {
        return Launcher.MINECRAFT_DIRECTORY.getAbsolutePath();
    }

    public String getCommonDirectory() {
        switch (CONFIG.getCommonDirType()) {
            case DISABLED:
                return null;
            case DEFAULT:
                return getDefaultCommonDirectory();
            case CUSTOM:
                return CONFIG.getCommonDirectory();
            default:
                return null;
        }
    }

    /****************************************
     *        DOWNLOAD PROVIDERS            *
     ****************************************/

    public DownloadProvider getDownloadProvider() {
        return DownloadProviders.getDownloadProvider(CONFIG.getDownloadType());
    }

    public void setDownloadProvider(DownloadProvider downloadProvider) {
        int index = DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(downloadProvider);
        if (index == -1)
            throw new IllegalArgumentException("Unknown download provider: " + downloadProvider);
        CONFIG.setDownloadType(index);
    }

    /****************************************
     *               PROFILES               *
     ****************************************/

    public Profile getSelectedProfile() {
        checkProfileMap();

        if (!hasProfile(CONFIG.getSelectedProfile())) {
            getProfileMap().keySet().stream().findFirst().ifPresent(selectedProfile -> {
                CONFIG.setSelectedProfile(selectedProfile);
            });
            Schedulers.computation().schedule(this::onProfileChanged);
        }
        return getProfile(CONFIG.getSelectedProfile());
    }

    public void setSelectedProfile(Profile selectedProfile) {
        if (hasProfile(selectedProfile.getName()) && !Objects.equals(selectedProfile.getName(), CONFIG.getSelectedProfile())) {
            CONFIG.setSelectedProfile(selectedProfile.getName());
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
        return CONFIG.getConfigurations();
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
}
