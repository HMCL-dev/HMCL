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
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
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
public final class GameDirectoryManager {

    /// The default current-workspace game directory path.
    private static final PortablePath CURRENT_PROFILE_PATH = PortablePath.of(".minecraft");

    /// The default user-home game directory path.
    private static final PortablePath HOME_PROFILE_PATH = PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY);

    /// Prevents instantiation.
    private GameDirectoryManager() {
    }

    /// Creates a profile ID that does not collide with existing profiles.
    public static GameDirectoryID newProfileId() {
        GameDirectoryID id;
        do {
            id = GameDirectoryID.generate();
        } while (hasProfileId(id));
        return id;
    }

    /// Returns whether an existing profile uses the given ID.
    private static boolean hasProfileId(GameDirectoryID id) {
        return localGameDirectories().getGameDirectories().stream()
                .anyMatch(profile -> profile.getId().equals(id))
                || userGameDirectories().getGameDirectories().stream()
                .anyMatch(profile -> profile.getId().equals(id));
    }

    /// Returns the display name for the given profile.
    public static String getProfileDisplayName(GameDirectoryProfile profile) {
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
    public static @Nullable String getProfileCustomName(GameDirectoryProfile profile) {
        @Nullable LocalizedText name = profile.getName();
        return name != null ? name.getText(I18n.getLocale().getCandidateLocales()) : null;
    }

    /// Returns whether the profile uses the given path.
    private static boolean isProfilePath(GameDirectoryProfile profile, PortablePath expectedPath) {
        PortablePath actualPath = profile.getPath();
        return actualPath.isAbsolute() == expectedPath.isAbsolute()
                && actualPath.getPath().equals(expectedPath.getPath());
    }

    /// True if [#init()] hasn't been called.
    private static boolean initialized = false;

    /// The mutable merged profile list used by UI bindings and profile selection.
    private static final ObservableList<GameDirectoryProfile> mergedProfiles =
            FXCollections.observableArrayList(profile -> new javafx.beans.Observable[]{profile});

    /// Read-only view of [#mergedProfiles] exposed to callers.
    private static final @UnmodifiableView ObservableList<GameDirectoryProfile> mergedProfilesUnmodifiable =
            FXCollections.unmodifiableObservableList(mergedProfiles);

    /// Runtime repositories keyed by profile object identity.
    private static final Map<GameDirectoryProfile, HMCLGameRepository> repositories = new IdentityHashMap<>();

    /// The selected profile, or `null` before the fallback profile is resolved.
    private static final ObjectProperty<@UnknownNullability GameDirectoryProfile> selectedProfile = new SimpleObjectProperty<>(GameDirectoryManager.class, "selectedProfile");

    /// The selected game repository, or `null` before the fallback profile is resolved.
    private static final ObjectProperty<@UnknownNullability HMCLGameRepository> selectedRepository = new SimpleObjectProperty<>(GameDirectoryManager.class, "selectedRepository");

    /// The selected instance ID for the selected profile.
    private static final ReadOnlyStringWrapper selectedInstance = new ReadOnlyStringWrapper(GameDirectoryManager.class, "selectedInstance");

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
            gameDirectories.getGameDirectories().add(new GameDirectoryProfile(newProfileId(), null, CURRENT_PROFILE_PATH));
        }
        if (userGameDirectories().isNewlyCreated() && userGameDirectories().getGameDirectories().isEmpty()) {
            needRebuildProfiles = true;
            GameDirectories gameDirectories = userGameDirectories();
            gameDirectories.getGameDirectories().add(new GameDirectoryProfile(newProfileId(), null, HOME_PROFILE_PATH));
        }

        needRebuildProfiles |= createDefaultProfilesIfEmpty();
        if (needRebuildProfiles) {
            rebuildProfiles();
        }

        assert !mergedProfiles.isEmpty();

        @Nullable GameDirectoryID selectedId = settings().selectedGameDirectoryProperty().get();
        GameDirectoryProfile currentProfile = null;

        if (selectedId != null) {
            for (GameDirectoryProfile profile : mergedProfiles) {
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
            HMCLGameRepository repository = getOrCreateRepository(newValue);
            selectedRepository.set(repository);
            repository.refreshVersionsAsync().start();
        });
        selectedProfile.set(currentProfile != null ? currentProfile : mergedProfiles.get(0));

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> {
            runInFX(() -> {
                @Nullable HMCLGameRepository repository = selectedRepository.get();
                if (repository != null && repository == event.getSource()) {
                    refreshSelectedVersion(repository);
                    for (Consumer<HMCLGameRepository> listener : versionsListeners)
                        listener.accept(repository);
                }
            });
        });
    }

    /// Refreshes selected instance state after the selected repository finishes loading versions.
    private static void refreshSelectedVersion(HMCLGameRepository repository) {
        GameDirectoryProfile profile = repository.getProfile();
        String version = settings().getSelectedInstance(profile.getId());
        if (!repository.hasVersion(version)) {
            Optional<String> fallback = repository.getVersions().stream()
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
                    .add(new GameDirectoryProfile(newProfileId(), null, CURRENT_PROFILE_PATH));
            userGameDirectories().getGameDirectories()
                    .add(new GameDirectoryProfile(newProfileId(), null, HOME_PROFILE_PATH));
            return true;
        } else {
            return false;
        }
    }

    /// Rebuilds the merged runtime profile view from the two backing stores.
    private static void rebuildProfiles() {
        GameDirectories userGameDirectories = userGameDirectories();
        GameDirectories localGameDirectories = localGameDirectories();
        Map<GameDirectoryID, GameDirectoryProfile> visibleProfiles = new LinkedHashMap<>();

        for (GameDirectoryProfile profile : localGameDirectories.getGameDirectories()) {
            GameDirectoryID id = profile.getId();
            visibleProfiles.put(id, profile);
        }
        for (GameDirectoryProfile profile : userGameDirectories.getGameDirectories()) {
            GameDirectoryID id = profile.getId();
            visibleProfiles.putIfAbsent(id, profile);
        }

        mergedProfiles.setAll(visibleProfiles.values());
    }

    /// Returns the read-only merged profile list.
    public static @UnmodifiableView ObservableList<GameDirectoryProfile> getProfiles() {
        return mergedProfilesUnmodifiable;
    }

    /// Returns the repository for the given game directory profile, creating it when needed.
    private static HMCLGameRepository getOrCreateRepository(GameDirectoryProfile profile) {
        Objects.requireNonNull(profile);
        return repositories.computeIfAbsent(profile, HMCLGameRepository::new);
    }

    /// Adds a profile to the per-workspace game directory store.
    public static void addLocalProfile(GameDirectoryProfile profile) {
        if (SettingsManager.isLocalGameDirectoriesReadOnly()) {
            throw new IllegalStateException("Local game directories are read-only");
        }
        addProfile(localGameDirectories(), profile);
    }

    /// Adds a profile to the user game directory store.
    public static void addUserProfile(GameDirectoryProfile profile) {
        if (SettingsManager.isUserGameDirectoriesReadOnly()) {
            throw new IllegalStateException("User game directories are read-only");
        }
        addProfile(userGameDirectories(), profile);
    }

    /// Returns whether an existing profile can be updated to the given path.
    ///
    /// @param profile the profile to update
    /// @param path the new profile path
    /// @return whether both the current and target stores may be edited
    public static boolean canUpdateProfile(GameDirectoryProfile profile, PortablePath path) {
        GameDirectories source = requireProfileStore(profile);
        GameDirectories target = getProfileStore(path);
        return !isGameDirectoriesReadOnly(source) && !isGameDirectoriesReadOnly(target);
    }

    /// Backs up and overwrites read-only game directory files required to update a profile.
    ///
    /// @param profile the profile to update
    /// @param path the new profile path
    public static void forceOverwriteProfileFiles(GameDirectoryProfile profile, PortablePath path) {
        GameDirectories source = requireProfileStore(profile);
        GameDirectories target = getProfileStore(path);
        forceOverwriteGameDirectoriesIfReadOnly(source);
        if (target != source) {
            forceOverwriteGameDirectoriesIfReadOnly(target);
        }
    }

    /// Updates a profile, moving it between local and user stores when the path type changes.
    ///
    /// @param profile the profile to update
    /// @param name the new custom profile name, or `null` for an unnamed profile
    /// @param path the new profile path
    public static void updateProfile(GameDirectoryProfile profile, @Nullable LocalizedText name, PortablePath path) {
        GameDirectories source = requireProfileStore(profile);
        GameDirectories target = getProfileStore(path);
        if (isGameDirectoriesReadOnly(source) || isGameDirectoriesReadOnly(target)) {
            throw new IllegalStateException("Game directories are read-only");
        }

        if (source == target) {
            profile.setName(name);
            profile.setPath(path);
            return;
        }

        source.getGameDirectories().remove(profile);
        profile.setName(name);
        profile.setPath(path);
        addProfile(target, profile);
    }

    /// Returns whether the profile can be removed from all stores containing it.
    ///
    /// @param profile the profile to remove
    /// @return whether all containing stores may be edited
    public static boolean canRemoveProfile(GameDirectoryProfile profile) {
        boolean localContains = localGameDirectories().getGameDirectories().contains(profile);
        boolean userContains = userGameDirectories().getGameDirectories().contains(profile);
        return (!localContains || !SettingsManager.isLocalGameDirectoriesReadOnly())
                && (!userContains || !SettingsManager.isUserGameDirectoriesReadOnly());
    }

    /// Backs up and overwrites read-only game directory files required to remove a profile.
    ///
    /// @param profile the profile to remove
    public static void forceOverwriteProfileFiles(GameDirectoryProfile profile) {
        if (localGameDirectories().getGameDirectories().contains(profile)) {
            forceOverwriteGameDirectoriesIfReadOnly(localGameDirectories());
        }
        if (userGameDirectories().getGameDirectories().contains(profile)) {
            forceOverwriteGameDirectoriesIfReadOnly(userGameDirectories());
        }
    }

    /// Adds a profile to the given game directory store, replacing a profile with the same ID in that store.
    private static void addProfile(GameDirectories gameDirectories, GameDirectoryProfile profile) {
        Objects.requireNonNull(profile);
        ObservableList<GameDirectoryProfile> profiles = gameDirectories.getGameDirectories();
        GameDirectoryID id = profile.getId();
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(id)) {
                repositories.remove(profiles.get(i));
                profiles.set(i, profile);
                rebuildProfiles();
                return;
            }
        }

        profiles.add(profile);
        rebuildProfiles();
    }

    /// Removes a profile and recreates the built-in game directories when the list becomes empty.
    public static void removeProfile(GameDirectoryProfile profile) {
        if (!canRemoveProfile(profile)) {
            throw new IllegalStateException("Game directories are read-only");
        }

        userGameDirectories().getGameDirectories().remove(profile);
        localGameDirectories().getGameDirectories().remove(profile);
        repositories.remove(profile);
        createDefaultProfilesIfEmpty();
        rebuildProfiles();

        if (!mergedProfiles.contains(selectedProfile.get())) {
            setSelectedProfile(mergedProfiles.get(0));
        }
    }

    /// Returns the store that should own profiles with the given path.
    private static GameDirectories getProfileStore(PortablePath path) {
        Objects.requireNonNull(path);
        return path.isAbsolute() ? userGameDirectories() : localGameDirectories();
    }

    /// Returns the store currently containing the profile.
    private static @Nullable GameDirectories findProfileStore(GameDirectoryProfile profile) {
        Objects.requireNonNull(profile);
        if (localGameDirectories().getGameDirectories().contains(profile)) {
            return localGameDirectories();
        }
        if (userGameDirectories().getGameDirectories().contains(profile)) {
            return userGameDirectories();
        }
        return null;
    }

    /// Returns the store currently containing the profile, or throws when it is detached.
    private static GameDirectories requireProfileStore(GameDirectoryProfile profile) {
        @Nullable GameDirectories gameDirectories = findProfileStore(profile);
        if (gameDirectories == null) {
            throw new IllegalArgumentException("Profile does not belong to a game directory store");
        }
        return gameDirectories;
    }

    /// Returns whether the given game directory store is read-only.
    private static boolean isGameDirectoriesReadOnly(GameDirectories gameDirectories) {
        return gameDirectories.isUserFile()
                ? SettingsManager.isUserGameDirectoriesReadOnly()
                : SettingsManager.isLocalGameDirectoriesReadOnly();
    }

    /// Backs up and overwrites the given game directory store when it is read-only.
    private static void forceOverwriteGameDirectoriesIfReadOnly(GameDirectories gameDirectories) {
        if (!isGameDirectoriesReadOnly(gameDirectories)) {
            return;
        }

        if (gameDirectories.isUserFile()) {
            SettingsManager.forceOverwriteUserGameDirectories();
        } else {
            SettingsManager.forceOverwriteLocalGameDirectories();
        }
    }

    /// Returns the selected profile, creating built-in profiles first if the profile list is empty.
    public static GameDirectoryProfile getSelectedProfile() {
        GameDirectoryProfile profile = selectedProfile.get();
        if (profile == null) {
            throw new IllegalStateException("Selected profile cannot be null");
        }
        return profile;
    }

    /// Returns the selected game repository.
    public static HMCLGameRepository getSelectedRepository() {
        HMCLGameRepository repository = selectedRepository.get();
        if (repository == null) {
            throw new IllegalStateException("Selected repository cannot be null");
        }
        return repository;
    }

    /// Sets the selected profile.
    public static void setSelectedProfile(GameDirectoryProfile profile) {
        Objects.requireNonNull(profile);
        if (!mergedProfiles.contains(profile)) {
            throw new IllegalArgumentException("Unknown profile: " + profile);
        }
        selectedProfile.set(profile);
    }

    /// Returns the selected profile property.
    ///
    /// The property is exposed for UI binding. Values should be changed through
    /// [#setSelectedProfile(GameDirectoryProfile)] or by bidirectional bindings that only select
    /// profiles from [#getProfiles()].
    public static ObjectProperty<GameDirectoryProfile> selectedProfileProperty() {
        return selectedProfile;
    }

    /// Returns the selected game repository property.
    public static ObjectProperty<HMCLGameRepository> selectedRepositoryProperty() {
        return selectedRepository;
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
    public static @Nullable String getSelectedInstance(GameDirectoryProfile profile) {
        return settings().getSelectedInstance(profile.getId());
    }

    /// Sets the selected instance ID for the currently selected profile.
    public static void setSelectedInstance(@Nullable String instance) {
        @Nullable GameDirectoryProfile profile = selectedProfile.get();
        if (profile != null) {
            setSelectedInstance(profile, instance);
        }
    }

    /// Sets the selected instance ID for the given profile.
    public static void setSelectedInstance(GameDirectoryProfile profile, @Nullable String instance) {
        settings().setSelectedInstance(profile.getId(), instance);
        if (profile == selectedProfile.get()) {
            selectedInstance.set(settings().getSelectedInstance(profile.getId()));
        }
    }

    /// Listeners notified after the selected repository has loaded versions.
    private static final List<Consumer<HMCLGameRepository>> versionsListeners = new ArrayList<>(4);

    /// Registers a listener that is notified when the selected repository's versions are available.
    public static void registerVersionsListener(Consumer<HMCLGameRepository> listener) {
        HMCLGameRepository repository = getSelectedRepository();
        if (repository.isLoaded())
            listener.accept(repository);
        versionsListeners.add(listener);
    }
}
