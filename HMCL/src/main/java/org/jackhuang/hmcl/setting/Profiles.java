/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static javafx.collections.FXCollections.observableArrayList;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
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

    private static final ObservableList<Profile> profiles = observableArrayList(profile -> new Observable[] { profile });
    private static final ReadOnlyListWrapper<Profile> profilesWrapper = new ReadOnlyListWrapper<>(profiles);

    private static ObjectProperty<Profile> selectedProfile = new SimpleObjectProperty<Profile>() {
        {
            profiles.addListener(onInvalidating(this::invalidated));
        }

        @Override
        protected void invalidated() {
            if (!initialized)
                return;

            Profile profile = get();

            if (profiles.isEmpty()) {
                if (profile != null) {
                    set(null);
                    return;
                }
            } else {
                if (!profiles.contains(profile)) {
                    set(profiles.get(0));
                    return;
                }
            }

            config().setSelectedProfile(profile == null ? "" : profile.getName());
            if (profile != null) {
                if (profile.getRepository().isLoaded())
                    selectedVersion.bind(profile.selectedVersionProperty());
                else {
                    selectedVersion.unbind();
                    selectedVersion.set(null);
                    // bind when repository was reloaded.
                    profile.getRepository().refreshVersionsAsync().start();
                }
            } else {
                selectedVersion.unbind();
                selectedVersion.set(null);
            }
        }
    };

    private static void checkProfiles() {
        if (profiles.isEmpty()) {
            Profile current = new Profile(Profiles.DEFAULT_PROFILE, new File(".minecraft"), new VersionSetting(), null, true);
            Profile home = new Profile(Profiles.HOME_PROFILE, Metadata.MINECRAFT_DIRECTORY.toFile());
            Platform.runLater(() -> profiles.addAll(current, home));
        }
    }

    /**
     * True if {@link #init()} hasn't been called.
     */
    private static boolean initialized = false;

    static {
        profiles.addListener(onInvalidating(Profiles::updateProfileStorages));
        profiles.addListener(onInvalidating(Profiles::checkProfiles));

        selectedProfile.addListener((a, b, newValue) -> {
            if (newValue != null)
                newValue.getRepository().refreshVersionsAsync().start();
        });
    }

    private static void updateProfileStorages() {
        // don't update the underlying storage before data loading is completed
        // otherwise it might cause data loss
        if (!initialized)
            return;
        // update storage
        config().getConfigurations().clear();
        config().getConfigurations().putAll(profiles.stream().collect(Collectors.toMap(Profile::getName, it -> it)));
    }

    /**
     * Called when it's ready to load profiles from {@link ConfigHolder#config()}.
     */
    static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        HashSet<String> names = new HashSet<>();
        config().getConfigurations().forEach((name, profile) -> {
            if (!names.add(name)) return;
            profiles.add(profile);
            profile.setName(name);
        });
        checkProfiles();

        // Platform.runLater is necessary or profiles will be empty
        // since checkProfiles adds 2 base profile later.
        Platform.runLater(() -> {
            initialized = true;

            selectedProfile.set(
                    profiles.stream()
                            .filter(it -> it.getName().equals(config().getSelectedProfile()))
                            .findFirst()
                            .orElse(profiles.get(0)));
        });

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> {
            runInFX(() -> {
                Profile profile = selectedProfile.get();
                if (profile != null && profile.getRepository() == event.getSource()) {
                    selectedVersion.bind(profile.selectedVersionProperty());
                    for (Consumer<Profile> listener : versionsListeners)
                        listener.accept(profile);
                }
            });
        });
    }

    public static ObservableList<Profile> getProfiles() {
        return profiles;
    }

    public static ReadOnlyListProperty<Profile> profilesProperty() {
        return profilesWrapper.getReadOnlyProperty();
    }

    public static Profile getSelectedProfile() {
        return selectedProfile.get();
    }

    public static void setSelectedProfile(Profile profile) {
        selectedProfile.set(profile);
    }

    public static ObjectProperty<Profile> selectedProfileProperty() {
        return selectedProfile;
    }

    private static final ReadOnlyStringWrapper selectedVersion = new ReadOnlyStringWrapper();

    public static ReadOnlyStringProperty selectedVersionProperty() {
        return selectedVersion.getReadOnlyProperty();
    }

    // Guaranteed that the repository is loaded.
    public static String getSelectedVersion() {
        return selectedVersion.get();
    }

    private static final List<Consumer<Profile>> versionsListeners = new LinkedList<>();

    public static void registerVersionsListener(Consumer<Profile> listener) {
        Profile profile = getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            listener.accept(profile);
        versionsListeners.add(listener);
    }
}
