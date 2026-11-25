// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.modpack.*;
import org.jackhuang.hmcl.modpack.curse.CurseModpackProvider;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackManifest;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackProvider;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthModpackProvider;
import org.jackhuang.hmcl.modpack.multimc.MultiMCComponents;
import org.jackhuang.hmcl.modpack.multimc.MultiMCInstanceConfiguration;
import org.jackhuang.hmcl.modpack.multimc.MultiMCModpackProvider;
import org.jackhuang.hmcl.modpack.server.ServerModpackManifest;
import org.jackhuang.hmcl.modpack.server.ServerModpackProvider;
import org.jackhuang.hmcl.modpack.server.ServerModpackRemoteInstallTask;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Utilities for reading, installing, and applying modpack-specific game settings.
@NotNullByDefault
public final class ModpackHelper {
    private ModpackHelper() {
    }

    /// Displays asset and library downloads alongside other modpack downloads.
    private static final @Unmodifiable List<TaskStage.Hint> MODPACK_STAGE_HINTS = List.of(
            new TaskStage.Hint(InstallStages.MODPACK),
            new TaskStage.Hint(InstallStages.MODPACK_DOWNLOAD, List.of(InstallStages.ASSETS, InstallStages.LIBRARIES))
    );

    private static final Map<String, ModpackProvider> providers = mapOf(
            pair(CurseModpackProvider.INSTANCE.getName(), CurseModpackProvider.INSTANCE),
            pair(McbbsModpackProvider.INSTANCE.getName(), McbbsModpackProvider.INSTANCE),
            pair(ModrinthModpackProvider.INSTANCE.getName(), ModrinthModpackProvider.INSTANCE),
            pair(MultiMCModpackProvider.INSTANCE.getName(), MultiMCModpackProvider.INSTANCE),
            pair(ServerModpackProvider.INSTANCE.getName(), ServerModpackProvider.INSTANCE),
            pair(HMCLModpackProvider.INSTANCE.getName(), HMCLModpackProvider.INSTANCE)
    );

    static {
        MultiMCComponents.setImplementation(Metadata.FULL_NAME + " v" + Metadata.VERSION);
    }

    @Nullable
    public static ModpackProvider getProviderByType(String type) {
        return providers.get(type);
    }

    public static boolean isFileModpackByExtension(Path file) {
        String ext = FileUtils.getExtension(file);
        return "zip".equals(ext) || "mrpack".equals(ext);
    }

    public static Modpack readModpackManifest(Path file) throws UnsupportedModpackException, ManuallyCreatedModpackException {
        try (ZipArchiveReader zipFile = CompressingUtils.openZipFile(file)) {
            // Order for trying detecting manifest is necessary here.
            // Do not change to iterating providers.
            for (ModpackProvider provider : new ModpackProvider[]{
                    McbbsModpackProvider.INSTANCE,
                    CurseModpackProvider.INSTANCE,
                    ModrinthModpackProvider.INSTANCE,
                    HMCLModpackProvider.INSTANCE,
                    MultiMCModpackProvider.INSTANCE,
                    ServerModpackProvider.INSTANCE}) {
                try {
                    return provider.readManifest(zipFile, file);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }

        try {
            findMinecraftDirectoryInManuallyCreatedModpack(file.toString(), file);
            throw new ManuallyCreatedModpackException(file);
        } catch (IOException e) {
            // ignore it
        }

        throw new UnsupportedModpackException(file.toString());
    }

    public static String findMinecraftDirectoryInManuallyCreatedModpack(String modpackName, Path zipPath)
            throws IOException, UnsupportedModpackException {

        try (ArchiveFileTree<?, ?> tree = ArchiveFileTree.open(zipPath)) {
            ArchiveFileTree.Dir<?> rootDir = tree.getRoot();

            if (isMinecraftDirectory(rootDir)) {
                return "";
            }


            for (ArchiveFileTree.Dir<?> firstLayer : rootDir.getSubDirs().values()) {
                if (isMinecraftDirectory(firstLayer)) {
                    return firstLayer.getName();
                }

                for (ArchiveFileTree.Dir<?> secondLayer : firstLayer.getSubDirs().values()) {
                    if (isMinecraftDirectory(secondLayer)) {
                        return firstLayer.getName() + "/" + secondLayer.getName();
                    }
                }
            }
        }

        throw new UnsupportedModpackException(modpackName);
    }

    private static boolean isMinecraftDirectory(ArchiveFileTree.Dir<?> dir) {
        return dir.getSubDirs().containsKey("versions") && (dir.isRoot() || ".minecraft".equals(dir.getName()));
    }

    /// Creates a server modpack installation task that refreshes the repository and enables instance isolation.
    public static Task<?> getInstallTask(HMCLGameRepository repository, ServerModpackManifest manifest, GameInstanceID instanceId, Modpack modpack) {
        return new ServerModpackRemoteInstallTask(repository.getDependency(), manifest, instanceId)
                .whenCompleteAsync(Schedulers.io(), (ignored, failure) -> refreshInstalledInstance(repository, instanceId, failure))
                .withStage(InstallStages.MODPACK)
                .withStageHints(MODPACK_STAGE_HINTS);
    }

    /// Refreshes and enables isolation for complete installations and retained incomplete instances.
    private static void refreshInstalledInstance(HMCLGameRepository repository, GameInstanceID instanceId, @Nullable Throwable failure) {
        if (failure == null || failure instanceof ModpackCompletionException) {
            repository.refresh();
            repository.getInstance(instanceId).enableIsolation();
        }
    }

    /// Refreshes the repository after a published update and preserves its completion exception.
    private static Task<@Nullable Void> refreshAfterUpdate(HMCLGameRepository repository, Task<?> update) {
        return update.whenCompleteAsync(Schedulers.io(), (ignored, failure) -> {
            if (failure == null || failure instanceof ModpackCompletionException)
                repository.refresh();
        }).thenApply(ignored -> null);
    }

    public static boolean isExternalGameNameConflicts(String name) {
        return Files.exists(Paths.get("externalgames").resolve(name));
    }

    /// Extracts a manually created modpack and selects its new external game directory.
    public static Task<?> getInstallManuallyCreatedModpackTask(Path zipFile, String name) {
        Path destination = Paths.get("externalgames").resolve(name);
        if (Files.exists(destination)) {
            throw new IllegalArgumentException("name existing");
        }

        return new ManuallyCreatedModpackInstallTask(zipFile, destination, name)
                .withStage(InstallStages.MODPACK)
                .withStageHints(InstallStages.MODPACK);
    }

    public static Task<?> getInstallTask(HMCLGameRepository repository, Path zipFile, GameInstanceID instanceId, Modpack modpack, @Nullable String iconUrl) {
        return getInstallTask(repository, zipFile, instanceId, modpack, iconUrl, null);
    }

    /// Creates an install task that respects optional-file selection.
    ///
    /// @param repository      the target repository
    /// @param zipFile         the modpack archive
    /// @param instanceId      the new instance id
    /// @param modpack         the parsed modpack
    /// @param iconUrl         the optional icon URL, or `null`
    /// @param excludedFiles keys of optional files the user chose not to install; `null` means install all.
    ///                        When non-null, must not contain `null` elements.
    /// @return the install task
    public static Task<?> getInstallTask(
            HMCLGameRepository repository,
            Path zipFile,
            GameInstanceID instanceId,
            Modpack modpack,
            @Nullable String iconUrl,
            @Nullable Set<String> excludedFiles) {
        Task<?> task = modpack.getInstallTask(repository.getDependency(), zipFile, instanceId, iconUrl, excludedFiles)
                .whenCompleteAsync(Schedulers.io(), (ignored, failure) -> refreshInstalledInstance(repository, instanceId, failure));
        if (modpack.getManifest() instanceof MultiMCInstanceConfiguration manifest)
            task = task.thenCompose(createMultiMCPostInstallTask(repository, manifest, instanceId));
        else if (modpack.getManifest() instanceof McbbsModpackManifest manifest)
            task = task.thenCompose(createMcbbsPostInstallTask(repository, manifest, instanceId));
        return task.withStage(InstallStages.MODPACK).withStageHints(MODPACK_STAGE_HINTS);
    }

    /// Raised if the installed instance has no readable modpack configuration for an update.
    public static final class InvalidModpackConfigurationException extends IOException {
        /// Identifies the configuration that could not be read.
        private InvalidModpackConfigurationException(Path file, @Nullable IOException cause) {
            super("Missing or invalid modpack configuration: " + file, cause);
        }
    }

    /// Reads the installed pack type used to select an update provider.
    private static String readInstalledModpackType(HMCLGameInstance instance) throws InvalidModpackConfigurationException {
        try {
            @Nullable ModpackConfiguration<?> configuration = instance.readModpackConfiguration();
            if (configuration != null)
                return configuration.getType();
        } catch (IOException e) {
            throw new InvalidModpackConfigurationException(instance.getModpackConfigurationFile(), e);
        }
        throw new InvalidModpackConfigurationException(instance.getModpackConfigurationFile(), null);
    }

    /// Checks the installed modpack type before updating a server modpack.
    public static Task<@Nullable Void> getUpdateTask(HMCLGameRepository repository, ServerModpackManifest manifest, GameInstanceID instanceId) {
        HMCLGameInstance instance = repository.getInstance(instanceId);
        return Task.composeAsync(Schedulers.io(), () -> {
            if (!ServerModpackRemoteInstallTask.MODPACK_TYPE.equals(readInstalledModpackType(instance)))
                throw new UnsupportedModpackException();
            return refreshAfterUpdate(repository, new ServerModpackRemoteInstallTask(repository.getDependency(), manifest, instance)
                    .setName(i18n("modpack.update")));
        })
                .withStage(InstallStages.MODPACK)
                .withStageHints(MODPACK_STAGE_HINTS);
    }

    /// Checks the selected archive against the installed configuration at execution time.
    /// The optional-file selection is captured when the task is created.
    public static Task<@Nullable Void> getUpdateTask(
            HMCLGameRepository repository,
            Path zipFile,
            GameInstanceID instanceId,
            @Nullable Set<String> excludedFiles) {
        HMCLGameInstance instance = repository.getInstance(instanceId);
        @Nullable @Unmodifiable Set<String> selection = excludedFiles == null ? null : Set.copyOf(excludedFiles);
        return Task.composeAsync(Schedulers.io(), () -> {
            String type = readInstalledModpackType(instance);
            Modpack modpack = readModpackManifest(zipFile);
            @Nullable ModpackProvider provider = getProviderByType(type);
            if (provider == null)
                throw new UnsupportedModpackException();
            Task<?> update = provider.createUpdateTask(repository.getDependency(), instance, zipFile, modpack, selection)
                    .setName(i18n("modpack.update"));
            if (modpack.getManifest() instanceof MultiMCInstanceConfiguration manifest)
                update = update.thenCompose(createMultiMCPostUpdateTask(repository, manifest, instanceId));
            return refreshAfterUpdate(repository, update);
        }).withStage(InstallStages.MODPACK).withStageHints(MODPACK_STAGE_HINTS);
    }

    public static void toGameSettings(MultiMCInstanceConfiguration c, GameSettings.Instance setting) {
        setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);

        if (c.isOverrideJavaLocation()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_JAVA_TYPE);
            setting.getOverrideProperties().add(GameSettings.PROPERTY_CUSTOM_JAVA_PATH);
            setting.javaTypeProperty().setValue(JavaVersionType.CUSTOM);
            setting.customJavaPathProperty().setValue(Objects.requireNonNullElse(c.getJavaPath(), ""));
        }

        if (c.isOverrideMemory()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_AUTO_MEMORY,
                    GameSettings.PROPERTY_PERM_SIZE,
                    GameSettings.PROPERTY_MAX_MEMORY,
                    GameSettings.PROPERTY_MIN_MEMORY
            ));
            setting.permSizeProperty().setValue(Optional.ofNullable(c.getPermGen()).map(Object::toString).orElse(""));
            if (c.getMaxMemory() != null)
                setting.maxMemoryProperty().setValue(c.getMaxMemory());
            setting.minMemoryProperty().setValue(c.getMinMemory());
        }

        if (c.isOverrideCommands()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_COMMAND_WRAPPER,
                    GameSettings.PROPERTY_PRE_LAUNCH_COMMAND
            ));
            setting.commandWrapperProperty().setValue(Objects.requireNonNullElse(c.getWrapperCommand(), ""));
            setting.preLaunchCommandProperty().setValue(Objects.requireNonNullElse(c.getPreLaunchCommand(), ""));
        }

        if (c.isOverrideJavaArgs()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_JVM_OPTIONS);
            setting.jvmOptionsProperty().setValue(Objects.requireNonNullElse(c.getJvmArgs(), ""));
        }

        if (c.isOverrideConsole()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_SHOW_LOGS);
            setting.showLogsProperty().setValue(c.isShowConsole());
        }

        if (c.isOverrideWindow()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_WINDOW_TYPE,
                    GameSettings.PROPERTY_WIDTH,
                    GameSettings.PROPERTY_HEIGHT
            ));
            setting.windowTypeProperty().setValue(c.isFullscreen() ? GameWindowType.FULLSCREEN : GameWindowType.WINDOWED);
            if (c.getWidth() != null)
                setting.widthProperty().setValue(c.getWidth().doubleValue());
            if (c.getHeight() != null)
                setting.heightProperty().setValue(c.getHeight().doubleValue());
        }
    }

    private static void applyCommandAndJvmSettings(MultiMCInstanceConfiguration c, GameSettings.Instance setting) {
        if (c.isOverrideCommands()) {
            setting.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_COMMAND_WRAPPER,
                    GameSettings.PROPERTY_PRE_LAUNCH_COMMAND
            ));
            setting.commandWrapperProperty().setValue(Lang.nonNull(c.getWrapperCommand(), ""));
            setting.preLaunchCommandProperty().setValue(Lang.nonNull(c.getPreLaunchCommand(), ""));
        }

        if (c.isOverrideJavaArgs()) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_JVM_OPTIONS);
            setting.jvmOptionsProperty().setValue(Lang.nonNull(c.getJvmArgs(), ""));
        }
    }

    private static Task<Void> createMultiMCPostUpdateTask(HMCLGameRepository repository, MultiMCInstanceConfiguration manifest, GameInstanceID instanceId) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettingsOrCreate(instanceId));
            ModpackHelper.applyCommandAndJvmSettings(manifest, setting);
        });
    }

    private static Task<Void> createMultiMCPostInstallTask(HMCLGameRepository repository, MultiMCInstanceConfiguration manifest, GameInstanceID instanceId) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettingsOrCreate(instanceId));
            ModpackHelper.toGameSettings(manifest, setting);
        });
    }

    private static Task<Void> createMcbbsPostInstallTask(HMCLGameRepository repository, McbbsModpackManifest manifest, GameInstanceID instanceId) {
        return Task.runAsync(Schedulers.javafx(), () -> {
            GameSettings.Effective effective = repository.getEffectiveGameSettings(instanceId);
            if (manifest.getLaunchInfo().getMinMemory() > effective.getMaxMemory()) {
                GameSettings.Instance setting = Objects.requireNonNull(repository.getInstanceGameSettingsOrCreate(instanceId));
                setting.getOverrideProperties().addAll(List.of(
                        GameSettings.PROPERTY_AUTO_MEMORY,
                        GameSettings.PROPERTY_MIN_MEMORY,
                        GameSettings.PROPERTY_MAX_MEMORY,
                        GameSettings.PROPERTY_PERM_SIZE
                ));
                setting.autoMemoryProperty().setValue(effective.getInheritable(GameSettings::autoMemoryProperty));
                setting.minMemoryProperty().setValue(effective.getInheritable(GameSettings::minMemoryProperty));
                setting.maxMemoryProperty().setValue(manifest.getLaunchInfo().getMinMemory());
                setting.permSizeProperty().setValue(effective.getInheritable(GameSettings::permSizeProperty));
            }
        });
    }
}
