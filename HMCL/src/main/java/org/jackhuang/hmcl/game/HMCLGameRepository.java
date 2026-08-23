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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.DefaultIsolationType;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.LegacyGameSettingsMigrator;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Bits;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HMCL game repository implementation backed by a GameDirectory and per-instance game settings.
@NotNullByDefault
public final class HMCLGameRepository extends DefaultGameRepository {
    /// The persistent game directory for this repository.
    private final GameDirectory gameDirectory;

    /// The selected instance ID persisted for this repository's game directory.
    private final ObjectBinding<@Nullable GameInstanceID> selectedInstanceId;

    /// The selected instance resolved from the current repository snapshot.
    private final ReadOnlyObjectWrapper<@Nullable HMCLGameInstance> selectedInstance;

    /// Settings reservations transferred to the next draft that creates the corresponding id.
    private final Map<GameInstanceID, PreparedInstanceSettings> preparedInstanceSettings = new ConcurrentHashMap<>();

    /// Creates a repository backed by the given game directory.
    ///
    /// @param gameDirectory the persistent game directory represented by this repository
    public HMCLGameRepository(GameDirectory gameDirectory) {
        super(gameDirectory.getPath().toPath());
        this.gameDirectory = gameDirectory;
        this.selectedInstanceId = Bindings.valueAt(settings().getSelectedInstance(), gameDirectory.getId());
        this.selectedInstance = new ReadOnlyObjectWrapper<>(this, "selectedInstance");
        this.selectedInstance.bind(Bindings.createObjectBinding(
                this::resolveSelectedInstance,
                selectedInstanceId,
                snapshotProperty()));
        gameDirectory.pathProperty().addListener((a, b, newValue) -> changeDirectory(newValue.toPath()));
    }

    @Override
    protected HMCLGameRepositoryLayout createLayout(Path baseDirectory) {
        return new HMCLGameRepositoryLayout(baseDirectory);
    }

    @Override
    protected HMCLGameRepositorySnapshot createSnapshot(DefaultGameRepositoryLayout layout) {
        return new HMCLGameRepositorySnapshot(this, (HMCLGameRepositoryLayout) layout);
    }

    /// {@inheritDoc}
    ///
    /// Prepared settings belong to the old layout and are discarded after a successful replacement.
    @Override
    public void setBaseDirectory(Path baseDirectory) {
        super.setBaseDirectory(baseDirectory);
        preparedInstanceSettings.clear();
    }

    /// {@inheritDoc}
    ///
    /// Accepts an existing root only when this repository reserved the id while the root was absent.
    @Override
    protected boolean mayClaimDraftInstanceRoot(GameInstanceID instanceId, Path instanceRoot) {
        PreparedInstanceSettings prepared = preparedInstanceSettings.get(instanceId);
        if (prepared != null) {
            return prepared.instanceRoot().equals(instanceRoot) && prepared.rootWasAbsent();
        }
        return super.mayClaimDraftInstanceRoot(instanceId, instanceRoot);
    }

    /// {@inheritDoc}
    ///
    /// Writes settings prepared by [#ensureIsolatedRunningDirectory(GameInstanceID)] only after the
    /// draft owns the instance root.
    @Override
    protected void initializeDraftInstanceRoot(GameInstanceID instanceId, Path instanceRoot) throws IOException {
        PreparedInstanceSettings prepared = preparedInstanceSettings.get(instanceId);
        if (prepared == null) {
            return;
        }
        if (!prepared.instanceRoot().equals(instanceRoot) || !prepared.rootWasAbsent()) {
            throw new IOException("Prepared instance root cannot be claimed: " + instanceRoot);
        }

        writeInstanceGameSettings(instanceId, prepared.settings());
        preparedInstanceSettings.remove(instanceId, prepared);
    }

    @Override
    protected HMCLGameInstance createInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            @Nullable Path manifestFile) {
        return new HMCLGameInstance(snapshot, id, manifest, manifestFile);
    }

    @Override
    public HMCLGameRepositorySnapshot getSnapshot() {
        return (HMCLGameRepositorySnapshot) super.getSnapshot();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReadOnlyObjectProperty<HMCLGameRepositorySnapshot> snapshotProperty() {
        return (ReadOnlyObjectProperty<HMCLGameRepositorySnapshot>) super.snapshotProperty();
    }

    @Override
    public HMCLGameRepositoryLayout getLayout() {
        return (HMCLGameRepositoryLayout) super.getLayout();
    }

    @Override
    public HMCLGameInstance getInstance(GameInstanceID id) throws NoSuchGameInstanceException {
        return (HMCLGameInstance) super.getInstance(id);
    }

    /// Returns the indexed instance for the given id, or `null` when it is not loaded.
    ///
    /// @param id the instance id
    /// @return the instance, or `null` when absent
    public @Nullable HMCLGameInstance findInstance(GameInstanceID id) {
        return (HMCLGameInstance) getSnapshot().findInstance(id);
    }

    /// {@inheritDoc}
    @Override
    protected void flushPendingInstanceWrites() throws IOException {
        try {
            FileSaver.waitForAllSaves();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for pending instance writes", e);
        }
    }

    /// Returns the persistent game directory for this repository.
    public GameDirectory getGameDirectory() {
        return gameDirectory;
    }

    /// Returns the selected instance resolved from the current repository snapshot.
    ///
    /// The property is `null` when the persisted selection is absent or is not registered in the
    /// current snapshot. Publishing a new snapshot replaces the value with that snapshot's member,
    /// even when the selected ID is unchanged.
    ///
    /// @return the read-only selected-instance property
    public ReadOnlyObjectProperty<@Nullable HMCLGameInstance> selectedInstanceProperty() {
        return selectedInstance.getReadOnlyProperty();
    }

    /// Returns the selected instance from the current repository snapshot.
    ///
    /// @return the selected instance, or `null` when no registered instance is selected
    public @Nullable HMCLGameInstance getSelectedInstance() {
        return selectedInstance.get();
    }

    /// Persists an instance as this repository's current selection.
    ///
    /// A stale snapshot member from this repository is accepted; the observable property resolves
    /// its ID against the current snapshot.
    ///
    /// @param instance the instance to select, or `null` to clear the selection
    /// @throws IllegalArgumentException if `instance` belongs to another repository
    public void setSelectedInstance(@Nullable HMCLGameInstance instance) {
        if (instance != null && instance.getRepository() != this) {
            throw new IllegalArgumentException("Selected instance belongs to another repository");
        }
        settings().setSelectedInstance(gameDirectory.getId(), instance != null ? instance.getId() : null);
    }

    /// Restores a valid selected instance after repository instances are loaded.
    ///
    /// If the persisted ID is not registered, the first indexed instance is selected. If the
    /// repository is empty, the persisted selection is cleared.
    public void refreshSelectedInstance() {
        @Nullable GameInstanceID persistedId = selectedInstanceId.get();
        @Nullable HMCLGameInstance refreshedInstance = persistedId != null ? findInstance(persistedId) : null;
        if (refreshedInstance == null) {
            refreshedInstance = getSnapshot().getInstances().stream().findFirst().orElse(null);
        }

        @Nullable GameInstanceID refreshedId = refreshedInstance != null ? refreshedInstance.getId() : null;
        if (!Objects.equals(persistedId, refreshedId)) {
            setSelectedInstance(refreshedInstance);
        }
    }

    /// Resolves the persisted selected ID from the current repository snapshot.
    ///
    /// @return the current snapshot member, or `null` when the selected ID is absent or unregistered
    private @Nullable HMCLGameInstance resolveSelectedInstance() {
        @Nullable GameInstanceID instanceId = selectedInstanceId.get();
        return instanceId != null ? findInstance(instanceId) : null;
    }

    /// Returns a dependency manager using the currently selected download provider.
    public DefaultDependencyManager getDependency() {
        return getDependency(DownloadProviders.getDownloadProvider());
    }

    /// Returns a dependency manager using the given download provider.
    public DefaultDependencyManager getDependency(DownloadProvider downloadProvider) {
        return new DefaultDependencyManager(this, downloadProvider, HMCLCacheRepository.REPOSITORY);
    }

    /// Resolves the run directory from modpack state and local settings.
    ///
    /// @param instanceId   the instance id
    /// @param modpack      whether the instance is an HMCL modpack (`modpack.cfg` present)
    /// @param localSetting the instance-local settings, or `null` when absent
    /// @return the run directory
    Path computeRunDirectory(
            GameInstanceID instanceId,
            boolean modpack,
            GameSettings.@Nullable Instance localSetting) {
        Path instanceRoot = getLayout().getInstanceRoot(instanceId);
        if (modpack) {
            return instanceRoot;
        }

        boolean useInstanceRunningDirectory =
                localSetting != null
                        && localSetting.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY);

        String runningDirectory = selectedRunningDirectory(localSetting, useInstanceRunningDirectory);
        if (StringUtils.isBlank(runningDirectory)) {
            return useInstanceRunningDirectory ? instanceRoot : getBaseDirectory();
        }

        try {
            return Path.of(runningDirectory);
        } catch (Exception ignored) {
            return instanceRoot;
        }
    }

    /// {@inheritDoc}
    ///
    /// Resolves HMCL isolation and modpack rules directly from files and settings so an unpublished
    /// installation does not require a [GameInstance].
    @Override
    public Path getRunDirectoryForInstallation(GameInstanceID instanceId) {
        @Nullable PreparedInstanceSettings prepared = preparedInstanceSettings.get(instanceId);
        return computeRunDirectory(
                instanceId,
                Files.exists(getLayout().getModpackConfigurationFile(instanceId)),
                prepared != null ? prepared.settings() : getInstanceGameSettings(instanceId));
    }

    private String selectedRunningDirectory(
            GameSettings.@Nullable Instance localSetting,
            boolean useInstanceRunningDirectory) {
        if (useInstanceRunningDirectory) {
            if (localSetting == null) {
                return "";
            }
            return Objects.requireNonNullElse(localSetting.runningDirectoryProperty().getValue(), "");
        }

        GameSettings.Preset parent = getParentGameSettings(localSetting);
        return Objects.requireNonNullElse(parent.runningDirectoryProperty().getValue(), "");
    }

    /// Reads instance-local settings from disk without requiring a registered snapshot member.
    ///
    /// Used for install-time path resolution and migration before the instance is indexed. Does not
    /// publish a snapshot entry.
    ///
    /// @param instanceId the instance id
    /// @return the loaded settings, or `null` when none can be loaded
    private GameSettings.@Nullable Instance peekInstanceGameSettings(GameInstanceID instanceId) {
        Path file = getLayout().getInstanceGameSettingsFile(instanceId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return LauncherSettings.SETTINGS_GSON
                    .fromJson(Files.readString(file), GameSettings.Instance.class);
        } catch (Exception e) {
            LOG.warning("Failed to peek instance game settings: " + file, e);
            return null;
        }
    }

    /// Writes instance-local settings to disk for an id that may not yet be registered.
    ///
    /// @param instanceId the instance id
    /// @param setting    the settings to write
    /// @throws IOException if the file cannot be written
    private void writeInstanceGameSettings(GameInstanceID instanceId, GameSettings.Instance setting)
            throws IOException {
        Path file = getLayout().getInstanceGameSettingsFile(instanceId).toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
        FileUtils.saveSafely(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
    }

    /// Ensures the instance uses an isolated running directory under its instance root.
    ///
    /// When the instance is already registered, settings are updated through
    /// [HMCLGameInstance]. Otherwise the settings are retained in memory and transferred to the
    /// draft that creates the instance, so the draft owns every file created for the installation.
    ///
    /// @param instanceId the instance id
    public void ensureIsolatedRunningDirectory(GameInstanceID instanceId) {
        HMCLGameInstance instance = findInstance(instanceId);
        if (instance != null) {
            if (instance.isSettingsReadOnly()) {
                return;
            }
            GameSettings.Instance setting = instance.getSettingsOrCreate();
            if (setting != null
                    && setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY)) {
                instance.saveSettings();
            }
            return;
        }

        Path instanceRoot = getLayout().getInstanceRoot(instanceId).toAbsolutePath().normalize();
        PreparedInstanceSettings prepared = preparedInstanceSettings.get(instanceId);
        GameSettings.Instance setting = prepared != null
                ? prepared.settings()
                : peekInstanceGameSettings(instanceId);
        if (setting == null) {
            setting = new GameSettings.Instance();
        }
        setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        preparedInstanceSettings.put(
                instanceId,
                new PreparedInstanceSettings(
                        setting,
                        instanceRoot,
                        prepared != null ? prepared.rootWasAbsent() : Files.notExists(instanceRoot)));
    }

    public Stream<HMCLGameInstance> getDisplayInstances() {
        return getSnapshot().getInstances().stream()
                .filter(it -> !it.getManifest().isHidden())
                .sorted(Comparator.comparing(DefaultGameInstance::getVersion)
                        .thenComparing(instance -> Lang.requireNonNullElse(instance.getLaunchManifest().releaseTime(), Instant.EPOCH))
                        .thenComparing(instance -> VersionNumber.asVersion(instance.getId().id())));
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
        clean(getInstance(instanceId).getRunDirectory());
    }

    /// Duplicates an instance and publishes the copy through one exclusive repository draft.
    ///
    /// The destination remains unpublished until all selected instance and run-directory files have
    /// been copied. Failure aborts the draft and removes the destination instance root.
    ///
    /// @param srcId     the source instance id
    /// @param dstId     the destination instance id
    /// @param copySaves whether saved worlds should be copied
    /// @throws IOException if the destination exists or any file cannot be copied or committed
    public void duplicateInstance(GameInstanceID srcId, GameInstanceID dstId, boolean copySaves) throws IOException {
        Path srcDir = getLayout().getInstanceRoot(srcId);
        Path dstDir = getLayout().getInstanceRoot(dstId);

        GameInstanceManifest fromManifest = getInstanceManifest(srcId);
        GameInstanceManifest destinationManifest = fromManifest.withId(dstId).withJar(dstId);

        List<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(srcId.id() + ".jar");
        blackList.add(srcId.id() + ".json");
        if (!copySaves)
            blackList.add("saves");

        try (DefaultGameRepositoryDraft draft = openDraft()) {
            draft.put(destinationManifest);

            Files.createDirectories(dstDir);
            FileUtils.copyDirectory(srcDir, dstDir, path -> Modpack.acceptFile(path, blackList, null));

            Path fromJar = srcDir.resolve(srcId.id() + ".jar");
            Path toJar = dstDir.resolve(dstId.id() + ".jar");
            if (Files.exists(fromJar)) {
                Files.copy(fromJar, toJar);
            }

            Path srcGameDir = getInstance(srcId).getRunDirectory();
            boolean copyOriginalGameDir;
            try {
                copyOriginalGameDir = !Files.isSameFile(srcGameDir, srcDir);
            } catch (IOException e) {
                copyOriginalGameDir = true;
            }

            GameSettings.Instance newGameSettings = getInstance(srcId).copySettings();
            newGameSettings.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
            newGameSettings.runningDirectoryProperty().setValue("");
            writeInstanceGameSettings(dstId, newGameSettings);

            Path dstGameDir = computeRunDirectory(dstId, false, newGameSettings);
            if (copyOriginalGameDir) {
                FileUtils.copyDirectory(
                        srcGameDir,
                        dstGameDir,
                        path -> Modpack.acceptFile(path, blackList, null));
            }

            draft.commit();
        }
    }

    /// Returns instance-local settings for a registered instance ID, creating empty settings when
    /// the settings file is absent and writable.
    ///
    /// Code that already has an [HMCLGameInstance] should use
    /// [HMCLGameInstance#getSettingsOrCreate()] instead.
    ///
    /// @param instanceId the registered instance ID
    /// @return the settings, or `null` when the instance is not registered or settings are unavailable
    public @Nullable GameSettings.Instance getInstanceGameSettingsOrCreate(GameInstanceID instanceId) {
        HMCLGameInstance instance = findInstance(instanceId);
        if (instance == null) {
            return null;
        }
        return instance.getSettingsOrCreate();
    }

    /// Returns instance-local settings for a registered instance ID.
    ///
    /// When the instance is not yet indexed, settings are loaded from disk (including lazy legacy
    /// migration) without publishing a snapshot entry. Callers that already have an
    /// [HMCLGameInstance] should use [HMCLGameInstance#getSettings()] instead.
    ///
    /// @param instanceId the instance ID
    /// @return the settings, or `null` when no local settings exist
    public @Nullable GameSettings.Instance getInstanceGameSettings(GameInstanceID instanceId) {
        HMCLGameInstance instance = findInstance(instanceId);
        if (instance != null) {
            return instance.getSettings();
        }
        return loadOrMigrateInstanceGameSettings(instanceId);
    }

    /// Returns the explicit parent preset of the instance, falling back to the default preset.
    public GameSettings.Preset getParentGameSettings(@Nullable GameSettings.Instance instance) {
        @Nullable GameSettingsPresetID parent = instance != null ? instance.parentProperty().getValue() : null;
        GameSettings.Preset parentSetting = SettingsManager.getGameSettings(parent);
        return parentSetting != null ? parentSetting : SettingsManager.getDefaultGameSettingsPresetOrCreate();
    }

    /// Resolves effective settings for a registered instance ID.
    ///
    /// Instance-oriented callers should use [HMCLGameInstance#getEffectiveSettings()] instead.
    ///
    /// @param instanceId the registered instance ID
    /// @return the effective settings
    /// @throws NoSuchGameInstanceException if the instance is not registered
    public GameSettings.Effective getEffectiveGameSettings(GameInstanceID instanceId) {
        return getInstance(instanceId).getEffectiveSettings();
    }

    /// Returns whether a new instance should use an isolated running directory under the default isolation settings.
    public boolean shouldIsolateNewInstance(boolean modded) {
        GameSettings.Preset preset = getParentGameSettings(null);
        DefaultIsolationType type = Lang.requireNonNullElse(preset.defaultIsolationTypeProperty().getValue(), DefaultIsolationType.MODDED);
        return switch (type) {
            case NEVER -> false;
            case ALWAYS -> true;
            case MODDED -> modded;
        };
    }

    /// Applies default isolation to a new instance before its manifest is saved.
    ///
    /// Writes the isolation flag to the instance settings file so a later
    /// [HMCLGameInstance#getRunDirectory] returns the instance root.
    public void applyDefaultIsolationSettingForNewInstance(GameInstanceID instanceId, boolean modded) {
        if (!shouldIsolateNewInstance(modded)) {
            return;
        }
        ensureIsolatedRunningDirectory(instanceId);
    }

    /// Loads settings from disk for an unregistered id, running legacy migration when needed.
    private GameSettings.@Nullable Instance loadOrMigrateInstanceGameSettings(GameInstanceID instanceId) {
        Path file = getLayout().getInstanceGameSettingsFile(instanceId);
        if (Files.isRegularFile(file)) {
            return peekInstanceGameSettings(instanceId);
        }

        @Nullable GameSettingsPresetID legacyParent = getGameDirectory().getLegacyGameSettings();
        if (SettingsManager.getGameSettings(legacyParent) == null) {
            legacyParent = null;
        }

        LegacyGameSettingsMigrator.InstanceMigrationResult migrationResult =
                LegacyGameSettingsMigrator.migrateInstanceGameSettings(this, instanceId, legacyParent);
        if (migrationResult == null) {
            return null;
        }

        try {
            writeInstanceGameSettings(instanceId, migrationResult.setting());
            migrationResult.saveReceipt();
        } catch (IOException e) {
            LOG.warning("Failed to save migrated instance game settings for " + instanceId, e);
        }
        return migrationResult.setting();
    }

    // These instance ids are forbidden because they may conflict with modpack configuration filenames
    private static final Set<String> FORBIDDEN_INSTANCE_IDS = Set.of("modpack", "minecraftinstance", "manifest");

    public static boolean isValidInstanceId(String id) {
        if (!GameInstanceID.isValid(id))
            return false;

        if (FORBIDDEN_INSTANCE_IDS.contains(id))
            return false;

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS &&
                FORBIDDEN_INSTANCE_IDS.contains(id.toLowerCase(Locale.ROOT)))
            return false;

        return FileUtils.isNameValidForJar(id);
    }

    /**
     * Returns true if the given instance id conflicts with an existing instance.
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
            for (HMCLGameInstance instance : getSnapshot().getInstances()) {
                if (instance.getId().toString().equalsIgnoreCase(id.toString())) {
                    return true;
                }
            }
            return false;
        } else {
            return hasInstance(id);
        }
    }

    public static long getAutoAllocatedMemory(long available, Platform platform) {
        long usable = available - 512 * 1024 * 1024; // Reserve 512 MiB memory for off-heap memory and HMCL itself
        if (usable <= 0) {
            return available;
        }

        final long threshold = 8L * 1024 * 1024 * 1024; // 8 GiB
        final long suggested;
        if (usable <= threshold)
            suggested = (long) (usable * 0.8);
        else
            suggested = Math.min(
                    (long) (threshold * 0.8 + (usable - threshold) * 0.2),
                    16L * 1024 * 1024 * 1024);
        return platform.getBits() == Bits.BIT_32
                ? Math.min(suggested, 768 * 1024 * 1024) // https://github.com/HMCL-dev/HMCL/issues/6638
                : suggested;
    }

    /// Records settings prepared for an instance that has not entered a repository draft yet.
    ///
    /// @param settings      the settings to materialize after the draft claims the root
    /// @param instanceRoot  the normalized root reserved for the instance
    /// @param rootWasAbsent whether the root was absent when the reservation was made
    private record PreparedInstanceSettings(
            GameSettings.Instance settings,
            Path instanceRoot,
            boolean rootWasAbsent) {
    }
}
