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
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.SettingsManager.*;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Manages the merged runtime view of local and user game directory profiles.
///
/// Local profiles take precedence over user profiles with the same ID. This class is
/// the only owner of the currently selected profile and selected instance bookkeeping;
/// callers should use this API instead of mutating the profile-related fields in
/// [LauncherSettings] directly.
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
        return localGameDirectories().getGameDirectories().stream()
                .anyMatch(profile -> profile.getId().equals(id))
                || userGameDirectories().getGameDirectories().stream()
                .anyMatch(profile -> profile.getId().equals(id));
    }

    /// Returns the display name for the given profile.
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

    /// True if [#init()] hasn't been called.
    private static boolean initialized = false;

    private static final ObservableList<Profile> mergedProfiles =
            FXCollections.observableArrayList(profile -> new javafx.beans.Observable[]{profile});

    private static final @UnmodifiableView ObservableList<Profile> mergedProfilesUnmodifiable =
            FXCollections.unmodifiableObservableList(mergedProfiles);

    /// The selected profile, or `null` before the fallback profile is resolved.
    private static final ObjectProperty<@UnknownNullability Profile> selectedProfile = new SimpleObjectProperty<>(Profiles.class, "selectedProfile");

    private static final ReadOnlyStringWrapper selectedInstance = new ReadOnlyStringWrapper(Profiles.class, "selectedInstance");

    /// Initializes profile state from the game directory stores loaded by [SettingsManager].
    ///
    /// This method creates the built-in local and user-home profiles when required, rebuilds
    /// the merged profile view, and restores the selected profile from [LauncherSettings].
    public static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        initialized = true;

        rebuildProfiles();

        boolean needRebuildProfiles = false;
        if (localGameDirectories().isNewlyCreated() && localGameDirectories().getGameDirectories().isEmpty()) {
            needRebuildProfiles = true;
            GameDirectories gameDirectories = localGameDirectories();
            gameDirectories.getGameDirectories().add(new Profile(newProfileId(), null, CURRENT_PROFILE_PATH));
        }
        if (userGameDirectories().isNewlyCreated() && userGameDirectories().getGameDirectories().isEmpty()) {
            needRebuildProfiles = true;
            GameDirectories gameDirectories = userGameDirectories();
            gameDirectories.getGameDirectories().add(new Profile(newProfileId(), null, HOME_PROFILE_PATH));
        }

        needRebuildProfiles |= createDefaultProfilesIfEmpty();
        if (needRebuildProfiles) {
            rebuildProfiles();
        }

        assert !mergedProfiles.isEmpty();

        @Nullable SettingID selectedId = settings().selectedGameDirectoryProperty().get();
        Profile currentProfile = null;

        if (selectedId != null) {
            for (Profile profile : mergedProfiles) {
                if (profile.getId().equals(selectedId)) {
                    currentProfile = profile;
                    break;
                }
            }
        }

        selectedProfile.addListener((a, b, newValue) -> {
            if (newValue == null) {
                throw new IllegalStateException("selectedProfile cannot be null");
            }

            settings().selectedGameDirectoryProperty().set(newValue.getId());
            selectedInstance.set(settings().getSelectedInstance(newValue.getId()));
            newValue.getRepository().refreshVersionsAsync().start();
        });
        selectedProfile.set(currentProfile != null ? currentProfile : mergedProfiles.get(0));

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> {
            runInFX(() -> {
                @Nullable Profile profile = selectedProfile.get();
                if (profile != null && profile.getRepository() == event.getSource()) {
                    refreshSelectedVersion(profile);
                    for (Consumer<Profile> listener : versionsListeners)
                        listener.accept(profile);
                }
            });
        });
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
    private static boolean createDefaultProfilesIfEmpty() {
        if (localGameDirectories().getGameDirectories().isEmpty()
                && userGameDirectories().getGameDirectories().isEmpty()) {
            localGameDirectories().getGameDirectories()
                    .add(new Profile(newProfileId(), null, CURRENT_PROFILE_PATH));
            userGameDirectories().getGameDirectories()
                    .add(new Profile(newProfileId(), null, HOME_PROFILE_PATH));
            return true;
        } else {
            return false;
        }
    }

    /// Rebuilds the merged runtime profile view from the two backing stores.
    private static void rebuildProfiles() {
        GameDirectories userGameDirectories = userGameDirectories();
        GameDirectories localGameDirectories = localGameDirectories();
        Map<SettingID, Profile> visibleProfiles = new LinkedHashMap<>();

        for (Profile profile : localGameDirectories.getGameDirectories()) {
            SettingID id = profile.getId();
            visibleProfiles.put(id, profile);
        }
        for (Profile profile : userGameDirectories.getGameDirectories()) {
            SettingID id = profile.getId();
            visibleProfiles.putIfAbsent(id, profile);
        }

        mergedProfiles.setAll(visibleProfiles.values());
    }

    /// Returns the read-only merged profile list.
    public static @UnmodifiableView ObservableList<Profile> getProfiles() {
        return mergedProfilesUnmodifiable;
    }

    /// Adds a profile to the per-workspace game directory store.
    public static void addLocalProfile(Profile profile) {
        addProfile(localGameDirectories(), profile);
    }

    /// Adds a profile to the user game directory store.
    public static void addUserProfile(Profile profile) {
        addProfile(userGameDirectories(), profile);
    }

    /// Adds a profile to the given game directory store, replacing a profile with the same ID in that store.
    private static void addProfile(GameDirectories gameDirectories, Profile profile) {
        Objects.requireNonNull(profile);
        ObservableList<Profile> profiles = gameDirectories.getGameDirectories();
        SettingID id = profile.getId();
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(id)) {
                profiles.set(i, profile);
                rebuildProfiles();
                return;
            }
        }

        profiles.add(profile);
        rebuildProfiles();
    }

    /// Removes a profile and recreates the built-in game directories when the list becomes empty.
    public static void removeProfile(Profile profile) {
        userGameDirectories().getGameDirectories().remove(profile);
        localGameDirectories().getGameDirectories().remove(profile);
        createDefaultProfilesIfEmpty();
        rebuildProfiles();

        if (!mergedProfiles.contains(selectedProfile.get())) {
            setSelectedProfile(mergedProfiles.get(0));
        }
    }

    /// Returns the selected profile, creating built-in profiles first if the profile list is empty.
    public static Profile getSelectedProfile() {
        Profile profile = selectedProfile.get();
        if (profile == null) {
            throw new IllegalStateException("Selected profile cannot be null");
        }
        return profile;
    }

    /// Sets the selected profile.
    public static void setSelectedProfile(Profile profile) {
        Objects.requireNonNull(profile);
        if (!mergedProfiles.contains(profile)) {
            throw new IllegalArgumentException("Unknown profile: " + profile);
        }
        selectedProfile.set(profile);
    }

    /// Returns the selected profile property.
    ///
    /// The property is exposed for UI binding. Values should be changed through
    /// [#setSelectedProfile(Profile)] or by bidirectional bindings that only select
    /// profiles from [#getProfiles()].
    public static ObjectProperty<Profile> selectedProfileProperty() {
        return selectedProfile;
    }

    /// Returns the selected instance property for the selected profile.
    public static ReadOnlyStringProperty selectedInstanceProperty() {
        return selectedInstance.getReadOnlyProperty();
    }

    /// Returns the selected instance ID for the selected profile.
    public static @Nullable String getSelectedInstance() {
        return selectedInstance.get();
    }

    /// Returns the selected instance ID for the given profile.
    public static @Nullable String getSelectedInstance(Profile profile) {
        return settings().getSelectedInstance(profile.getId());
    }

    /// Sets the selected instance ID for the currently selected profile.
    public static void setSelectedInstance(@Nullable String instance) {
        @Nullable Profile profile = selectedProfile.get();
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

    /// Registers a listener that is notified when the selected profile's versions are available.
    public static void registerVersionsListener(Consumer<Profile> listener) {
        Profile profile = getSelectedProfile();
        if (profile.getRepository().isLoaded())
            listener.accept(profile);
        versionsListeners.add(listener);
    }
}
