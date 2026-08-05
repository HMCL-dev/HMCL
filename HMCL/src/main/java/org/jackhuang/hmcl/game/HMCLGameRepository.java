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

import com.google.gson.JsonParseException;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.DefaultIsolationType;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameWindowType;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.ProxyType;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.jackhuang.hmcl.util.Lang;
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
    /// The persistent game directory for this repository.
    private final GameDirectory gameDirectory;

    /// The selected instance ID persisted for this repository's game directory.
    private final ObjectBinding<@Nullable GameInstanceID> selectedInstanceId;

    /// The selected instance resolved from the current repository snapshot.
    private final ReadOnlyObjectWrapper<@Nullable HMCLGameInstance> selectedInstance;

    /// Publishes notifications after an instance icon changes.
    public final EventManager<Event> onInstanceIconChanged = new EventManager<>();

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
    /// Provisional placeholders are excluded.
    ///
    /// @param id the instance id
    /// @return the instance, or `null` when absent
    public @Nullable HMCLGameInstance findInstance(GameInstanceID id) {
        return (HMCLGameInstance) getSnapshot().findInstance(id);
    }

    /// Returns the instance that owns local state for the given id.
    ///
    /// When the id is already present in the current snapshot (including provisional placeholders),
    /// that instance is returned. Otherwise a provisional [HMCLGameInstance] is created and published
    /// in a new snapshot until it is promoted by a real manifest or the snapshot is replaced by
    /// refresh.
    ///
    /// @param instanceId the instance id
    /// @return the instance used to manage settings and install-time state for the id
    private HMCLGameInstance resolveInstance(GameInstanceID instanceId) {
        DefaultGameInstance existing = findSnapshotInstance(instanceId);
        if (existing != null) {
            return (HMCLGameInstance) existing;
        }

        HMCLGameRepositorySnapshot newSnapshot = getSnapshot().clone();
        HMCLGameInstance provisional = HMCLGameInstance.provisional(newSnapshot, instanceId);
        newSnapshot.put(provisional);
        publishSnapshot(newSnapshot);
        return provisional;
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

    @Override
    public Path getRunDirectory(GameInstanceID instanceId) {
        return resolveInstance(instanceId).getRunDirectory();
    }

    public Stream<HMCLGameInstance> getDisplayInstances() {
        return getSnapshot().getInstances().stream()
                .filter(it -> !it.getManifest().isHidden())
                .sorted(Comparator.comparing((HMCLGameInstance instance) -> Lang.requireNonNullElse(instance.getLaunchManifest().releaseTime(), Instant.EPOCH))
                        .thenComparing(DefaultGameInstance::getVersion)
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
        clean(getRunDirectory(instanceId));
    }

    public void duplicateInstance(GameInstanceID srcId, GameInstanceID dstId, boolean copySaves) throws IOException {
        Path srcDir = getLayout().getInstanceRoot(srcId);
        Path dstDir = getLayout().getInstanceRoot(dstId);

        GameInstanceManifest fromManifest = getInstanceManifest(srcId);

        List<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(srcId.id() + ".jar");
        blackList.add(srcId.id() + ".json");
        if (!copySaves)
            blackList.add("saves");

        if (Files.exists(dstDir)) throw new IOException("Instance exists");

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

        JsonUtils.writeToJsonFile(toJson, fromManifest.withId(dstId).withJar(dstId));

        boolean copyOriginalGameDir;
        try {
            copyOriginalGameDir = !Files.isSameFile(getRunDirectory(srcId), getLayout().getInstanceRoot(srcId));
        } catch (IOException e) {
            copyOriginalGameDir = true;
        }

        Path srcGameDir = getRunDirectory(srcId);

        GameSettings.Instance newGameSettings = resolveInstance(srcId).copySettings();
        newGameSettings.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        newGameSettings.runningDirectoryProperty().setValue("");
        HMCLGameInstance dstInstance = resolveInstance(dstId);
        dstInstance.initSettings(newGameSettings, true);
        dstInstance.saveSettingsSync();

        Path dstGameDir = getRunDirectory(dstId);

        if (copyOriginalGameDir)
            FileUtils.copyDirectory(srcGameDir, dstGameDir, path -> Modpack.acceptFile(path, blackList, null));
    }

    /// Returns instance-local settings for an instance ID, creating empty settings when the instance
    /// is registered and its settings file is absent and writable.
    ///
    /// This ID-based entry point is retained for installation before an instance has entered the
    /// registered snapshot. Code that already has an [HMCLGameInstance] should use
    /// [HMCLGameInstance#getSettingsOrCreate()] instead.
    ///
    /// @param instanceId the indexed or pending instance ID
    /// @return the settings, or `null` when no settings exist and none can be created
    public @Nullable GameSettings.Instance getInstanceGameSettingsOrCreate(GameInstanceID instanceId) {
        HMCLGameInstance instance = resolveInstance(instanceId);
        @Nullable GameSettings.Instance setting = instance.getSettings();
        if (setting == null && hasInstance(instanceId)) {
            setting = instance.createSettings();
        }
        return setting;
    }

    /// Returns instance-local settings for an indexed or provisional instance ID.
    ///
    /// This ID-based entry point is retained for installation and legacy migration before an
    /// instance has entered the registered snapshot. Code that already has an [HMCLGameInstance]
    /// should use [HMCLGameInstance#getSettings()] instead.
    ///
    /// @param instanceId the indexed or pending instance ID
    /// @return the settings, or `null` when no local settings exist
    public @Nullable GameSettings.Instance getInstanceGameSettings(GameInstanceID instanceId) {
        return resolveInstance(instanceId).getSettings();
    }

    /// Returns the explicit parent preset of the instance, falling back to the default preset.
    public GameSettings.Preset getParentGameSettings(@Nullable GameSettings.Instance instance) {
        @Nullable GameSettingsPresetID parent = instance != null ? instance.parentProperty().getValue() : null;
        GameSettings.Preset parentSetting = SettingsManager.getGameSettings(parent);
        return parentSetting != null ? parentSetting : SettingsManager.getDefaultGameSettingsPresetOrCreate();
    }

    /// Resolves effective settings for an indexed or provisional instance ID.
    ///
    /// This ID-based entry point is retained for launch construction and installation code that has
    /// not yet obtained an [HMCLGameInstance]. Instance-oriented callers should use
    /// [HMCLGameInstance#getEffectiveSettings()] instead.
    ///
    /// @param instanceId the indexed or pending instance ID
    /// @return the effective settings
    public GameSettings.Effective getEffectiveGameSettings(GameInstanceID instanceId) {
        return resolveInstance(instanceId).getEffectiveSettings();
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
    public void applyDefaultIsolationSettingForNewInstance(GameInstanceID instanceId, boolean modded) {
        HMCLGameInstance instance = resolveInstance(instanceId);
        if (!shouldIsolateNewInstance(modded) || instance.isSettingsReadOnly()) {
            return;
        }

        GameSettings.Instance setting = instance.getSettings();
        if (setting == null) {
            setting = instance.initSettings(new GameSettings.Instance(), true);
        }
        if (setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY)) {
            instance.saveSettings();
        }
    }

    /// Marks the instance as a modpack for run-directory resolution during installation.
    ///
    /// @param instanceId the instance id
    public void markInstanceAsModpack(GameInstanceID instanceId) {
        resolveInstance(instanceId).markAsModpack();
    }

    /// Clears the install-time modpack mark for the instance.
    ///
    /// @param instanceId the instance id
    public void undoMark(GameInstanceID instanceId) {
        DefaultGameInstance existing = findSnapshotInstance(instanceId);
        if (existing != null) {
            ((HMCLGameInstance) existing).unmarkAsModpack();
        }
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

    public static long getAutoAllocatedMemory(long available) {
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
        return suggested;
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
