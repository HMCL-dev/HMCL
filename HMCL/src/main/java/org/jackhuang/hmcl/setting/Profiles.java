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

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.util.GUID;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Profiles {

    public static final String DEFAULT_PROFILE = "Default";
    public static final String HOME_PROFILE = "Home";

    /// The stable ID used by the built-in default profile.
    public static final GUID DEFAULT_PROFILE_ID = LegacyGameSettingsMigrator.getLegacyProfileId(DEFAULT_PROFILE);

    /// The stable ID used by the built-in home profile.
    public static final GUID HOME_PROFILE_ID = LegacyGameSettingsMigrator.getLegacyProfileId(HOME_PROFILE);

    private Profiles() {
    }

    public static String getProfileDisplayName(Profile profile) {
        return switch (profile.getName()) {
            case Profiles.DEFAULT_PROFILE -> i18n("profile.default");
            case Profiles.HOME_PROFILE -> i18n("profile.home");
            default -> profile.getName();
        };
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

        ObservableList<Profile> profiles = GameDirectoriesHolder.getGameDirectories();
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

        GameDirectoriesHolder.setSelectedGameDirectory(profile == null ? null : profile.getId());
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

    private static void checkProfiles() {
        ObservableList<Profile> profiles = GameDirectoriesHolder.getGameDirectories();
        if (profiles.isEmpty()) {
            Profile current = new Profile(
                    Profiles.DEFAULT_PROFILE_ID, Profiles.DEFAULT_PROFILE, Path.of(".minecraft"), null, true);
            Profile home = new Profile(
                    Profiles.HOME_PROFILE_ID, Profiles.HOME_PROFILE, Metadata.MINECRAFT_DIRECTORY, null, false);
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

    /// Called when it's ready to load profiles from [GameDirectoriesHolder].
    static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        profilesWrapper.set(GameDirectoriesHolder.getGameDirectories());
        removeDuplicateProfiles(GameDirectoriesHolder.getGameDirectories());
        GameDirectoriesHolder.getGameDirectories().addListener(onInvalidating(Profiles::refreshSelectedProfile));
        GameDirectoriesHolder.getGameDirectories().addListener(onInvalidating(Profiles::checkProfiles));
        checkProfiles();
        migrateGameSettings();

        // Platform.runLater is necessary or profiles will be empty
        // since checkProfiles adds 2 base profile later.
        Platform.runLater(() -> {
            initialized = true;

            @Nullable GUID selectedId = GameDirectoriesHolder.getSelectedGameDirectory();
            selectedProfile.set(
                    GameDirectoriesHolder.getGameDirectories().stream()
                            .filter(it -> it.getId().equals(selectedId))
                            .findFirst()
                            .orElse(GameDirectoriesHolder.getGameDirectories().isEmpty() ? null : GameDirectoriesHolder.getGameDirectories().get(0)));
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

    private static void removeDuplicateProfiles(ObservableList<Profile> profiles) {
        HashSet<String> names = new HashSet<>();
        profiles.removeIf(profile -> {
            String name = profile.getName();
            return name == null || !names.add(name);
        });
    }

    private static void migrateGameSettings() {
        if (GameSettingsPresetsHolder.getGameSettings().isEmpty()) {
            GameSettingsPresetsHolder.getDefaultGameSettingsOrCreate();
        } else if (GameSettingsPresetsHolder.getGameSettings(GameSettingsPresetsHolder.getDefaultGameSettings()) == null) {
            GameSettingsPresetsHolder.setDefaultGameSettings(GameSettingsPresetsHolder.getGameSettings().get(0).idProperty().getValue());
        }
    }

    public static ObservableList<Profile> getProfiles() {
        return GameDirectoriesHolder.getGameDirectories();
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

    private static final List<Consumer<Profile>> versionsListeners = new ArrayList<>(4);

    public static void registerVersionsListener(Consumer<Profile> listener) {
        Profile profile = getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            listener.accept(profile);
        versionsListeners.add(listener);
    }
}
