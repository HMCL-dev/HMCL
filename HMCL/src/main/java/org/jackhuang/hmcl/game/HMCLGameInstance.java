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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.LegacyGameSettingsMigrator;
import org.jackhuang.hmcl.setting.SettingFileUtils;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HMCL-specific game instance that owns the lifecycle of instance-local [GameSettings.Instance].
@NotNullByDefault
public class HMCLGameInstance extends DefaultGameInstance {

    /// Loads, caches, and persists the instance-local game settings for this instance.
    private GameSettingsController gameSettings;

    /// Creates an instance bound to the given repository status snapshot.
    ///
    /// @param status   the repository status that owns this instance
    /// @param id       the instance id
    /// @param manifest the stored instance manifest
    protected HMCLGameInstance(DefaultGameRepository.Status status, GameInstanceID id, GameInstanceManifest manifest) {
        super(status, id, manifest);
        this.gameSettings = new GameSettingsController(getRepository(), id);
    }

    /// Creates an instance that reuses an existing settings controller.
    ///
    /// Used when the repository clones a status snapshot so that already-loaded settings and
    /// autosave listeners remain attached to the same controller.
    ///
    /// @param status        the repository status that owns this instance
    /// @param id            the instance id
    /// @param manifest      the stored instance manifest
    /// @param gameSettings  the settings controller to adopt
    private HMCLGameInstance(
            DefaultGameRepository.Status status,
            GameInstanceID id,
            GameInstanceManifest manifest,
            GameSettingsController gameSettings) {
        super(status, id, manifest);
        this.gameSettings = gameSettings;
    }

    @Override
    protected HMCLGameInstance withNewStatus(DefaultGameRepository.Status newStatus) {
        return new HMCLGameInstance(newStatus, id, manifest, gameSettings);
    }

    @Override
    protected HMCLGameInstance withManifest(DefaultGameRepository.Status newStatus, GameInstanceManifest manifest) {
        return new HMCLGameInstance(newStatus, id, manifest, gameSettings);
    }

    @Override
    public HMCLGameRepository getRepository() {
        return (HMCLGameRepository) super.getRepository();
    }

    @Override
    public HMCLGameRepositoryLayout getLayout() {
        return (HMCLGameRepositoryLayout) super.getLayout();
    }

    /// Returns the controller that owns this instance's local game settings.
    ///
    /// @return the settings controller
    GameSettingsController gameSettings() {
        return gameSettings;
    }

    /// Replaces this instance's settings controller.
    ///
    /// Used when a detached controller created before the instance was indexed should become the
    /// authoritative controller for the newly registered instance.
    ///
    /// @param gameSettings the controller to adopt
    void adoptGameSettings(GameSettingsController gameSettings) {
        this.gameSettings = gameSettings;
    }

    /// Returns the loaded instance-local game settings, loading them on first access.
    ///
    /// @return the settings, or `null` when no local settings exist after loading
    public @Nullable GameSettings.Instance getSettings() {
        return gameSettings.get();
    }

    /// Returns the instance-local game settings, creating an empty settings object when absent.
    ///
    /// @return the settings, or `null` when the settings file is read-only and no settings are loaded
    public @Nullable GameSettings.Instance getSettingsOrCreate() {
        return gameSettings.getOrCreate();
    }

    /// Creates empty instance-local game settings when none are loaded.
    ///
    /// @return the settings, or `null` when settings already exist in read-only mode or cannot be created
    public @Nullable GameSettings.Instance createSettings() {
        return gameSettings.create();
    }

    /// Returns whether the instance-local game settings file cannot be overwritten safely.
    ///
    /// @return whether the settings are loaded in read-only mode
    public boolean isSettingsReadOnly() {
        return gameSettings.isReadOnly();
    }

    /// Backs up and overwrites the instance-local game settings file with the currently loaded settings.
    public void forceOverwriteSettings() {
        gameSettings.forceOverwrite();
    }

    /// Saves the currently loaded instance-local game settings asynchronously when writable.
    public void saveSettings() {
        gameSettings.save();
    }

    /// Saves the currently loaded instance-local game settings synchronously when writable.
    ///
    /// @throws IOException if saving the file fails
    public void saveSettingsSync() throws IOException {
        gameSettings.saveSync();
    }

    /// Initializes this instance with the given settings object.
    ///
    /// @param setting the settings to install
    /// @return the installed settings
    public GameSettings.Instance initSettings(GameSettings.Instance setting) {
        return gameSettings.init(setting, true);
    }

    /// Initializes this instance with the given settings object.
    ///
    /// @param setting   the settings to install
    /// @param allowSave whether the settings may be written back to disk
    /// @return the installed settings
    public GameSettings.Instance initSettings(GameSettings.Instance setting, boolean allowSave) {
        return gameSettings.init(setting, allowSave);
    }

    /// Returns a deep copy of the currently loaded settings, or a new settings object that inherits
    /// the effective parent preset when no local settings exist.
    ///
    /// @return a detached copy suitable for installing into another instance
    public GameSettings.Instance copySettings() {
        return gameSettings.copy();
    }

    /// Owns the load, cache, mutation, and persistence lifecycle of one instance's local game settings.
    ///
    /// A controller may be attached to an [HMCLGameInstance], or held temporarily by
    /// [HMCLGameRepository] for instance IDs that are not yet present in the repository index
    /// (for example during new-instance installation).
    @NotNullByDefault
    static final class GameSettingsController {
        private final HMCLGameRepository repository;
        private final GameInstanceID instanceId;

        private boolean loaded;
        private boolean readOnly;
        private GameSettings.@Nullable Instance settings;

        /// Creates a controller for the given repository and instance id.
        ///
        /// @param repository the owning repository
        /// @param instanceId the instance id whose settings file is managed
        GameSettingsController(HMCLGameRepository repository, GameInstanceID instanceId) {
            this.repository = repository;
            this.instanceId = instanceId;
        }

        /// Returns the instance id managed by this controller.
        ///
        /// @return the instance id
        GameInstanceID instanceId() {
            return instanceId;
        }

        /// Returns whether the settings file has already been inspected.
        ///
        /// @return whether loading has been attempted
        boolean isLoaded() {
            return loaded;
        }

        /// Returns whether the settings file cannot be overwritten safely.
        ///
        /// @return whether the settings are read-only
        boolean isReadOnly() {
            ensureLoaded();
            return readOnly;
        }

        /// Returns the loaded settings, loading them on first access.
        ///
        /// @return the settings, or `null` when no local settings exist after loading
        @Nullable GameSettings.Instance get() {
            ensureLoaded();
            return settings;
        }

        /// Returns the settings, creating empty writable settings when absent.
        ///
        /// @return the settings, or `null` when the settings file is read-only and no settings are loaded
        @Nullable GameSettings.Instance getOrCreate() {
            GameSettings.Instance setting = get();
            if (setting == null) {
                setting = create();
            }
            return setting;
        }

        /// Creates empty writable settings when none are loaded.
        ///
        /// @return the settings, or `null` when settings are read-only or already present
        @Nullable GameSettings.Instance create() {
            ensureLoaded();
            if (readOnly) {
                return null;
            }
            if (settings != null) {
                return settings;
            }
            return init(new GameSettings.Instance(), true);
        }

        /// Installs the given settings object as the cached local settings.
        ///
        /// @param setting   the settings to install
        /// @param allowSave whether the settings may be written back to disk
        /// @return the installed settings
        GameSettings.Instance init(GameSettings.Instance setting, boolean allowSave) {
            normalizeRunningDirectoryOverride(setting);
            setting.setSavable(allowSave);
            loaded = true;
            settings = setting;
            if (allowSave) {
                readOnly = false;
                setting.addListener(a -> save());
            } else {
                readOnly = true;
            }
            return setting;
        }

        /// Backs up and overwrites the settings file with the currently loaded settings.
        void forceOverwrite() {
            ensureLoaded();

            GameSettings.Instance setting = settings;
            if (setting == null) {
                setting = new GameSettings.Instance();
                settings = setting;
                loaded = true;
            }

            boolean installAutoSave = !setting.isSavable();
            Path file = settingsFile().toAbsolutePath().normalize();
            SettingFileUtils.backupInvalidConfig(file);
            setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
            setting.setSavable(true);
            setting.setBackupOnNextSave(false);
            readOnly = false;
            save();
            if (installAutoSave) {
                setting.addListener(a -> save());
            }
        }

        /// Saves the currently loaded settings asynchronously when writable.
        void save() {
            if (settings == null || readOnly) {
                return;
            }

            GameSettings.Instance setting = settings;
            Path file = settingsFile().toAbsolutePath().normalize();
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException e) {
                LOG.warning("Failed to create directory: " + file.getParent(), e);
            }

            if (setting.isBackupOnNextSave()) {
                setting.setBackupOnNextSave(false);
                SettingFileUtils.backupInvalidConfig(file);
            }
            org.jackhuang.hmcl.util.FileSaver.save(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
        }

        /// Saves the currently loaded settings synchronously when writable.
        ///
        /// @throws IOException if saving the file fails
        void saveSync() throws IOException {
            if (settings == null || readOnly) {
                return;
            }

            GameSettings.Instance setting = settings;
            Path file = settingsFile().toAbsolutePath().normalize();
            Files.createDirectories(file.getParent());
            if (setting.isBackupOnNextSave()) {
                setting.setBackupOnNextSave(false);
                SettingFileUtils.backupInvalidConfig(file);
            }
            FileUtils.saveSafely(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
        }

        /// Returns a deep copy of the loaded settings, or a new object bound to the effective parent.
        ///
        /// @return a detached copy of the settings
        GameSettings.Instance copy() {
            GameSettings.Instance setting = get();
            if (setting != null) {
                return JsonUtils.clone(LauncherSettings.SETTINGS_GSON, setting, TypeToken.get(GameSettings.Instance.class));
            }

            GameSettings.Instance copied = new GameSettings.Instance();
            copied.parentProperty().setValue(
                    repository.getEffectiveGameSettings(instanceId).getPreset().idProperty().getValue());
            return copied;
        }

        private void ensureLoaded() {
            if (!loaded) {
                load();
            }
        }

        private void load() {
            loaded = true;
            LoadResult result = loadSettingsFile(settingsFile());
            if (result.setting() != null) {
                init(result.setting(), result.allowSave());
                return;
            }
            if (!result.allowSave()) {
                readOnly = true;
                return;
            }

            @Nullable GameSettingsPresetID legacyParent = repository.getGameDirectory().getLegacyGameSettings();
            if (SettingsManager.getGameSettings(legacyParent) == null) {
                legacyParent = null;
            }

            LegacyGameSettingsMigrator.InstanceMigrationResult migrationResult =
                    LegacyGameSettingsMigrator.migrateInstanceGameSettings(
                            repository, instanceId, legacyParent);
            if (migrationResult != null) {
                init(migrationResult.setting(), true);
                try {
                    saveSync();
                    migrationResult.saveReceipt();
                } catch (IOException e) {
                    LOG.warning("Failed to save migrated instance game settings for " + instanceId, e);
                }
            }
        }

        private Path settingsFile() {
            return repository.getLayout().getInstanceGameSettingsFile(instanceId);
        }

        /// Loads a new-format instance game settings file.
        private static LoadResult loadSettingsFile(Path file) {
            if (!Files.exists(file)) {
                return new LoadResult(null, true);
            }

            try {
                JsonObject jsonObject = JsonUtils.fromJsonFile(LauncherSettings.SETTINGS_GSON, file, JsonObject.class);
                if (jsonObject == null) {
                    LOG.warning("Instance game settings are empty: " + file);
                    GameSettings.Instance fallback = new GameSettings.Instance();
                    return new LoadResult(fallback, true);
                }

                JsonSchema.CompatibilityResult schemaResult =
                        JsonSchema.check(jsonObject, GameSettings.Instance.CURRENT_SCHEMA);
                switch (schemaResult.status()) {
                    case MISSING -> LOG.warning("Missing schema in instance game settings: " + file);
                    case INVALID -> LOG.warning("Invalid schema in instance game settings: "
                            + file + ", Actual: " + schemaResult.invalidValue());
                    case UNPARSEABLE -> LOG.warning("Unparseable schema in instance game settings: "
                            + file + ", Actual: " + schemaResult.actual());
                    case UNEXPECTED_ID -> LOG.warning("Unexpected instance game settings schema. Expected: "
                            + GameSettings.Instance.CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                    case UNSUPPORTED_MAJOR, READ_ONLY_PRESERVE_SCHEMA ->
                            LOG.warning("Unsupported instance game settings schema. Expected: "
                                    + GameSettings.Instance.CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                    case READ_WRITE, READ_WRITE_PRESERVE_SCHEMA -> {
                    }
                }
                if (!schemaResult.readable()) {
                    GameSettings.Instance fallback = new GameSettings.Instance();
                    fallback.setSavable(false);
                    return new LoadResult(fallback, false);
                }

                GameSettings.@Nullable Instance setting =
                        LauncherSettings.SETTINGS_GSON.fromJson(jsonObject, GameSettings.Instance.class);
                if (setting == null) {
                    LOG.warning("Instance game settings deserialized to null: " + file);
                    GameSettings.Instance fallback = new GameSettings.Instance();
                    fallback.setBackupOnNextSave(true);
                    return new LoadResult(fallback, true);
                }
                if (!schemaResult.preserveSchema() && !GameSettings.Instance.CURRENT_SCHEMA.equals(setting.getSchema())) {
                    setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
                }
                return new LoadResult(setting, schemaResult.allowSave());
            } catch (JsonParseException ex) {
                LOG.warning("Failed to parse game setting " + file, ex);
                GameSettings.Instance fallback = new GameSettings.Instance();
                fallback.setBackupOnNextSave(true);
                return new LoadResult(fallback, true);
            } catch (Exception ex) {
                LOG.warning("Failed to load game setting " + file, ex);
                return new LoadResult(null, false);
            }
        }

        /// Keeps old local custom running directories effective under the new source-selection model.
        private static void normalizeRunningDirectoryOverride(GameSettings.Instance setting) {
            if (StringUtils.isNotBlank(setting.runningDirectoryProperty().getValue())) {
                setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
            }
        }

        /// Result of loading an instance-specific game settings file.
        ///
        /// @param setting   the loaded instance settings, or `null` when unavailable
        /// @param allowSave whether the file may be overwritten
        private record LoadResult(@Nullable GameSettings.Instance setting, boolean allowSave) {
        }
    }

    /// Optional reference to an HMCL game instance and its repository.
    @NotNullByDefault
    public static final class Optional {
        private final HMCLGameRepository repository;
        private final @Nullable HMCLGameInstance instance;

        /// Creates an empty optional bound only to a repository.
        ///
        /// @param repository the repository
        public Optional(HMCLGameRepository repository) {
            this.repository = repository;
            this.instance = null;
        }

        /// Creates an optional that holds the given instance.
        ///
        /// @param instance the instance
        public Optional(HMCLGameInstance instance) {
            this.repository = instance.getRepository();
            this.instance = instance;
        }

        /// Returns the repository associated with this optional.
        ///
        /// @return the repository
        public HMCLGameRepository repository() {
            return repository;
        }

        /// Returns the held instance, if any.
        ///
        /// @return the instance, or `null` when empty
        @Contract(pure = true)
        public @Nullable HMCLGameInstance instance() {
            return instance;
        }

        /// Returns the held instance id, if any.
        ///
        /// @return the instance id, or `null` when empty
        @Contract(pure = true)
        public @Nullable GameInstanceID instanceId() {
            return instance != null ? instance.getId() : null;
        }

        /// Returns whether an instance is present.
        ///
        /// @return whether [#instance()] is non-null
        public boolean isPresent() {
            return instance != null;
        }

        /// Returns whether no instance is present.
        ///
        /// @return whether [#instance()] is null
        public boolean isEmpty() {
            return instance == null;
        }
    }
}
