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
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HMCL-specific game instance that owns instance-local settings and run-directory policy.
@NotNullByDefault
public class HMCLGameInstance extends DefaultGameInstance {

    /// Whether this instance is only a provisional placeholder in the current status.
    private final boolean provisional;

    /// Whether install-time code currently treats this instance as a modpack for run-directory
    /// resolution, before [HMCLGameRepository#isModpack(GameInstanceID)] becomes true.
    private boolean treatingAsModpack;

    /// Whether the instance-local game settings file has already been inspected.
    private boolean gameSettingsLoaded;

    /// Whether the instance-local game settings file cannot be overwritten safely.
    private boolean gameSettingsReadOnly;

    /// Cached instance-local game settings, or `null` when none exist after loading.
    private GameSettings.@Nullable Instance gameSettings;

    /// Creates a registered instance bound to the given repository status snapshot.
    ///
    /// @param status   the repository status that owns this instance
    /// @param id       the instance id
    /// @param manifest the stored instance manifest
    protected HMCLGameInstance(DefaultGameRepository.Status status, GameInstanceID id, GameInstanceManifest manifest) {
        this(status, id, manifest, false);
    }

    /// Creates a provisional instance used before a real manifest is indexed.
    ///
    /// @param status the repository status that owns this instance
    /// @param id     the instance id
    /// @return a provisional instance with an empty placeholder manifest
    static HMCLGameInstance provisional(DefaultGameRepository.Status status, GameInstanceID id) {
        return new HMCLGameInstance(status, id, new GameInstanceManifest(id), true);
    }

    private HMCLGameInstance(
            DefaultGameRepository.Status status,
            GameInstanceID id,
            GameInstanceManifest manifest,
            boolean provisional) {
        super(status, id, manifest);
        this.provisional = provisional;
    }

    /// Creates an instance that shares mutable instance-local state with another instance.
    ///
    /// Used when the repository clones a status snapshot or promotes a provisional instance so that
    /// settings and install-time flags remain available on the new wrapper.
    private HMCLGameInstance(
            DefaultGameRepository.Status status,
            GameInstanceID id,
            GameInstanceManifest manifest,
            boolean provisional,
            HMCLGameInstance shareState) {
        super(status, id, manifest);
        this.provisional = provisional;
        this.treatingAsModpack = shareState.treatingAsModpack;
        this.gameSettingsLoaded = shareState.gameSettingsLoaded;
        this.gameSettingsReadOnly = shareState.gameSettingsReadOnly;
        this.gameSettings = shareState.gameSettings;
        this.version = shareState.version;
    }

    @Override
    protected HMCLGameInstance withNewStatus(DefaultGameRepository.Status newStatus) {
        return new HMCLGameInstance(newStatus, id, manifest, provisional, this);
    }

    @Override
    protected HMCLGameInstance withManifest(DefaultGameRepository.Status newStatus, GameInstanceManifest manifest) {
        // A real stored manifest promotes a provisional placeholder to a registered instance.
        return new HMCLGameInstance(newStatus, id, manifest, false, this);
    }

    @Override
    public boolean isProvisional() {
        return provisional;
    }

    @Override
    public HMCLGameRepository getRepository() {
        return (HMCLGameRepository) super.getRepository();
    }

    @Override
    public HMCLGameRepositoryLayout getLayout() {
        return (HMCLGameRepositoryLayout) super.getLayout();
    }

    /// Marks this instance as a modpack for run-directory resolution during installation.
    public void markAsModpack() {
        treatingAsModpack = true;
    }

    /// Clears the install-time modpack mark.
    public void unmarkAsModpack() {
        treatingAsModpack = false;
    }

    /// Returns whether install-time code currently treats this instance as a modpack.
    ///
    /// @return whether [#markAsModpack()] is in effect
    public boolean isTreatingAsModpack() {
        return treatingAsModpack;
    }

    @Override
    public Path getRunDirectory() {
        if (treatingAsModpack || getRepository().isModpack(id)) {
            return getInstanceRoot();
        }

        GameSettings.Instance localSetting = getSettings();
        boolean useInstanceRunningDirectory =
                localSetting != null
                        && localSetting.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY);

        String runningDirectory = selectedRunningDirectory(localSetting, useInstanceRunningDirectory);
        if (StringUtils.isBlank(runningDirectory)) {
            return useInstanceRunningDirectory ? getInstanceRoot() : getLayout().getBaseDirectory();
        }

        try {
            return Path.of(runningDirectory);
        } catch (InvalidPathException ignored) {
            return getInstanceRoot();
        }
    }

    private String selectedRunningDirectory(
            @Nullable GameSettings.Instance localSetting,
            boolean useInstanceRunningDirectory) {
        if (useInstanceRunningDirectory) {
            if (localSetting == null) {
                return "";
            }

            //noinspection DataFlowIssue
            return Objects.requireNonNullElse(localSetting.runningDirectoryProperty().getValue(), "");
        }

        GameSettings.Preset parent = getRepository().getParentGameSettings(localSetting);
        //noinspection DataFlowIssue
        return Objects.requireNonNullElse(parent.runningDirectoryProperty().getValue(), "");
    }

    /// Returns the loaded instance-local game settings, loading them on first access.
    ///
    /// @return the settings, or `null` when no local settings exist after loading
    public @Nullable GameSettings.Instance getSettings() {
        ensureGameSettingsLoaded();
        return gameSettings;
    }

    /// Returns the instance-local game settings, creating an empty settings object when absent.
    ///
    /// @return the settings, or `null` when the settings file is read-only and no settings are loaded
    public @Nullable GameSettings.Instance getSettingsOrCreate() {
        GameSettings.Instance setting = getSettings();
        if (setting == null) {
            setting = createSettings();
        }
        return setting;
    }

    /// Creates empty instance-local game settings when none are loaded.
    ///
    /// @return the settings, or `null` when settings are read-only or already present in a non-creatable state
    public @Nullable GameSettings.Instance createSettings() {
        ensureGameSettingsLoaded();
        if (gameSettingsReadOnly) {
            return null;
        }
        if (gameSettings != null) {
            return gameSettings;
        }
        return initSettings(new GameSettings.Instance(), true);
    }

    /// Returns whether the instance-local game settings file cannot be overwritten safely.
    ///
    /// @return whether the settings are loaded in read-only mode
    public boolean isSettingsReadOnly() {
        ensureGameSettingsLoaded();
        return gameSettingsReadOnly;
    }

    /// Backs up and overwrites the instance-local game settings file with the currently loaded settings.
    public void forceOverwriteSettings() {
        ensureGameSettingsLoaded();

        GameSettings.Instance setting = gameSettings;
        if (setting == null) {
            setting = new GameSettings.Instance();
            gameSettings = setting;
            gameSettingsLoaded = true;
        }

        boolean installAutoSave = !setting.isSavable();
        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        SettingFileUtils.backupInvalidConfig(file);
        setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
        setting.setSavable(true);
        setting.setBackupOnNextSave(false);
        gameSettingsReadOnly = false;
        saveSettings();
        if (installAutoSave) {
            setting.addListener(a -> saveSettings());
        }
    }

    /// Saves the currently loaded instance-local game settings asynchronously when writable.
    public void saveSettings() {
        if (gameSettings == null || gameSettingsReadOnly) {
            return;
        }

        GameSettings.Instance setting = gameSettings;
        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOG.warning("Failed to create directory: " + file.getParent(), e);
        }

        if (setting.isBackupOnNextSave()) {
            setting.setBackupOnNextSave(false);
            SettingFileUtils.backupInvalidConfig(file);
        }
        FileSaver.save(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
    }

    /// Saves the currently loaded instance-local game settings synchronously when writable.
    ///
    /// @throws IOException if saving the file fails
    public void saveSettingsSync() throws IOException {
        if (gameSettings == null || gameSettingsReadOnly) {
            return;
        }

        GameSettings.Instance setting = gameSettings;
        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        if (setting.isBackupOnNextSave()) {
            setting.setBackupOnNextSave(false);
            SettingFileUtils.backupInvalidConfig(file);
        }
        FileUtils.saveSafely(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
    }

    /// Initializes this instance with the given settings object.
    ///
    /// @param setting the settings to install
    /// @return the installed settings
    public GameSettings.Instance initSettings(GameSettings.Instance setting) {
        return initSettings(setting, true);
    }

    /// Initializes this instance with the given settings object.
    ///
    /// @param setting   the settings to install
    /// @param allowSave whether the settings may be written back to disk
    /// @return the installed settings
    public GameSettings.Instance initSettings(GameSettings.Instance setting, boolean allowSave) {
        normalizeRunningDirectoryOverride(setting);
        setting.setSavable(allowSave);
        gameSettingsLoaded = true;
        gameSettings = setting;
        if (allowSave) {
            gameSettingsReadOnly = false;
            setting.addListener(a -> saveSettings());
        } else {
            gameSettingsReadOnly = true;
        }
        return setting;
    }

    /// Returns a deep copy of the currently loaded settings, or a new settings object that inherits
    /// the effective parent preset when no local settings exist.
    ///
    /// @return a detached copy suitable for installing into another instance
    public GameSettings.Instance copySettings() {
        GameSettings.Instance setting = getSettings();
        if (setting != null) {
            return JsonUtils.clone(LauncherSettings.SETTINGS_GSON, setting, TypeToken.get(GameSettings.Instance.class));
        }

        GameSettings.Instance copied = new GameSettings.Instance();
        copied.parentProperty().setValue(
                getRepository().getEffectiveGameSettings(id).getPreset().idProperty().getValue());
        return copied;
    }

    private void ensureGameSettingsLoaded() {
        if (!gameSettingsLoaded) {
            loadGameSettings();
        }
    }

    private void loadGameSettings() {
        gameSettingsLoaded = true;
        LoadResult result = loadGameSettingsFile(getGameSettingsFile());
        if (result.setting() != null) {
            initSettings(result.setting(), result.allowSave());
            return;
        }
        if (!result.allowSave()) {
            gameSettingsReadOnly = true;
            return;
        }

        @Nullable GameSettingsPresetID legacyParent = getRepository().getGameDirectory().getLegacyGameSettings();
        if (SettingsManager.getGameSettings(legacyParent) == null) {
            legacyParent = null;
        }

        LegacyGameSettingsMigrator.InstanceMigrationResult migrationResult =
                LegacyGameSettingsMigrator.migrateInstanceGameSettings(
                        getRepository(), id, legacyParent);
        if (migrationResult != null) {
            initSettings(migrationResult.setting(), true);
            try {
                saveSettingsSync();
                migrationResult.saveReceipt();
            } catch (IOException e) {
                LOG.warning("Failed to save migrated instance game settings for " + id, e);
            }
        }
    }

    private Path getGameSettingsFile() {
        return getLayout().getInstanceGameSettingsFile(id);
    }

    /// Loads a new-format instance game settings file.
    private static LoadResult loadGameSettingsFile(Path file) {
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
