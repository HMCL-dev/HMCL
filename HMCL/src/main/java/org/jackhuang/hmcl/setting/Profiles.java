/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.github.f4b6a3.uuid.alt.GUID;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.PortablePath;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Profiles {

    public static final String DEFAULT_PROFILE = "Default";
    public static final String HOME_PROFILE = "Home";

    /// The stable ID used by the built-in default profile.
    public static final GUID DEFAULT_PROFILE_ID = LegacyConfigMigrator.getLegacyProfileId(DEFAULT_PROFILE);

    /// The stable ID used by the built-in home profile.
    public static final GUID HOME_PROFILE_ID = LegacyConfigMigrator.getLegacyProfileId(HOME_PROFILE);

    private Profiles() {
    }

    /// Creates a profile ID that does not collide with existing profiles.
    public static GUID newProfileId() {
        GUID id;
        do {
            id = GUID.v7();
        } while (hasProfileId(id));
        return id;
    }

    /// Returns whether an existing profile uses the given ID.
    private static boolean hasProfileId(GUID id) {
        for (Profile profile : SettingsManager.getGameDirectories()) {
            if (id.equals(profile.getId())) {
                return true;
            }
        }
        return false;
    }

    public static String getProfileDisplayName(Profile profile) {
        String name = profile.getName();
        if (name != null) {
            return name;
        }

        GUID id = profile.getId();
        if (DEFAULT_PROFILE_ID.equals(id)) {
            return i18n("profile.default");
        }
        if (HOME_PROFILE_ID.equals(id)) {
            return i18n("profile.home");
        }

        return id.toString();
    }

    private static final ReadOnlyListWrapper<Profile> profilesWrapper =
            new ReadOnlyListWrapper<>(FXCollections.emptyObservableList());

    private static final ObjectProperty<Profile> selectedProfile = new SimpleObjectProperty<>() {
        @Override
        protected void invalidated() {
            refreshSelectedProfile();
        }
    };

    private static void refreshSelectedProfile() {
        if (!initialized)
            return;

        ObservableList<Profile> profiles = SettingsManager.getGameDirectories();
        Profile profile = selectedProfile.get();

        if (profiles.isEmpty()) {
            if (profile != null) {
                selectedProfile.set(null);
                return;
            }
        } else {
            if (!profiles.contains(profile)) {
                selectedProfile.set(profiles.get(0));
                return;
            }
        }

        SettingsManager.setSelectedGameDirectory(profile == null ? null : profile.getId());
        if (profile != null) {
            if (profile.getRepository().isLoaded()) {
                refreshSelectedVersion(profile);
            } else {
                selectedVersion.set(null);
                // bind when repository was reloaded.
                profile.getRepository().refreshVersionsAsync().start();
            }
        } else {
            selectedVersion.set(null);
        }
    }

    private static void refreshSelectedVersion(Profile profile) {
        String version = SettingsManager.getSelectedInstance(profile.getId());
        if (!profile.getRepository().hasVersion(version)) {
            Optional<String> fallback = profile.getRepository().getVersions().stream()
                    .findFirst()
                    .map(Version::getId);
            version = fallback.orElse(null);
            if (!Objects.equals(SettingsManager.getSelectedInstance(profile.getId()), version)) {
                SettingsManager.setSelectedInstance(profile.getId(), version);
            }
        }
        selectedVersion.set(version);
    }

    private static void checkProfiles() {
        ObservableList<Profile> profiles = SettingsManager.getGameDirectories();
        if (profiles.isEmpty()) {
            Profile current = new Profile(
                    Profiles.DEFAULT_PROFILE_ID, null, PortablePath.of(".minecraft"));
            Profile home = new Profile(
                    Profiles.HOME_PROFILE_ID, null, PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY));
            Platform.runLater(() -> profiles.addAll(current, home));
        }
    }

    /**
     * True if {@link #init()} hasn't been called.
     */
    private static boolean initialized = false;

    static {
        selectedProfile.addListener((a, b, newValue) -> {
            if (newValue != null)
                newValue.getRepository().refreshVersionsAsync().start();
        });
    }

    /// Called when it's ready to load profiles from [SettingsManager].
    public static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        profilesWrapper.set(SettingsManager.getGameDirectories());
        removeDuplicateProfiles(SettingsManager.getGameDirectories());
        SettingsManager.getGameDirectories().addListener(onInvalidating(Profiles::refreshSelectedProfile));
        SettingsManager.getGameDirectories().addListener(onInvalidating(Profiles::checkProfiles));
        SettingsManager.getSelectedInstance().addListener(onInvalidating(() -> {
            Profile profile = selectedProfile.get();
            if (profile != null && profile.getRepository().isLoaded()) {
                refreshSelectedVersion(profile);
            }
        }));
        checkProfiles();
        migrateGameSettings();

        // Platform.runLater is necessary or profiles will be empty
        // since checkProfiles adds 2 base profile later.
        Platform.runLater(() -> {
            initialized = true;

            @Nullable GUID selectedId = SettingsManager.getSelectedGameDirectory();
            selectedProfile.set(
                    SettingsManager.getGameDirectories().stream()
                            .filter(it -> it.getId().equals(selectedId))
                            .findFirst()
                            .orElse(SettingsManager.getGameDirectories().isEmpty() ? null : SettingsManager.getGameDirectories().get(0)));
        });

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> {
            runInFX(() -> {
                Profile profile = selectedProfile.get();
                if (profile != null && profile.getRepository() == event.getSource()) {
                    refreshSelectedVersion(profile);
                    for (Consumer<Profile> listener : versionsListeners)
                        listener.accept(profile);
                }
            });
        });
    }

    private static void removeDuplicateProfiles(ObservableList<Profile> profiles) {
        HashSet<GUID> ids = new HashSet<>();
        HashSet<String> names = new HashSet<>();
        profiles.removeIf(profile -> {
            String name = profile.getName();
            return !ids.add(profile.getId()) || (name != null && !names.add(name));
        });
    }

    private static void migrateGameSettings() {
        if (SettingsManager.getGameSettings().isEmpty()) {
            SettingsManager.getDefaultGameSettingsPresetOrCreate();
        } else if (SettingsManager.getGameSettings(SettingsManager.getDefaultGameSettingsPreset()) == null) {
            SettingsManager.setDefaultGameSettingsPreset(SettingsManager.getGameSettings().get(0).idProperty().getValue());
        }
    }

    public static ObservableList<Profile> getProfiles() {
        return SettingsManager.getGameDirectories();
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
    public static @Nullable String getSelectedInstance() {
        return selectedVersion.get();
    }

    /// Returns the selected instance ID for the given profile.
    public static @Nullable String getSelectedInstance(Profile profile) {
        return SettingsManager.getSelectedInstance(profile.getId());
    }

    /// Sets the selected instance ID for the currently selected profile.
    public static void setSelectedInstance(@Nullable String instance) {
        Profile profile = selectedProfile.get();
        if (profile != null) {
            setSelectedInstance(profile, instance);
        }
    }

    /// Sets the selected instance ID for the given profile.
    public static void setSelectedInstance(Profile profile, @Nullable String instance) {
        SettingsManager.setSelectedInstance(profile.getId(), instance);
        if (profile == selectedProfile.get()) {
            selectedVersion.set(SettingsManager.getSelectedInstance(profile.getId()));
        }
    }

    private static final List<Consumer<Profile>> versionsListeners = new ArrayList<>(4);

    public static void registerVersionsListener(Consumer<Profile> listener) {
        Profile profile = getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            listener.accept(profile);
        versionsListeners.add(listener);
    }
}
