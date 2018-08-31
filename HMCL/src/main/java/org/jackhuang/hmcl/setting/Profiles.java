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
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.ProfileLoadingEvent;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Profiles {

    public static final String DEFAULT_PROFILE = "Default";
    public static final String HOME_PROFILE = "Home";

    private Profiles() {
    }

    public static String getProfileDisplayName(Profile profile) {
        switch (profile.getName()) {
            case Profiles.DEFAULT_PROFILE:
                return i18n("profile.default");
            case Profiles.HOME_PROFILE:
                return i18n("profile.home");
            default:
                return profile.getName();
        }
    }

    /****************************************
     *               PROFILES               *
     ****************************************/

    public static Profile getSelectedProfile() {
        checkProfileMap();

        if (!hasProfile(config().getSelectedProfile())) {
            getProfileMap().keySet().stream().findFirst().ifPresent(selectedProfile -> {
                config().setSelectedProfile(selectedProfile);
            });
            Schedulers.computation().schedule(Profiles::onProfileChanged);
        }
        return getProfile(config().getSelectedProfile());
    }

    public static void setSelectedProfile(Profile selectedProfile) {
        if (hasProfile(selectedProfile.getName()) && !Objects.equals(selectedProfile.getName(), config().getSelectedProfile())) {
            config().setSelectedProfile(selectedProfile.getName());
            Schedulers.computation().schedule(Profiles::onProfileChanged);
        }
    }

    public static Profile getProfile(String name) {
        checkProfileMap();

        Optional<Profile> p = name == null ? getProfileMap().values().stream().findFirst() : Optional.ofNullable(getProfileMap().get(name));
        return p.orElse(null);
    }

    public static boolean hasProfile(String name) {
        return getProfileMap().containsKey(name);
    }

    public static Map<String, Profile> getProfileMap() {
        return config().getConfigurations();
    }

    public static Collection<Profile> getProfiles() {
        return getProfileMap().values().stream().filter(t -> StringUtils.isNotBlank(t.getName())).collect(Collectors.toList());
    }

    public static void putProfile(Profile ver) {
        if (StringUtils.isBlank(ver.getName()))
            throw new IllegalArgumentException("Profile's name is empty");

        getProfileMap().put(ver.getName(), ver);
        Schedulers.computation().schedule(Profiles::onProfileLoading);

        ver.nameProperty().setChangedListener(Profiles::profileNameChanged);
    }

    public static void deleteProfile(Profile profile) {
        deleteProfile(profile.getName());
    }

    public static void deleteProfile(String profileName) {
        getProfileMap().remove(profileName);
        checkProfileMap();
        Schedulers.computation().schedule(Profiles::onProfileLoading);
    }

    private static void checkProfileMap() {
        if (getProfileMap().isEmpty()) {
            Profile current = new Profile(Profiles.DEFAULT_PROFILE);
            current.setUseRelativePath(true);
            getProfileMap().put(Profiles.DEFAULT_PROFILE, current);

            Profile home = new Profile(Profiles.HOME_PROFILE, Launcher.MINECRAFT_DIRECTORY);
            getProfileMap().put(Profiles.HOME_PROFILE, home);
        }
    }

    private static void onProfileChanged() {
        EventBus.EVENT_BUS.fireEvent(new ProfileChangedEvent(new Object(), getSelectedProfile()));
        getSelectedProfile().getRepository().refreshVersionsAsync().start();
    }

    private static void profileNameChanged(ObservableValue<? extends String> observableValue, String oldValue, String newValue) {
        getProfileMap().put(newValue, getProfileMap().remove(oldValue));
    }

    /**
     * Start profiles loading process.
     * Invoked by loading GUI phase.
     */
    public static void onProfileLoading() {
        EventBus.EVENT_BUS.fireEvent(new ProfileLoadingEvent(new Object(), getProfiles()));
        onProfileChanged();
    }

    static void init() {
        checkProfileMap();

        for (Map.Entry<String, Profile> profileEntry : getProfileMap().entrySet()) {
            profileEntry.getValue().setName(profileEntry.getKey());
            profileEntry.getValue().nameProperty().setChangedListener(Profiles::profileNameChanged);
            profileEntry.getValue().addPropertyChangedListener(e -> ConfigHolder.markConfigDirty());
        }
    }
}
