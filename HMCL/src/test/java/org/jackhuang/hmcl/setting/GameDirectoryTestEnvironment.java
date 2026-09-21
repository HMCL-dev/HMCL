/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/// Temporary static state override for tests exercising [GameDirectoryManager].
///
/// The environment replaces the game directory, launcher settings and game settings preset state owned by
/// [SettingsManager] together with the runtime state owned by [GameDirectoryManager], so that
/// [GameDirectoryManager#init()] can run against stores supplied by a test. All replaced state is restored
/// by [GameDirectoryTestEnvironment#close()].
@NotNullByDefault
public final class GameDirectoryTestEnvironment implements AutoCloseable {
    /// The reflected SettingsManager local game directories field.
    private final Field localGameDirectoriesField;

    /// The reflected SettingsManager user game directories field.
    private final Field userGameDirectoriesField;

    /// The reflected SettingsManager launcher settings field.
    private final Field launcherSettingsField;

    /// The reflected SettingsManager game settings presets field.
    private final Field gameSettingsPresetsField;

    /// The reflected SettingsManager local game directories access field.
    private final Field localGameDirectoriesAccessField;

    /// The reflected SettingsManager user game directories access field.
    private final Field userGameDirectoriesAccessField;

    /// The reflected GameDirectoryManager initialized field.
    private final Field initializedField;

    /// The reflected GameDirectoryManager selected game directory property.
    private final ObjectProperty<GameDirectory> selectedGameDirectory;

    /// The reflected GameDirectoryManager selected repository property.
    private final ObjectProperty<HMCLGameRepository> selectedRepository;

    /// The merged game directory list used by GameDirectoryManager.
    private final ObservableList<GameDirectory> mergedGameDirectories;

    /// The repositories mapped by GameDirectoryManager.
    private final Map<GameDirectory, HMCLGameRepository> repositories;

    /// The previous local game directories instance.
    private final @Nullable Object previousLocalGameDirectories;

    /// The previous user game directories instance.
    private final @Nullable Object previousUserGameDirectories;

    /// The previous launcher settings instance.
    private final @Nullable Object previousLauncherSettings;

    /// The previous game settings presets instance.
    private final @Nullable Object previousGameSettingsPresets;

    /// The previous local game directories access.
    private final SettingFileAccess previousLocalGameDirectoriesAccess;

    /// The previous user game directories access.
    private final SettingFileAccess previousUserGameDirectoriesAccess;

    /// The previous GameDirectoryManager initialization state.
    private final boolean previousInitialized;

    /// The previous selected game directory.
    private final @Nullable GameDirectory previousSelectedGameDirectory;

    /// The previous selected repository.
    private final @Nullable HMCLGameRepository previousSelectedRepository;

    /// The previous merged game directories.
    private final List<GameDirectory> previousMergedGameDirectories;

    /// The previous repository map entries.
    private final Map<GameDirectory, HMCLGameRepository> previousRepositories;

    /// Creates an empty store marked as the per-workspace game directory store.
    public static GameDirectories newLocalGameDirectories() {
        GameDirectories gameDirectories = new GameDirectories();
        gameDirectories.setUserFile(false);
        return gameDirectories;
    }

    /// Creates an empty store marked as the user game directory store.
    public static GameDirectories newUserGameDirectories() {
        GameDirectories gameDirectories = new GameDirectories();
        gameDirectories.setUserFile(true);
        return gameDirectories;
    }

    /// Replaces game-directory-related static state with the given stores and an empty preset store.
    public GameDirectoryTestEnvironment(GameDirectories localDirectories, GameDirectories userDirectories)
            throws ReflectiveOperationException {
        this(localDirectories, userDirectories, new GameSettingsPresets());
    }

    /// Replaces game-directory-related static state with the given stores.
    public GameDirectoryTestEnvironment(
            GameDirectories localDirectories,
            GameDirectories userDirectories,
            GameSettingsPresets gameSettingsPresets) throws ReflectiveOperationException {
        localGameDirectoriesField = SettingsManager.class.getDeclaredField("localGameDirectories");
        userGameDirectoriesField = SettingsManager.class.getDeclaredField("userGameDirectories");
        launcherSettingsField = SettingsManager.class.getDeclaredField("launcherSettings");
        gameSettingsPresetsField = SettingsManager.class.getDeclaredField("gameSettingsPresets");
        localGameDirectoriesAccessField = SettingsManager.class.getDeclaredField("localGameDirectoriesAccess");
        userGameDirectoriesAccessField = SettingsManager.class.getDeclaredField("userGameDirectoriesAccess");
        initializedField = GameDirectoryManager.class.getDeclaredField("initialized");
        Field selectedGameDirectoryField = GameDirectoryManager.class.getDeclaredField("selectedGameDirectory");
        Field selectedRepositoryField = GameDirectoryManager.class.getDeclaredField("selectedRepository");
        Field mergedGameDirectoriesField = GameDirectoryManager.class.getDeclaredField("mergedGameDirectories");
        Field repositoriesField = GameDirectoryManager.class.getDeclaredField("repositories");
        localGameDirectoriesField.setAccessible(true);
        userGameDirectoriesField.setAccessible(true);
        launcherSettingsField.setAccessible(true);
        gameSettingsPresetsField.setAccessible(true);
        localGameDirectoriesAccessField.setAccessible(true);
        userGameDirectoriesAccessField.setAccessible(true);
        initializedField.setAccessible(true);
        selectedGameDirectoryField.setAccessible(true);
        selectedRepositoryField.setAccessible(true);
        mergedGameDirectoriesField.setAccessible(true);
        repositoriesField.setAccessible(true);

        previousLocalGameDirectories = localGameDirectoriesField.get(null);
        previousUserGameDirectories = userGameDirectoriesField.get(null);
        previousLauncherSettings = launcherSettingsField.get(null);
        previousGameSettingsPresets = gameSettingsPresetsField.get(null);
        previousLocalGameDirectoriesAccess = (SettingFileAccess) localGameDirectoriesAccessField.get(null);
        previousUserGameDirectoriesAccess = (SettingFileAccess) userGameDirectoriesAccessField.get(null);
        previousInitialized = initializedField.getBoolean(null);

        @SuppressWarnings("unchecked")
        ObjectProperty<GameDirectory> selectedGameDirectory =
                (ObjectProperty<GameDirectory>) selectedGameDirectoryField.get(null);
        this.selectedGameDirectory = selectedGameDirectory;
        previousSelectedGameDirectory = selectedGameDirectory.get();

        @SuppressWarnings("unchecked")
        ObjectProperty<HMCLGameRepository> selectedRepository =
                (ObjectProperty<HMCLGameRepository>) selectedRepositoryField.get(null);
        this.selectedRepository = selectedRepository;
        previousSelectedRepository = selectedRepository.get();

        @SuppressWarnings("unchecked")
        ObservableList<GameDirectory> mergedGameDirectories =
                (ObservableList<GameDirectory>) mergedGameDirectoriesField.get(null);
        this.mergedGameDirectories = mergedGameDirectories;
        previousMergedGameDirectories = List.copyOf(mergedGameDirectories);

        @SuppressWarnings("unchecked")
        Map<GameDirectory, HMCLGameRepository> repositories =
                (Map<GameDirectory, HMCLGameRepository>) repositoriesField.get(null);
        this.repositories = repositories;
        previousRepositories = Map.copyOf(repositories);

        localGameDirectoriesField.set(null, localDirectories);
        userGameDirectoriesField.set(null, userDirectories);
        launcherSettingsField.set(null, new LauncherSettings());
        gameSettingsPresetsField.set(null, gameSettingsPresets);
        localGameDirectoriesAccessField.set(null, SettingFileAccess.READ_WRITE);
        userGameDirectoriesAccessField.set(null, SettingFileAccess.READ_WRITE);
        initializedField.setBoolean(null, false);
        mergedGameDirectories.clear();
        repositories.clear();
        selectedRepository.set(null);
    }

    /// Sets the local game directories access used by [SettingsManager].
    public void setLocalGameDirectoriesAccess(SettingFileAccess access) throws IllegalAccessException {
        localGameDirectoriesAccessField.set(null, access);
    }

    /// Sets the user game directories access used by [SettingsManager].
    public void setUserGameDirectoriesAccess(SettingFileAccess access) throws IllegalAccessException {
        userGameDirectoriesAccessField.set(null, access);
    }

    /// Restores the previous static state.
    @Override
    public void close() throws ReflectiveOperationException {
        if (previousSelectedGameDirectory != null) {
            selectedGameDirectory.set(previousSelectedGameDirectory);
        }
        selectedRepository.set(previousSelectedRepository);
        mergedGameDirectories.setAll(previousMergedGameDirectories);
        repositories.clear();
        repositories.putAll(previousRepositories);
        localGameDirectoriesField.set(null, previousLocalGameDirectories);
        userGameDirectoriesField.set(null, previousUserGameDirectories);
        launcherSettingsField.set(null, previousLauncherSettings);
        gameSettingsPresetsField.set(null, previousGameSettingsPresets);
        localGameDirectoriesAccessField.set(null, previousLocalGameDirectoriesAccess);
        userGameDirectoriesAccessField.set(null, previousUserGameDirectoriesAccess);
        initializedField.setBoolean(null, previousInitialized);
    }
}
