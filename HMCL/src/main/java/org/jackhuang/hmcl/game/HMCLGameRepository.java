/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.DefaultIsolationType;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameWindowType;
import org.jackhuang.hmcl.setting.LegacyGameSettingsMigrator;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.ProxyType;
import org.jackhuang.hmcl.setting.SettingFileUtils;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HMCL game repository implementation backed by a GameDirectory and per-instance game settings.
@NotNullByDefault
public final class HMCLGameRepository extends DefaultGameRepository {
    /// References an optional game instance in a repository.
    ///
    /// @param repository the owning game repository
    /// @param instanceId the game instance ID, or `null` when only repository context is available
    @NotNullByDefault
    public record InstanceReference(HMCLGameRepository repository, @Nullable GameInstanceID instanceId) {
    }

    /// Directory under the version root that stores HMCL-managed instance metadata.
    private static final String INSTANCE_METADATA_DIRECTORY = ".hmcl";

    /// Directory under the instance metadata directory that stores instance configuration files.
    private static final String INSTANCE_CONFIG_DIRECTORY = "config";

    /// Directory under the instance metadata directory that stores instance state files.
    private static final String INSTANCE_STATE_DIRECTORY = "state";

    /// Current file name for instance-specific game settings.
    private static final String INSTANCE_GAME_SETTINGS_FILENAME = "instance-game-settings.json";

    /// The persistent game directory for this repository.
    private final GameDirectory gameDirectory;

    /// The selected instance ID persisted for this repository's game directory.
    private final ObjectBinding<@Nullable GameInstanceID> selectedInstance;

    // instance game settings
    private final Map<GameInstanceID, GameSettings.Instance> instanceGameSettings = new HashMap<>();
    /// Instance IDs whose local game settings file has already been checked.
    private final Set<GameInstanceID> loadedInstanceGameSettings = new HashSet<>();
    private final Set<GameInstanceID> readOnlyInstanceGameSettings = new HashSet<>();
    private final Set<GameInstanceID> beingModpackInstances = new HashSet<>();

    public final EventManager<Event> onInstanceIconChanged = new EventManager<>();

    /// Creates a repository backed by the given game directory.
    public HMCLGameRepository(GameDirectory gameDirectory) {
        super(gameDirectory.getPath().toPath());
        this.gameDirectory = gameDirectory;
        this.selectedInstance = Bindings.valueAt(settings().getSelectedInstance(), gameDirectory.getId());
        gameDirectory.pathProperty().addListener((a, b, newValue) -> changeDirectory(newValue.toPath()));
    }

    /// Returns the persistent game directory for this repository.
    public GameDirectory getGameDirectory() {
        return gameDirectory;
    }

    /// Returns the selected instance ID property for this repository's game directory.
    public Binding<@Nullable GameInstanceID> selectedInstanceProperty() {
        return selectedInstance;
    }

    /// Returns the selected instance ID for this repository's game directory.
    public @Nullable GameInstanceID getSelectedInstance() {
        return selectedInstance.get();
    }

    /// Sets the selected instance ID for this repository's game directory.
    public void setSelectedInstance(@Nullable GameInstanceID instanceId) {
        settings().setSelectedInstance(gameDirectory.getId(), instanceId);
    }

    /// Refreshes the selected instance ID after versions are loaded.
    public void refreshSelectedInstance() {
        @Nullable GameInstanceID selectedInstance = settings().getSelectedInstance(gameDirectory.getId());
        @Nullable GameInstanceID refreshedInstance = selectedInstance;
        if (refreshedInstance == null || !hasInstance(refreshedInstance)) {
            refreshedInstance = getInstanceManifests().isEmpty() ? null : getInstanceManifests().iterator().next().id();
        }
        if (!Objects.equals(selectedInstance, refreshedInstance)) {
            setSelectedInstance(refreshedInstance);
        }
    }

    /// Returns a dependency manager using the currently selected download provider.
    public DefaultDependencyManager getDependency() {
        return getDependency(DownloadProviders.getDownloadProvider());
    }

    /// Returns a dependency manager using the given download provider.
    public DefaultDependencyManager getDependency(DownloadProvider downloadProvider) {
        return new DefaultDependencyManager(this, downloadProvider, HMCLCacheRepository.REPOSITORY);
    }

    @Override
    public Path getRunDirectory(GameInstanceID instanceId) {
        if (beingModpackInstances.contains(instanceId) || isModpack(instanceId)) {
            return getInstanceRoot(instanceId);
        }

        GameSettings.Instance localSetting = getInstanceGameSettings(instanceId);
        boolean useInstanceRunningDirectory =
                localSetting != null && localSetting.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY);

        String runningDirectory = getSelectedRunningDirectory(localSetting, useInstanceRunningDirectory);
        if (StringUtils.isBlank(runningDirectory)) {
            return useInstanceRunningDirectory ? getInstanceRoot(instanceId) : super.getRunDirectory(instanceId);
        }

        try {
            return Path.of(runningDirectory);
        } catch (InvalidPathException ignored) {
            return getInstanceRoot(instanceId);
        }
    }

    /// Returns the running directory string selected by the current source.
    private String getSelectedRunningDirectory(
            @Nullable GameSettings.Instance localSetting,
            boolean useInstanceRunningDirectory) {
        if (useInstanceRunningDirectory) {
            if (localSetting == null) {
                return "";
            }

            //noinspection DataFlowIssue
            return Objects.requireNonNullElse(localSetting.runningDirectoryProperty().getValue(), "");
        }

        GameSettings.Preset parent = getParentGameSettings(localSetting);
        //noinspection DataFlowIssue
        return Objects.requireNonNullElse(parent.runningDirectoryProperty().getValue(), "");
    }

    public Stream<GameInstanceManifest> getDisplayInstanceManifests() {
        return getInstanceManifests().stream()
                .filter(v -> !v.isHidden())
                .sorted(Comparator.comparing((GameInstanceManifest v) -> Lang.requireNonNullElse(v.getReleaseTime(), Instant.EPOCH))
                        .thenComparing(v -> VersionNumber.asVersion(v.id().id())));
    }

    @Override
    protected void refreshImpl() {
        instanceGameSettings.clear();
        loadedInstanceGameSettings.clear();
        readOnlyInstanceGameSettings.clear();
        super.refreshImpl();
        getInstanceManifests().stream().map(GameInstanceManifest::id).forEach(this::loadInstanceGameSettings);

        try {
            Path file = getBaseDirectory().resolve("launcher_profiles.json");
            if (!Files.exists(file) && !getInstanceManifests().isEmpty()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, PROFILE);
            }
        } catch (IOException ex) {
            LOG.warning("Unable to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex);
        }
    }

    public void changeDirectory(Path newDirectory) {
        setBaseDirectory(newDirectory);
        refreshAsync().start();
    }

    private void clean(Path directory) throws IOException {
        FileUtils.deleteDirectory(directory.resolve("crash-reports"));
        FileUtils.deleteDirectory(directory.resolve("logs"));
    }

    public void clean(GameInstanceID instanceId) throws IOException {
        clean(getBaseDirectory());
        clean(getRunDirectory(instanceId));
    }

    public void duplicateInstance(GameInstanceID srcId, GameInstanceID dstId, boolean copySaves) throws IOException {
        Path srcDir = getInstanceRoot(srcId);
        Path dstDir = getInstanceRoot(dstId);

        GameInstanceManifest fromVersion = getInstanceManifest(srcId);

        List<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(srcId.id() + ".jar");
        blackList.add(srcId.id() + ".json");
        if (!copySaves)
            blackList.add("saves");

        if (Files.exists(dstDir)) throw new IOException("Version exists");

        Files.createDirectories(dstDir);
        FileUtils.copyDirectory(srcDir, dstDir, path -> Modpack.acceptFile(path, blackList, null));

        Path fromJson = srcDir.resolve(srcId.id() + ".json");
        Path fromJar = srcDir.resolve(srcId.id() + ".jar");
        Path toJson = dstDir.resolve(dstId.id() + ".json");
        Path toJar = dstDir.resolve(dstId.id() + ".jar");

        if (Files.exists(fromJar)) {
            Files.copy(fromJar, toJar);
        }
        Files.copy(fromJson, toJson);

        JsonUtils.writeToJsonFile(toJson, fromVersion.withId(dstId).withJar(dstId));

        boolean copyOriginalGameDir;
        try {
            copyOriginalGameDir = !Files.isSameFile(getRunDirectory(srcId), getInstanceRoot(srcId));
        } catch (IOException e) {
            copyOriginalGameDir = true;
        }

        Path srcGameDir = getRunDirectory(srcId);

        GameSettings.Instance newGameSettings = copyInstanceGameSettings(srcId);
        newGameSettings.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        newGameSettings.runningDirectoryProperty().setValue("");
        initInstanceGameSettings(dstId, newGameSettings);
        saveGameSettings(dstId);

        Path dstGameDir = getRunDirectory(dstId);

        if (copyOriginalGameDir)
            FileUtils.copyDirectory(srcGameDir, dstGameDir, path -> Modpack.acceptFile(path, blackList, null));
    }

    private GameSettings.Instance copyInstanceGameSettings(GameInstanceID instanceId) {
        GameSettings.Instance setting = getInstanceGameSettings(instanceId);
        if (setting != null) {
            return JsonUtils.clone(LauncherSettings.SETTINGS_GSON, setting, TypeToken.get(GameSettings.Instance.class));
        }

        GameSettings.Instance copied = new GameSettings.Instance();
        copied.parentProperty().setValue(getEffectiveGameSettings(instanceId).getPreset().idProperty().getValue());
        return copied;
    }

    /// Returns the HMCL-managed metadata directory under the version root.
    ///
    /// This directory stores instance-scoped files owned by HMCL.
    public Path getInstanceMetadataDirectory(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve(INSTANCE_METADATA_DIRECTORY);
    }

    /// Returns the HMCL-managed configuration directory under the instance metadata directory.
    public Path getInstanceConfigDirectory(GameInstanceID instanceId) {
        return getInstanceMetadataDirectory(instanceId).resolve(INSTANCE_CONFIG_DIRECTORY);
    }

    /// Returns the HMCL-managed state directory under the instance metadata directory.
    public Path getInstanceStateDirectory(GameInstanceID instanceId) {
        return getInstanceMetadataDirectory(instanceId).resolve(INSTANCE_STATE_DIRECTORY);
    }

    /// Returns the current local game settings path under the instance configuration directory.
    private Path getInstanceGameSettingsFile(GameInstanceID instanceId) {
        return getInstanceConfigDirectory(instanceId).resolve(INSTANCE_GAME_SETTINGS_FILENAME);
    }

    private void loadInstanceGameSettings(GameInstanceID instanceId) {
        loadedInstanceGameSettings.add(instanceId);
        InstanceGameSettingsLoadResult result = loadGameSettingsFile(getInstanceGameSettingsFile(instanceId));
        if (result.setting() != null) {
            initInstanceGameSettings(instanceId, result.setting(), result.allowSave());
            return;
        }
        if (!result.allowSave()) {
            readOnlyInstanceGameSettings.add(instanceId);
            return;
        }

        @Nullable GameSettingsPresetID legacyParent = gameDirectory.getLegacyGameSettings();
        if (SettingsManager.getGameSettings(legacyParent) == null) {
            legacyParent = null;
        }

        LegacyGameSettingsMigrator.InstanceMigrationResult migrationResult =
                LegacyGameSettingsMigrator.migrateInstanceGameSettings(
                        this, instanceId,
                        legacyParent);
        if (migrationResult != null) {
            initInstanceGameSettings(instanceId, migrationResult.setting());
            try {
                saveGameSettingsSync(instanceId);
                migrationResult.saveReceipt();
            } catch (IOException e) {
                LOG.warning("Failed to save migrated instance game settings for " + instanceId, e);
            }
            return;
        }
    }

    /// Loads a new-format instance game settings file.
    private InstanceGameSettingsLoadResult loadGameSettingsFile(Path file) {
        if (!Files.exists(file)) {
            return new InstanceGameSettingsLoadResult(null, true);
        }

        try {
            JsonObject jsonObject = JsonUtils.fromJsonFile(LauncherSettings.SETTINGS_GSON, file, JsonObject.class);
            if (jsonObject == null) {
                LOG.warning("Instance game settings are empty: " + file);
                GameSettings.Instance fallback = new GameSettings.Instance();
                return new InstanceGameSettingsLoadResult(fallback, true);
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
                case UNSUPPORTED_MAJOR, READ_ONLY_PRESERVE_SCHEMA -> LOG.warning("Unsupported instance game settings schema. Expected: "
                        + GameSettings.Instance.CURRENT_SCHEMA + ", Actual: " + schemaResult.actual());
                case READ_WRITE, READ_WRITE_PRESERVE_SCHEMA -> {
                }
            }
            if (!schemaResult.readable()) {
                GameSettings.Instance fallback = new GameSettings.Instance();
                fallback.setSavable(false);
                return new InstanceGameSettingsLoadResult(fallback, false);
            }

            GameSettings.Instance setting =
                    LauncherSettings.SETTINGS_GSON.fromJson(jsonObject, GameSettings.Instance.class);
            if (setting == null) {
                LOG.warning("Instance game settings deserialized to null: " + file);
                GameSettings.Instance fallback = new GameSettings.Instance();
                fallback.setBackupOnNextSave(true);
                return new InstanceGameSettingsLoadResult(fallback, true);
            }
            if (!schemaResult.preserveSchema() && !GameSettings.Instance.CURRENT_SCHEMA.equals(setting.getSchema())) {
                setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
            }
            return new InstanceGameSettingsLoadResult(setting, schemaResult.allowSave());
        } catch (JsonParseException ex) {
            LOG.warning("Failed to parse game setting " + file, ex);
            GameSettings.Instance fallback = new GameSettings.Instance();
            fallback.setBackupOnNextSave(true);
            return new InstanceGameSettingsLoadResult(fallback, true);
        } catch (Exception ex) {
            LOG.warning("Failed to load game setting " + file, ex);
            return new InstanceGameSettingsLoadResult(null, false);
        }
    }

    public @Nullable GameSettings.Instance createInstanceGameSettings(GameInstanceID instanceId) {
        if (!hasInstance(instanceId)) {
            return null;
        }
        if (readOnlyInstanceGameSettings.contains(instanceId)) {
            return null;
        }
        if (instanceGameSettings.containsKey(instanceId)) {
            return getInstanceGameSettings(instanceId);
        }

        GameSettings.Instance setting = new GameSettings.Instance();
        return initInstanceGameSettings(instanceId, setting);
    }

    private GameSettings.Instance initInstanceGameSettings(GameInstanceID instanceId, GameSettings.Instance setting) {
        return initInstanceGameSettings(instanceId, setting, true);
    }

    private GameSettings.Instance initInstanceGameSettings(GameInstanceID instanceId, GameSettings.Instance setting, boolean allowSave) {
        normalizeRunningDirectoryOverride(setting);
        setting.setSavable(allowSave);
        loadedInstanceGameSettings.add(instanceId);
        instanceGameSettings.put(instanceId, setting);
        if (allowSave) {
            readOnlyInstanceGameSettings.remove(instanceId);
            setting.addListener(a -> saveGameSettings(instanceId));
        } else {
            readOnlyInstanceGameSettings.add(instanceId);
        }
        return setting;
    }

    /// Keeps old local custom running directories effective under the new source-selection model.
    private void normalizeRunningDirectoryOverride(GameSettings.Instance setting) {
        if (StringUtils.isNotBlank(setting.runningDirectoryProperty().getValue())) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        }
    }

    @Nullable
    public GameSettings.Instance getInstanceGameSettings(GameInstanceID instanceId) {
        if (!loadedInstanceGameSettings.contains(instanceId)) {
            loadInstanceGameSettings(instanceId);
        }
        return instanceGameSettings.get(instanceId);
    }

    @Nullable
    public GameSettings.Instance getInstanceGameSettingsOrCreate(GameInstanceID instanceId) {
        GameSettings.Instance setting = getInstanceGameSettings(instanceId);
        if (setting == null) {
            setting = createInstanceGameSettings(instanceId);
        }
        return setting;
    }

    /// Returns whether the instance-specific game settings file cannot be overwritten safely.
    ///
    /// @param instanceId the instance ID
    /// @return whether the instance settings are loaded in read-only mode
    public boolean isInstanceGameSettingsReadOnly(GameInstanceID instanceId) {
        if (!loadedInstanceGameSettings.contains(instanceId)) {
            loadInstanceGameSettings(instanceId);
        }

        return readOnlyInstanceGameSettings.contains(instanceId);
    }

    /// Backs up and overwrites the instance-specific game settings file with the currently loaded settings.
    ///
    /// @param instanceId the instance ID
    public void forceOverwriteInstanceGameSettings(GameInstanceID instanceId) {
        if (!loadedInstanceGameSettings.contains(instanceId)) {
            loadInstanceGameSettings(instanceId);
        }

        GameSettings.Instance setting = instanceGameSettings.get(instanceId);
        if (setting == null) {
            setting = new GameSettings.Instance();
            instanceGameSettings.put(instanceId, setting);
            loadedInstanceGameSettings.add(instanceId);
        }

        boolean installAutoSave = !setting.isSavable();
        Path file = getInstanceGameSettingsFile(instanceId).toAbsolutePath().normalize();
        SettingFileUtils.backupInvalidConfig(file);
        setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
        setting.setSavable(true);
        setting.setBackupOnNextSave(false);
        readOnlyInstanceGameSettings.remove(instanceId);
        saveGameSettings(instanceId);
        if (installAutoSave) {
            setting.addListener(a -> saveGameSettings(instanceId));
        }
    }

    /// Returns the explicit parent preset of the instance, falling back to the default preset.
    public GameSettings.Preset getParentGameSettings(@Nullable GameSettings.Instance instance) {
        @Nullable GameSettingsPresetID parent = instance != null ? instance.parentProperty().getValue() : null;
        GameSettings.Preset parentSetting = SettingsManager.getGameSettings(parent);
        return parentSetting != null ? parentSetting : SettingsManager.getDefaultGameSettingsPresetOrCreate();
    }

    public GameSettings.Effective getEffectiveGameSettings(GameInstanceID instanceId) {
        GameSettings.Instance instance = getInstanceGameSettings(instanceId);
        return GameSettings.resolve(getParentGameSettings(instance), instance);
    }

    public void applyDefaultIsolationSetting(GameInstanceID instanceId) {
        if (!hasInstance(instanceId)) {
            return;
        }

        GameSettings.Instance instanceSetting = getInstanceGameSettings(instanceId);
        GameSettings.Preset preset = getParentGameSettings(instanceSetting);
        DefaultIsolationType type = Lang.requireNonNullElse(preset.defaultIsolationTypeProperty().getValue(), DefaultIsolationType.MODDED);
        boolean isolated = switch (type) {
            case NEVER -> false;
            case ALWAYS -> true;
            case MODDED -> LibraryAnalyzer.isModded(this, getInstanceManifest(instanceId).resolve(this));
        };

        if (isolated) {
            GameSettings.Instance setting = instanceSetting != null ? instanceSetting : getInstanceGameSettingsOrCreate(instanceId);
            if (setting != null) {
                setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
            }
        }
    }

    public Optional<Path> getInstanceIconFile(GameInstanceID instanceId) {
        Path root = getInstanceRoot(instanceId);

        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = root.resolve("icon." + extension);
            if (Files.exists(file)) {
                return Optional.of(file);
            }
        }

        return Optional.empty();
    }

    public void setInstanceIconFile(GameInstanceID instanceId, Path iconFile) throws IOException {
        String ext = FileUtils.getExtension(iconFile).toLowerCase(Locale.ROOT);
        if (!FXUtils.IMAGE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported icon file: " + ext);
        }

        deleteIconFile(instanceId);

        FileUtils.copyFile(iconFile, getInstanceRoot(instanceId).resolve("icon." + ext));
    }

    public void deleteIconFile(GameInstanceID instanceId) {
        Path root = getInstanceRoot(instanceId);
        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = root.resolve("icon." + extension);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete icon file: " + file, e);
            }
        }
    }

    public Image getInstanceIconImage(@Nullable GameInstanceID instanceId) {
        if (instanceId == null || !isLoaded())
            return VersionIconType.DEFAULT.getIcon();

        GameSettings.Instance setting = getInstanceGameSettings(instanceId);
        VersionIconType iconType = setting != null ? Lang.requireNonNullElse(setting.iconProperty().getValue(), VersionIconType.DEFAULT) : VersionIconType.DEFAULT;

        if (iconType == VersionIconType.DEFAULT) {
            GameInstanceManifest version = getInstanceManifest(instanceId).resolve(this);
            Optional<Path> iconFile = getInstanceIconFile(instanceId);
            if (iconFile.isPresent()) {
                try {
                    return FXUtils.loadImage(iconFile.get(), 64, 64, true, true);
                } catch (Exception e) {
                    LOG.warning("Failed to load version icon of " + instanceId, e);
                }
            }

            if (LibraryAnalyzer.isModded(this, version)) {
                LibraryAnalyzer libraryAnalyzer = LibraryAnalyzer.analyze(version, null);
                if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.FABRIC))
                    return VersionIconType.FABRIC.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.QUILT))
                    return VersionIconType.QUILT.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.LEGACY_FABRIC))
                    return VersionIconType.LEGACY_FABRIC.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE))
                    return VersionIconType.NEO_FORGE.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.FORGE))
                    return VersionIconType.FORGE.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM))
                    return VersionIconType.CLEANROOM.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.LITELOADER))
                    return VersionIconType.CHICKEN.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.OPTIFINE))
                    return VersionIconType.OPTIFINE.getIcon();
            }

            String gameVersion = getGameVersion(version).orElse(null);
            if (gameVersion != null) {
                GameVersionNumber versionNumber = GameVersionNumber.asGameVersion(gameVersion);
                if (versionNumber.isAprilFools()) {
                    return VersionIconType.APRIL_FOOLS.getIcon();
                } else if (versionNumber instanceof GameVersionNumber.LegacySnapshot) {
                    return VersionIconType.COMMAND.getIcon();
                } else if (versionNumber instanceof GameVersionNumber.Old) {
                    return VersionIconType.CRAFT_TABLE.getIcon();
                }
            }
            return VersionIconType.GRASS.getIcon();
        } else {
            return iconType.getIcon();
        }
    }

    public void saveGameSettings(GameInstanceID instanceId) {
        if (!instanceGameSettings.containsKey(instanceId) || readOnlyInstanceGameSettings.contains(instanceId))
            return;
        GameSettings.Instance setting = instanceGameSettings.get(instanceId);
        if (setting == null) {
            return;
        }
        Path file = getInstanceGameSettingsFile(instanceId).toAbsolutePath().normalize();
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

    /// Saves instance-specific game settings synchronously.
    ///
    /// @param instanceId the instance ID
    /// @throws IOException if saving the file fails
    private void saveGameSettingsSync(GameInstanceID instanceId) throws IOException {
        if (!instanceGameSettings.containsKey(instanceId) || readOnlyInstanceGameSettings.contains(instanceId)) {
            return;
        }

        GameSettings.Instance setting = instanceGameSettings.get(instanceId);
        if (setting == null) {
            return;
        }

        Path file = getInstanceGameSettingsFile(instanceId).toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        if (setting.isBackupOnNextSave()) {
            setting.setBackupOnNextSave(false);
            SettingFileUtils.backupInvalidConfig(file);
        }
        FileUtils.saveSafely(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
    }

    /// Result of loading an instance-specific game settings file.
    ///
    /// @param setting the loaded instance settings, or `null` when unavailable
    /// @param allowSave whether the file may be overwritten
    private record InstanceGameSettingsLoadResult(
            @Nullable GameSettings.Instance setting,
            boolean allowSave) {
    }

    public LaunchOptions.Builder getLaunchOptions(GameInstanceID instanceId, JavaRuntime javaVersion, Path gameDir, List<String> javaAgents, List<String> javaArguments, boolean makeLaunchScript) {
        GameSettings.Effective vs = getEffectiveGameSettings(instanceId);
        boolean noJVMOptions = vs.getInheritable(GameSettings::noJVMOptionsProperty);
        boolean autoMemory = vs.get(GameSettings::autoMemoryProperty);
        GameVersionNumber gameVersionNumber = GameVersionNumber.asGameVersion(getGameVersion(instanceId));

        LaunchOptions.Builder builder = new LaunchOptions.Builder()
                .setGameDir(gameDir)
                .setJava(javaVersion)
                .setVersionType(Metadata.TITLE)
                .setVersionName(instanceId.id())
                .setProfileName(Metadata.TITLE)
                .setGameArguments(StringUtils.tokenize(vs.get(GameSettings::gameArgumentsProperty)))
                .setOverrideJavaArguments(StringUtils.tokenize(vs.get(GameSettings::jvmOptionsProperty)))
                .setMaxMemory(noJVMOptions && autoMemory ? null : (int) (getAllocatedMemory(
                        vs.getMaxMemory() * 1024L * 1024L,
                        SystemInfo.getPhysicalMemoryStatus().available(),
                        autoMemory
                ) / 1024 / 1024))
                .setMinMemory(vs.get(GameSettings::minMemoryProperty))
                .setMetaspace(Lang.toIntOrNull(vs.get(GameSettings::permSizeProperty)))
                .setEnvironmentVariables(
                        Lang.mapOf(StringUtils.tokenize(vs.get(GameSettings::environmentVariablesProperty))
                                .stream()
                                .map(it -> {
                                    int idx = it.indexOf('=');
                                    return idx >= 0 ? pair(it.substring(0, idx), it.substring(idx + 1)) : pair(it, "");
                                })
                                .collect(Collectors.toList())
                        )
                )
                .setWidth(vs.getWidth())
                .setHeight(vs.getHeight())
                .setFullscreen(vs.getInheritable(GameSettings::windowTypeProperty) == GameWindowType.FULLSCREEN)
                .setWrapper(vs.getInheritable(GameSettings::commandWrapperProperty))
                .setProxyOption(getProxyOption())
                .setPreLaunchCommand(vs.getInheritable(GameSettings::preLaunchCommandProperty))
                .setPostExitCommand(vs.getInheritable(GameSettings::postExitCommandProperty))
                .setNoGeneratedJVMArgs(noJVMOptions)
                .setNoGeneratedOptimizingJVMArgs(vs.getInheritable(GameSettings::noOptimizingJVMOptionsProperty))
                .setUseCustomNatives(vs.get(GameSettings::useCustomNativesProperty))
                .setNativesDir(vs.get(GameSettings::nativesDirectoryProperty))
                .setProcessPriority(vs.getInheritable(GameSettings::processPriorityProperty))
                .setGraphicsBackend(vs.getInheritable(GameSettings::graphicsBackendProperty))
                .setRenderer(vs.getRenderer(gameVersionNumber))
                .setEnableDebugLogOutput(vs.getInheritable(GameSettings::enableDebugLogOutputProperty))
                .setAllowAutoAgent(vs.getInheritable(GameSettings::allowAutoAgentProperty))
                .setDisableAutoGameOptions(vs.getInheritable(GameSettings::disableAutoGameOptionsProperty))
                .setUseNativeGLFW(vs.get(GameSettings::useNativeGLFWProperty))
                .setUseNativeOpenAL(vs.get(GameSettings::useNativeOpenALProperty))
                .setDaemon(!makeLaunchScript && vs.getInheritable(GameSettings::launcherVisibilityProperty).isDaemon())
                .setJavaAgents(javaAgents)
                .setJavaArguments(javaArguments);

        QuickPlayOption quickPlayOption = vs.getQuickPlayOption();
        if (quickPlayOption != null) {
            builder.setQuickPlayOption(quickPlayOption);
        }

        Path json = getModpackConfiguration(instanceId);
        if (Files.exists(json)) {
            try {
                String jsonText = Files.readString(json);
                ModpackConfiguration<?> modpackConfiguration = JsonUtils.GSON.fromJson(jsonText, ModpackConfiguration.class);
                ModpackProvider provider = ModpackHelper.getProviderByType(modpackConfiguration.getType());
                if (provider != null) provider.injectLaunchOptions(jsonText, builder);
            } catch (IOException | JsonParseException e) {
                LOG.warning("Failed to parse modpack configuration file " + json, e);
            }
        }

        if (autoMemory && builder.getJavaArguments().stream().anyMatch(it -> it.startsWith("-Xmx")))
            builder.setMaxMemory(null);

        return builder;
    }

    @Override
    public Path getModpackConfiguration(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve("modpack.cfg");
    }

    public void markInstanceAsModpack(GameInstanceID instanceId) {
        beingModpackInstances.add(instanceId);
    }

    public void undoMark(GameInstanceID instanceId) {
        beingModpackInstances.remove(instanceId);
    }

    public void markInstanceLaunchedAbnormally(GameInstanceID instanceId) {
        try {
            Files.createFile(getInstanceRoot(instanceId).resolve(".abnormal"));
        } catch (IOException ignored) {
        }
    }

    public boolean unmarkInstanceLaunchedAbnormally(GameInstanceID instanceId) {
        Path file = getInstanceRoot(instanceId).resolve(".abnormal");
        if (Files.isRegularFile(file)) {
            try {
                Files.delete(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete abnormal mark file: " + file, e);
            }

            return true;
        } else {
            return false;
        }
    }

    private static final String PROFILE = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";


    // These version ids are forbidden because they may conflict with modpack configuration filenames
    private static final Set<String> FORBIDDEN_INSTANCE_IDS = new HashSet<>(Arrays.asList(
            "modpack", "minecraftinstance", "manifest"));

    public static boolean isValidInstanceId(String id) {
        if (FORBIDDEN_INSTANCE_IDS.contains(id))
            return false;

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS &&
                FORBIDDEN_INSTANCE_IDS.contains(id.toLowerCase(Locale.ROOT)))
            return false;

        return FileUtils.isNameValidForJar(id);
    }

    /**
     * Returns true if the given version id conflicts with an existing version.
     */
    public boolean instanceIdConflicts(String instanceId) {
        try {
            return instanceIdConflicts(new GameInstanceID(instanceId));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean instanceIdConflicts(GameInstanceID id) {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            // on Windows, filenames are case-insensitive
            for (GameInstanceManifest manifest : getInstanceManifests()) {
                if (manifest.id().toString().equalsIgnoreCase(id.toString())) {
                    return true;
                }
            }
            return false;
        } else {
            return hasInstance(id);
        }
    }

    public static long getAllocatedMemory(long minimum, long available, boolean auto) {
        if (auto) {
            available -= 512 * 1024 * 1024; // Reserve 512 MiB memory for off-heap memory and HMCL itself
            if (available <= 0) {
                return minimum;
            }

            final long threshold = 8L * 1024 * 1024 * 1024; // 8 GiB
            final long suggested = Math.min(available <= threshold
                            ? (long) (available * 0.8)
                            : (long) (threshold * 0.8 + (available - threshold) * 0.2),
                    16L * 1024 * 1024 * 1024);
            return Math.max(minimum, suggested);
        } else {
            return minimum;
        }
    }

    public static ProxyOption getProxyOption() {
        return switch (settings().proxyTypeProperty().get()) {
            case SYSTEM -> ProxyOption.Default.INSTANCE;
            case DIRECT -> ProxyOption.Direct.INSTANCE;
            case HTTP, SOCKS -> {
                String proxyHost = settings().proxyHostProperty().get();
                int proxyPort = settings().proxyPortProperty().get();

                if (StringUtils.isBlank(proxyHost) || proxyPort < 0 || proxyPort > 0xFFFF) {
                    yield ProxyOption.Default.INSTANCE;
                }

                String proxyUser = settings().proxyUserProperty().get();
                String proxyPass = settings().proxyPasswordProperty().get();

                if (StringUtils.isBlank(proxyUser)) {
                    proxyUser = null;
                    proxyPass = null;
                } else if (proxyPass == null) {
                    proxyPass = "";
                }

                if (settings().proxyTypeProperty().get() == ProxyType.HTTP) {
                    yield new ProxyOption.Http(proxyHost, proxyPort, proxyUser, proxyPass);
                } else {
                    yield new ProxyOption.Socks(proxyHost, proxyPort, proxyUser, proxyPass);
                }
            }
        };
    }
}
