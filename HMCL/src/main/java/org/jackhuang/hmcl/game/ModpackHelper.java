// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.Metadata;
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
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
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

/// Utilities for reading, installing, and applying modpack-specific game settings.
@NotNullByDefault
public final class ModpackHelper {
    private ModpackHelper() {
    }

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

    public static ModpackConfiguration<?> readModpackConfiguration(Path file) throws IOException {
        try {
            return JsonUtils.fromJsonFile(file, ModpackConfiguration.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed modpack configuration");
        }
    }

    public static Task<?> getInstallTask(HMCLGameRepository repository, ServerModpackManifest manifest, GameInstanceID instanceId, Modpack modpack) {
        ExceptionalRunnable<?> success = () -> {
            repository.refresh();
            repository.getInstance(instanceId).enableIsolation();
        };

        ExceptionalConsumer<Exception, ?> failure = ex -> {
            if (ex instanceof ModpackCompletionException && !(ex.getCause() instanceof FileNotFoundException)) {
                success.run();
                // This is tolerable and we will not delete the game
            }
        };

        return new ServerModpackRemoteInstallTask(repository.getDependency(), manifest, instanceId)
                .whenComplete(Schedulers.defaultScheduler(), success, failure)
                .withStage("hmcl.modpack")
                .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
    }

    public static boolean isExternalGameNameConflicts(String name) {
        return Files.exists(Paths.get("externalgames").resolve(name));
    }

    /// Extracts a manually created modpack and selects its new external game directory.
    public static Task<?> getInstallManuallyCreatedModpackTask(Path zipFile, String name) {
        if (isExternalGameNameConflicts(name)) {
            throw new IllegalArgumentException("name existing");
        }

        return new ManuallyCreatedModpackInstallTask(zipFile, name)
                .thenAcceptAsync(Schedulers.javafx(), location -> {
                    GameDirectory newGameDirectory = new GameDirectory(
                            GameDirectoryManager.newGameDirectoryId(),
                            LocalizedText.plain(name),
                            PortablePath.fromPath(location));
                    GameDirectoryManager.addLocalGameDirectory(newGameDirectory);
                    GameDirectoryManager.setSelectedGameDirectory(newGameDirectory);
                })
                .withStage("hmcl.modpack")
                .withStagesHints("hmcl.modpack");
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
        ExceptionalRunnable<?> success = () -> {
            repository.refresh();
            repository.getInstance(instanceId).enableIsolation();
        };

        ExceptionalConsumer<Exception, ?> failure = ex -> {
            if (ex instanceof ModpackCompletionException && !(ex.getCause() instanceof FileNotFoundException)) {
                success.run();
                // This is tolerable and we will not delete the game
            }
        };

        if (modpack.getManifest() instanceof MultiMCInstanceConfiguration)
            return modpack.getInstallTask(repository.getDependency(), zipFile, instanceId, iconUrl, excludedFiles)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .thenComposeAsync(createMultiMCPostInstallTask(repository, (MultiMCInstanceConfiguration) modpack.getManifest(), instanceId))
                    .withStage("hmcl.modpack")
                    .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
        else if (modpack.getManifest() instanceof McbbsModpackManifest)
            return modpack.getInstallTask(repository.getDependency(), zipFile, instanceId, iconUrl, excludedFiles)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .thenComposeAsync(createMcbbsPostInstallTask(repository, (McbbsModpackManifest) modpack.getManifest(), instanceId))
                    .withStage("hmcl.modpack")
                    .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
        else
            return modpack.getInstallTask(repository.getDependency(), zipFile, instanceId, iconUrl, excludedFiles)
                    .whenComplete(Schedulers.defaultScheduler(), success, failure)
                    .withStage("hmcl.modpack")
                    .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
    }

    /// Resolves the current instance after a refresh while keeping the update in its original directory.
    private static HMCLGameInstance resolveUpdateTarget(HMCLGameRepository repository,
                                                        HMCLGameRepositoryLayout layout, GameInstanceID instanceId) {
        HMCLGameInstance instance = repository.getInstance(instanceId);
        if (instance.getLayout() != layout)
            throw new IllegalStateException("Game directory has changed: " + layout.getBaseDirectory());
        return instance;
    }

    /// Creates a task that updates the server modpack published under `instanceId` in the
    /// original game directory at execution time.
    ///
    /// @throws UnsupportedModpackException if the supplied configuration is not for a server modpack
    public static Task<Void> getUpdateTask(HMCLGameRepository repository, ServerModpackManifest manifest, GameInstanceID instanceId, ModpackConfiguration<?> configuration) throws UnsupportedModpackException {
        if (!ServerModpackRemoteInstallTask.MODPACK_TYPE.equals(configuration.getType()))
            throw new UnsupportedModpackException();

        HMCLGameRepositoryLayout layout = repository.getLayout();
        return Task.composeAsync(() -> {
            HMCLGameInstance instance = resolveUpdateTarget(repository, layout, instanceId);
            return new ModpackUpdateTask(instance, new ServerModpackRemoteInstallTask(repository.getDependency(), manifest, instance));
        }).thenComposeAsync(repository.refreshAsync())
                .withStage("hmcl.modpack")
                .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
    }

    /// Creates an archive update task with all optional files enabled.
    public static Task<?> getUpdateTask(HMCLGameRepository repository, Path zipFile, GameInstanceID instanceId, ModpackConfiguration<?> configuration) throws UnsupportedModpackException, ManuallyCreatedModpackException, MismatchedModpackTypeException {
        return getUpdateTask(repository, zipFile, instanceId, configuration, null);
    }

    /// Reads the archive manifest and creates a task that updates the instance published under
    /// `instanceId` in the original game directory when the task runs. The optional-file
    /// selection is copied at construction.
    ///
    /// @param repository      the target repository
    /// @param zipFile         the modpack archive
    /// @param instanceId      the instance to update
    /// @param configuration   the existing modpack configuration
    /// @param excludedFiles keys of optional files the user chose not to install; `null` means install all.
    ///                        When non-null, must not contain `null` elements.
    /// @return the update task
    /// @throws UnsupportedModpackException if the archive or installed provider is unrecognized
    /// @throws ManuallyCreatedModpackException if the archive contains a manually created pack
    /// @throws MismatchedModpackTypeException if the archive and the installed configuration use different providers
    public static Task<?> getUpdateTask(
            HMCLGameRepository repository,
            Path zipFile,
            GameInstanceID instanceId,
            ModpackConfiguration<?> configuration,
            @Nullable Set<String> excludedFiles)
            throws UnsupportedModpackException, ManuallyCreatedModpackException, MismatchedModpackTypeException {
        Modpack modpack = ModpackHelper.readModpackManifest(zipFile);
        @Nullable ModpackProvider provider = getProviderByType(configuration.getType());
        if (provider == null) {
            throw new UnsupportedModpackException();
        }
        ModpackProvider archiveProvider = modpack.getManifest().getProvider();
        if (archiveProvider != provider)
            throw new MismatchedModpackTypeException(provider.getName(), archiveProvider.getName());

        @Nullable @Unmodifiable Set<String> selection = excludedFiles == null ? null : Set.copyOf(excludedFiles);
        HMCLGameRepositoryLayout layout = repository.getLayout();
        Task<?> update = Task.composeAsync(() -> provider.createUpdateTask(
                repository.getDependency(), resolveUpdateTarget(repository, layout, instanceId), zipFile, modpack, selection));
        if (modpack.getManifest() instanceof MultiMCInstanceConfiguration manifest)
            update = update.thenComposeAsync(() -> createMultiMCPostUpdateTask(repository, manifest, instanceId));
        return update.thenComposeAsync(repository.refreshAsync())
                .withStage("hmcl.modpack")
                .withStagesHints(new Task.StagesHint("hmcl.modpack"), new Task.StagesHint("hmcl.modpack.download", List.of("hmcl.install.assets", "hmcl.install.libraries")));
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
