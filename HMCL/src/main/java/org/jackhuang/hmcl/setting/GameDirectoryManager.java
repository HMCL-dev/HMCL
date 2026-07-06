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
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
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
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.SettingsManager.*;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Manages the merged runtime view of local and user game directories.
///
/// Local game directories take precedence over user game directories with the same ID. This class is
/// the owner of the currently selected game directory and selected instance bookkeeping;
/// callers should use this API instead of mutating the game-directory selection fields in
/// [LauncherSettings] directly.
@NotNullByDefault
public final class GameDirectoryManager {

    /// The default current-workspace game directory path.
    private static final PortablePath CURRENT_GAME_DIRECTORY_PATH = PortablePath.of(".minecraft");

    /// The default user-home game directory path.
    private static final PortablePath HOME_GAME_DIRECTORY_PATH = PortablePath.fromPath(Metadata.MINECRAFT_DIRECTORY);

    /// Prevents instantiation.
    private GameDirectoryManager() {
    }

    /// Creates a game directory ID that does not collide with existing game directories.
    public static GameDirectoryID newGameDirectoryId() {
        GameDirectoryID id;
        do {
            id = GameDirectoryID.generate();
        } while (hasGameDirectoryId(id));
        return id;
    }

    /// Returns whether an existing game directory uses the given ID.
    private static boolean hasGameDirectoryId(GameDirectoryID id) {
        return localGameDirectories().getGameDirectories().stream()
                .anyMatch(gameDirectory -> gameDirectory.getId().equals(id))
                || userGameDirectories().getGameDirectories().stream()
                .anyMatch(gameDirectory -> gameDirectory.getId().equals(id));
    }

    /// Returns the display name for the given game directory.
    public static String getGameDirectoryDisplayName(GameDirectory gameDirectory) {
        String name = getGameDirectoryCustomName(gameDirectory);
        if (name != null) {
            return name;
        }

        if (isGameDirectoryPath(gameDirectory, CURRENT_GAME_DIRECTORY_PATH)) {
            return i18n("game_directory.default");
        }
        if (isGameDirectoryPath(gameDirectory, HOME_GAME_DIRECTORY_PATH)) {
            return i18n("game_directory.home");
        }

        return gameDirectory.getId().toString();
    }

    /// Returns the custom game directory name in the current locale.
    public static @Nullable String getGameDirectoryCustomName(GameDirectory gameDirectory) {
        @Nullable LocalizedText name = gameDirectory.getName();
        return name != null ? name.getText(I18n.getLocale().getCandidateLocales()) : null;
    }

    /// Returns whether the game directory uses the given path.
    private static boolean isGameDirectoryPath(GameDirectory gameDirectory, PortablePath expectedPath) {
        PortablePath actualPath = gameDirectory.getPath();
        return actualPath.isAbsolute() == expectedPath.isAbsolute()
                && actualPath.getPath().equals(expectedPath.getPath());
    }

    /// True if [#init()] hasn't been called.
    private static boolean initialized = false;

    /// The mutable merged game directory list used by UI bindings and game directory selection.
    private static final ObservableList<GameDirectory> mergedGameDirectories =
            FXCollections.observableArrayList(gameDirectory -> new javafx.beans.Observable[]{gameDirectory});

    /// Read-only view of [#mergedGameDirectories] exposed to callers.
    private static final @UnmodifiableView ObservableList<GameDirectory> mergedGameDirectoriesUnmodifiable =
            FXCollections.unmodifiableObservableList(mergedGameDirectories);

    /// Runtime repositories keyed by game directory object identity.
    private static final Map<GameDirectory, HMCLGameRepository> repositories = new IdentityHashMap<>();

    /// The selected game directory, or `null` before the fallback game directory is resolved.
    private static final ObjectProperty<@UnknownNullability GameDirectory> selectedGameDirectory = new SimpleObjectProperty<>(GameDirectoryManager.class, "selectedGameDirectory");

    /// The selected game repository, or `null` before the fallback game directory is resolved.
    private static final ObjectProperty<@UnknownNullability HMCLGameRepository> selectedRepository = new SimpleObjectProperty<>(GameDirectoryManager.class, "selectedRepository");

    /// The selected instance ID projected from the selected repository.
    private static final ReadOnlyStringWrapper selectedInstance = new ReadOnlyStringWrapper(GameDirectoryManager.class, "selectedInstance");

    /// Updates [#selectedInstance] when the selected repository changes its selected instance.
    private static final ChangeListener<String> selectedRepositoryInstanceListener =
            (observable, oldValue, newValue) -> selectedInstance.set(newValue);

    /// Initializes game directory state from the stores loaded by [SettingsManager].
    ///
    /// This method creates the built-in local and user-home game directories when required, rebuilds
    /// the merged game directory view, and restores the selected game directory from [LauncherSettings].
    public static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        initialized = true;

        rebuildGameDirectories();

        boolean needRebuildGameDirectories = false;
        if (localGameDirectories().isNewlyCreated() && localGameDirectories().getGameDirectories().isEmpty()) {
            needRebuildGameDirectories = true;
            GameDirectories gameDirectories = localGameDirectories();
            gameDirectories.getGameDirectories().add(new GameDirectory(newGameDirectoryId(), null, CURRENT_GAME_DIRECTORY_PATH));
        }
        if (userGameDirectories().isNewlyCreated() && userGameDirectories().getGameDirectories().isEmpty()) {
            needRebuildGameDirectories = true;
            GameDirectories gameDirectories = userGameDirectories();
            gameDirectories.getGameDirectories().add(new GameDirectory(newGameDirectoryId(), null, HOME_GAME_DIRECTORY_PATH));
        }

        needRebuildGameDirectories |= createDefaultGameDirectoriesIfEmpty();
        if (needRebuildGameDirectories) {
            rebuildGameDirectories();
        }

        assert !mergedGameDirectories.isEmpty();

        @Nullable GameDirectoryID selectedId = settings().selectedGameDirectoryProperty().get();
        GameDirectory currentGameDirectory = null;

        if (selectedId != null) {
            for (GameDirectory gameDirectory : mergedGameDirectories) {
                if (gameDirectory.getId().equals(selectedId)) {
                    currentGameDirectory = gameDirectory;
                    break;
                }
            }
        }

        selectedGameDirectory.addListener((a, b, newValue) -> {
            if (newValue == null) {
                throw new IllegalStateException("selectedGameDirectory cannot be null");
            }

            settings().selectedGameDirectoryProperty().set(newValue.getId());
            @Nullable HMCLGameRepository oldRepository = selectedRepository.get();
            if (oldRepository != null) {
                oldRepository.selectedInstanceProperty().removeListener(selectedRepositoryInstanceListener);
            }
            HMCLGameRepository repository = getOrCreateRepository(newValue);
            selectedRepository.set(repository);
            selectedInstance.set(repository.getSelectedInstance());
            repository.selectedInstanceProperty().addListener(selectedRepositoryInstanceListener);
            repository.refreshAsync().start();
        });
        selectedGameDirectory.set(currentGameDirectory != null ? currentGameDirectory : mergedGameDirectories.get(0));

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> {
            runInFX(() -> {
                @Nullable HMCLGameRepository repository = selectedRepository.get();
                if (repository != null && repository == event.getSource()) {
                    repository.refreshSelectedInstance();
                    for (Consumer<HMCLGameRepository> listener : versionsListeners)
                        listener.accept(repository);
                }
            });
        });
    }

    /// Creates the built-in game directories only when no game directory exists.
    private static boolean createDefaultGameDirectoriesIfEmpty() {
        if (localGameDirectories().getGameDirectories().isEmpty()
                && userGameDirectories().getGameDirectories().isEmpty()) {
            localGameDirectories().getGameDirectories()
                    .add(new GameDirectory(newGameDirectoryId(), null, CURRENT_GAME_DIRECTORY_PATH));
            userGameDirectories().getGameDirectories()
                    .add(new GameDirectory(newGameDirectoryId(), null, HOME_GAME_DIRECTORY_PATH));
            return true;
        } else {
            return false;
        }
    }

    /// Rebuilds the merged runtime game directory view from the two backing stores.
    private static void rebuildGameDirectories() {
        GameDirectories userGameDirectories = userGameDirectories();
        GameDirectories localGameDirectories = localGameDirectories();
        Map<GameDirectoryID, GameDirectory> visibleGameDirectories = new LinkedHashMap<>();

        for (GameDirectory gameDirectory : localGameDirectories.getGameDirectories()) {
            GameDirectoryID id = gameDirectory.getId();
            visibleGameDirectories.put(id, gameDirectory);
        }
        for (GameDirectory gameDirectory : userGameDirectories.getGameDirectories()) {
            GameDirectoryID id = gameDirectory.getId();
            visibleGameDirectories.putIfAbsent(id, gameDirectory);
        }

        mergedGameDirectories.setAll(visibleGameDirectories.values());
    }

    /// Returns the read-only merged game directory list.
    public static @UnmodifiableView ObservableList<GameDirectory> getGameDirectories() {
        return mergedGameDirectoriesUnmodifiable;
    }

    /// Returns the repository for the given game directory, creating it when needed.
    private static HMCLGameRepository getOrCreateRepository(GameDirectory gameDirectory) {
        Objects.requireNonNull(gameDirectory);
        return repositories.computeIfAbsent(gameDirectory, HMCLGameRepository::new);
    }

    /// Adds a game directory to the per-workspace store.
    public static void addLocalGameDirectory(GameDirectory gameDirectory) {
        if (SettingsManager.isLocalGameDirectoriesReadOnly()) {
            throw new IllegalStateException("Local game directories are read-only");
        }
        addGameDirectory(localGameDirectories(), gameDirectory);
    }

    /// Adds a game directory to the user store.
    public static void addUserGameDirectory(GameDirectory gameDirectory) {
        if (SettingsManager.isUserGameDirectoriesReadOnly()) {
            throw new IllegalStateException("User game directories are read-only");
        }
        addGameDirectory(userGameDirectories(), gameDirectory);
    }

    /// Returns whether an existing game directory can be updated to the given path.
    ///
    /// @param gameDirectory the game directory to update
    /// @param path the new game directory path
    /// @return whether both the current and target stores may be edited
    public static boolean canUpdateGameDirectory(GameDirectory gameDirectory, PortablePath path) {
        GameDirectories source = requireGameDirectoryStore(gameDirectory);
        GameDirectories target = getGameDirectoryStore(path);
        return !isGameDirectoriesReadOnly(source) && !isGameDirectoriesReadOnly(target);
    }

    /// Backs up and overwrites read-only game directory files required to update a game directory.
    ///
    /// @param gameDirectory the game directory to update
    /// @param path the new game directory path
    public static void forceOverwriteGameDirectoryFiles(GameDirectory gameDirectory, PortablePath path) {
        GameDirectories source = requireGameDirectoryStore(gameDirectory);
        GameDirectories target = getGameDirectoryStore(path);
        forceOverwriteGameDirectoriesIfReadOnly(source);
        if (target != source) {
            forceOverwriteGameDirectoriesIfReadOnly(target);
        }
    }

    /// Updates a game directory, moving it between local and user stores when the path type changes.
    ///
    /// @param gameDirectory the game directory to update
    /// @param name the new custom game directory name, or `null` for an unnamed game directory
    /// @param path the new game directory path
    public static void updateGameDirectory(GameDirectory gameDirectory, @Nullable LocalizedText name, PortablePath path) {
        GameDirectories source = requireGameDirectoryStore(gameDirectory);
        GameDirectories target = getGameDirectoryStore(path);
        if (isGameDirectoriesReadOnly(source) || isGameDirectoriesReadOnly(target)) {
            throw new IllegalStateException("Game directories are read-only");
        }

        if (source == target) {
            gameDirectory.setName(name);
            gameDirectory.setPath(path);
            return;
        }

        source.getGameDirectories().remove(gameDirectory);
        gameDirectory.setName(name);
        gameDirectory.setPath(path);
        addGameDirectory(target, gameDirectory);
    }

    /// Returns whether the game directory can be removed from all stores containing it.
    ///
    /// @param gameDirectory the game directory to remove
    /// @return whether all containing stores may be edited
    public static boolean canRemoveGameDirectory(GameDirectory gameDirectory) {
        boolean localContains = localGameDirectories().getGameDirectories().contains(gameDirectory);
        boolean userContains = userGameDirectories().getGameDirectories().contains(gameDirectory);
        return (!localContains || !SettingsManager.isLocalGameDirectoriesReadOnly())
                && (!userContains || !SettingsManager.isUserGameDirectoriesReadOnly());
    }

    /// Backs up and overwrites read-only game directory files required to remove a game directory.
    ///
    /// @param gameDirectory the game directory to remove
    public static void forceOverwriteGameDirectoryFiles(GameDirectory gameDirectory) {
        if (localGameDirectories().getGameDirectories().contains(gameDirectory)) {
            forceOverwriteGameDirectoriesIfReadOnly(localGameDirectories());
        }
        if (userGameDirectories().getGameDirectories().contains(gameDirectory)) {
            forceOverwriteGameDirectoriesIfReadOnly(userGameDirectories());
        }
    }

    /// Adds a game directory to the given store, replacing a game directory with the same ID in that store.
    private static void addGameDirectory(GameDirectories gameDirectories, GameDirectory gameDirectory) {
        Objects.requireNonNull(gameDirectory);
        ObservableList<GameDirectory> entries = gameDirectories.getGameDirectories();
        GameDirectoryID id = gameDirectory.getId();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(id)) {
                repositories.remove(entries.get(i));
                settings().setSelectedInstance(id, null);
                entries.set(i, gameDirectory);
                rebuildGameDirectories();
                return;
            }
        }

        entries.add(gameDirectory);
        rebuildGameDirectories();
    }

    /// Removes a game directory and recreates the built-in game directories when the list becomes empty.
    public static void removeGameDirectory(GameDirectory gameDirectory) {
        if (!canRemoveGameDirectory(gameDirectory)) {
            throw new IllegalStateException("Game directories are read-only");
        }

        userGameDirectories().getGameDirectories().remove(gameDirectory);
        localGameDirectories().getGameDirectories().remove(gameDirectory);
        repositories.remove(gameDirectory);
        createDefaultGameDirectoriesIfEmpty();
        rebuildGameDirectories();

        if (!mergedGameDirectories.contains(selectedGameDirectory.get())) {
            setSelectedGameDirectory(mergedGameDirectories.get(0));
        }
    }

    /// Returns the store that should own game directories with the given path.
    private static GameDirectories getGameDirectoryStore(PortablePath path) {
        Objects.requireNonNull(path);
        return path.isAbsolute() ? userGameDirectories() : localGameDirectories();
    }

    /// Returns the store currently containing the game directory.
    private static @Nullable GameDirectories findGameDirectoryStore(GameDirectory gameDirectory) {
        Objects.requireNonNull(gameDirectory);
        if (localGameDirectories().getGameDirectories().contains(gameDirectory)) {
            return localGameDirectories();
        }
        if (userGameDirectories().getGameDirectories().contains(gameDirectory)) {
            return userGameDirectories();
        }
        return null;
    }

    /// Returns the store currently containing the game directory, or throws when it is detached.
    private static GameDirectories requireGameDirectoryStore(GameDirectory gameDirectory) {
        @Nullable GameDirectories gameDirectories = findGameDirectoryStore(gameDirectory);
        if (gameDirectories == null) {
            throw new IllegalArgumentException("Game directory does not belong to a game directory store");
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

    /// Returns the selected game directory, creating built-in game directories first if the list is empty.
    public static GameDirectory getSelectedGameDirectory() {
        GameDirectory gameDirectory = selectedGameDirectory.get();
        if (gameDirectory == null) {
            throw new IllegalStateException("Selected game directory cannot be null");
        }
        return gameDirectory;
    }

    /// Returns the selected game repository.
    public static HMCLGameRepository getSelectedRepository() {
        HMCLGameRepository repository = selectedRepository.get();
        if (repository == null) {
            throw new IllegalStateException("Selected repository cannot be null");
        }
        return repository;
    }

    /// Sets the selected game directory.
    public static void setSelectedGameDirectory(GameDirectory gameDirectory) {
        Objects.requireNonNull(gameDirectory);
        if (!mergedGameDirectories.contains(gameDirectory)) {
            throw new IllegalArgumentException("Unknown game directory: " + gameDirectory);
        }
        selectedGameDirectory.set(gameDirectory);
    }

    /// Returns the selected game directory property.
    ///
    /// The property is exposed for UI binding. Values should be changed through
    /// [#setSelectedGameDirectory(GameDirectory)] or by bidirectional bindings that only select
    /// game directories from [#getGameDirectories()].
    public static ObjectProperty<GameDirectory> selectedGameDirectoryProperty() {
        return selectedGameDirectory;
    }

    /// Returns the selected game repository property.
    public static ObjectProperty<HMCLGameRepository> selectedRepositoryProperty() {
        return selectedRepository;
    }

    /// Returns the selected instance property projected from the selected repository.
    public static ReadOnlyStringProperty selectedInstanceProperty() {
        return selectedInstance.getReadOnlyProperty();
    }

    /// Returns the selected instance ID for the selected repository.
    public static @Nullable String getSelectedInstance() {
        return getSelectedRepository().getSelectedInstance();
    }

    /// Sets the selected instance ID for the selected repository.
    public static void setSelectedInstance(@Nullable String instance) {
        getSelectedRepository().setSelectedInstance(instance);
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
