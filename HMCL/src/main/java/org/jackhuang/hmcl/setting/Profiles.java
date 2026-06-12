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

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public final class Profiles {

    /// The default current-workspace game directory path.
    private static final PortablePath CURRENT_PROFILE_PATH = PortablePath.of(".minecraft");

    /// The default user-home game directory path.
    private static final PortablePath HOME_PROFILE_PATH = PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY);

    private Profiles() {
    }

    /// Creates a profile ID that does not collide with existing profiles.
    public static SettingID newProfileId() {
        SettingID id;
        do {
            id = SettingID.generate();
        } while (hasProfileId(id));
        return id;
    }

    /// Returns whether an existing profile uses the given ID.
    private static boolean hasProfileId(SettingID id) {
        return SettingsManager.localGameDirectories().getGameDirectories().stream()
                .anyMatch(profile -> profile.getId().equals(id))
                || SettingsManager.userGameDirectories().getGameDirectories().stream()
                .anyMatch(profile -> profile.getId().equals(id));
    }

    public static String getProfileDisplayName(Profile profile) {
        String name = getProfileCustomName(profile);
        if (name != null) {
            return name;
        }

        if (isProfilePath(profile, CURRENT_PROFILE_PATH)) {
            return i18n("profile.default");
        }
        if (isProfilePath(profile, HOME_PROFILE_PATH)) {
            return i18n("profile.home");
        }

        return profile.getId().toString();
    }

    /// Returns the custom profile name in the current locale.
    public static @Nullable String getProfileCustomName(Profile profile) {
        @Nullable LocalizedText name = profile.getName();
        return name != null ? name.getText(I18n.getLocale().getCandidateLocales()) : null;
    }

    /// Returns whether the profile uses the given path.
    private static boolean isProfilePath(Profile profile, PortablePath expectedPath) {
        PortablePath actualPath = profile.getPath();
        return actualPath.isAbsolute() == expectedPath.isAbsolute()
                && actualPath.getPath().equals(expectedPath.getPath());
    }

    private static final ReadOnlyListWrapper<Profile> mergedProfilesWrapper =
            new ReadOnlyListWrapper<>(FXCollections.observableArrayList(
                    profile -> new javafx.beans.Observable[] { profile }));

    /// Whether the game directory stores have been loaded.
    private static boolean gameDirectoriesLoaded;

    private static final ObjectProperty<Profile> selectedProfile = new SimpleObjectProperty<>() {
        @Override
        protected void invalidated() {
            refreshSelectedProfile();
        }
    };

    private static void refreshSelectedProfile() {
        if (!initialized)
            return;

        ObservableList<Profile> profiles = getProfiles();
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

        settings().selectedGameDirectoryProperty().set(profile == null ? null : profile.getId());
        if (profile != null) {
            if (profile.getRepository().isLoaded()) {
                refreshSelectedVersion(profile);
            } else {
                selectedInstance.set(null);
                // bind when repository was reloaded.
                profile.getRepository().refreshVersionsAsync().start();
            }
        } else {
            selectedInstance.set(null);
        }
    }

    private static void refreshSelectedVersion(Profile profile) {
        String version = settings().getSelectedInstance(profile.getId());
        if (!profile.getRepository().hasVersion(version)) {
            Optional<String> fallback = profile.getRepository().getVersions().stream()
                    .findFirst()
                    .map(Version::getId);
            version = fallback.orElse(null);
            if (!Objects.equals(settings().getSelectedInstance(profile.getId()), version)) {
                settings().setSelectedInstance(profile.getId(), version);
            }
        }
        selectedInstance.set(version);
    }

    /// Creates the built-in game directories only when no profile exists.
    private static void createDefaultProfilesIfEmpty() {
        rebuildProfiles();
        if (!mergedProfilesWrapper.isEmpty()) {
            return;
        }

        createDefaultLocalProfile();
        createDefaultUserProfile();
        rebuildProfiles();
    }

    /// Creates the default current-workspace game directory in the local store.
    private static void createDefaultLocalProfile() {
        createDefaultProfile(SettingsManager.localGameDirectories(), CURRENT_PROFILE_PATH);
    }

    /// Creates the default user-home game directory in the user store.
    private static void createDefaultUserProfile() {
        createDefaultProfile(SettingsManager.userGameDirectories(), HOME_PROFILE_PATH);
    }

    /// Creates a built-in profile in the given game directory store.
    private static void createDefaultProfile(GameDirectories gameDirectories, PortablePath path) {
        for (Profile profile : gameDirectories.getGameDirectories()) {
            if (isProfilePath(profile, path)) {
                return;
            }
        }

        Profile profile = new Profile(newProfileId(), null, path);
        profile.setUserGameDirectory(gameDirectories.isUserFile());
        gameDirectories.getGameDirectories().add(profile);
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

    /// Loads the two game directory stores and builds the merged runtime profile view.
    static void loadGameDirectories(
            GameDirectories localGameDirectories,
            GameDirectories userGameDirectories,
            boolean createLocalDefaultProfile,
            boolean createUserDefaultProfile) {
        if (gameDirectoriesLoaded) {
            throw new IllegalStateException("Game directories are already loaded");
        }

        gameDirectoriesLoaded = true;
        localGameDirectories.addListener(onInvalidating(Profiles::rebuildProfiles));
        userGameDirectories.addListener(onInvalidating(Profiles::rebuildProfiles));
        rebuildProfiles();
        if (createLocalDefaultProfile) {
            createDefaultLocalProfile();
        }
        if (createUserDefaultProfile) {
            createDefaultUserProfile();
        }
        createDefaultProfilesIfEmpty();
    }

    /// Rebuilds the merged runtime profile view from the two backing stores.
    private static void rebuildProfiles() {
        GameDirectories userGameDirectories = SettingsManager.userGameDirectories();
        GameDirectories localGameDirectories = SettingsManager.localGameDirectories();
        Map<SettingID, Profile> visibleProfiles = new LinkedHashMap<>();

        for (Profile profile : localGameDirectories.getGameDirectories()) {
            SettingID id = profile.getId();
            visibleProfiles.remove(id);
            visibleProfiles.put(id, profile);
        }
        for (Profile profile : userGameDirectories.getGameDirectories()) {
            SettingID id = profile.getId();
            visibleProfiles.remove(id);
            visibleProfiles.put(id, profile);
        }

        mergedProfilesWrapper.setAll(visibleProfiles.values());
    }

    /// Called when it's ready to load profiles from [SettingsManager].
    public static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        getProfiles().addListener(onInvalidating(Profiles::refreshSelectedProfile));
        settings().getSelectedInstance().addListener(onInvalidating(() -> {
            Profile profile = selectedProfile.get();
            if (profile != null && profile.getRepository().isLoaded()) {
                refreshSelectedVersion(profile);
            }
        }));
        createDefaultProfilesIfEmpty();

        initialized = true;

        @Nullable SettingID selectedId = settings().selectedGameDirectoryProperty().get();
        selectedProfile.set(
                getProfiles().stream()
                        .filter(it -> it.getId().equals(selectedId))
                        .findFirst()
                        .orElse(getProfiles().isEmpty() ? null : getProfiles().get(0)));

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

    /// Returns the read-only merged profile list.
    public static @UnmodifiableView ObservableList<Profile> getProfiles() {
        if (!gameDirectoriesLoaded) {
            throw new IllegalStateException("Game directories haven't been loaded");
        }
        return mergedProfilesWrapper.get();
    }

    /// Adds a profile to the per-workspace game directory store.
    public static void addProfile(Profile profile) {
        Objects.requireNonNull(profile);
        profile.setUserGameDirectory(false);
        ObservableList<Profile> profiles = SettingsManager.localGameDirectories().getGameDirectories();
        SettingID id = profile.getId();
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(id)) {
                profiles.set(i, profile);
                return;
            }
        }

        profiles.add(profile);
    }

    /// Removes a profile and recreates the built-in game directories when the list becomes empty.
    public static void removeProfile(Profile profile) {
        GameDirectories owner = profile.shouldSaveToUserGameDirectory()
                ? SettingsManager.userGameDirectories()
                : SettingsManager.localGameDirectories();
        owner.getGameDirectories().remove(profile);
        createDefaultProfilesIfEmpty();
    }

    public static ReadOnlyListProperty<Profile> profilesProperty() {
        return mergedProfilesWrapper.getReadOnlyProperty();
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

    private static final ReadOnlyStringWrapper selectedInstance = new ReadOnlyStringWrapper();

    public static ReadOnlyStringProperty selectedInstanceProperty() {
        return selectedInstance.getReadOnlyProperty();
    }

    // Guaranteed that the repository is loaded.
    public static @Nullable String getSelectedInstance() {
        return selectedInstance.get();
    }

    /// Returns the selected instance ID for the given profile.
    public static @Nullable String getSelectedInstance(Profile profile) {
        return settings().getSelectedInstance(profile.getId());
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
        settings().setSelectedInstance(profile.getId(), instance);
        if (profile == selectedProfile.get()) {
            selectedInstance.set(settings().getSelectedInstance(profile.getId()));
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
