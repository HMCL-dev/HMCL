// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackConfiguration.FileInformation;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Synchronizes an installed server modpack with its remote manifest.
@NotNullByDefault
public class ServerModpackCompletionTask extends Task<@Nullable Void> {

    /// The dependency manager used for downloads and game-component updates.
    private final DefaultDependencyManager dependencyManager;

    /// The fixed registered instance completed by this task.
    private final DefaultGameInstance instance;

    /// The fixed configuration-file path for [#instance].
    private final Path configurationFile;

    /// The supplied configuration, or `null` to load the installed configuration during execution.
    private final @Nullable ModpackConfiguration<ServerModpackManifest> manifest;

    /// Creates a task that loads the installed configuration from disk.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    public ServerModpackCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        this(dependencyManager, instance, null);
    }

    /// Creates a task using an optional preloaded configuration.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    /// @param manifest          the installed configuration, or `null` to read it from disk
    public ServerModpackCompletionTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            @Nullable ModpackConfiguration<ServerModpackManifest> manifest) {
        dependencyManager.validateGameInstance(instance);
        this.dependencyManager = dependencyManager;
        this.instance = instance;
        this.configurationFile = instance.getModpackConfigurationFile();
        this.manifest = manifest;

        setSignificance(TaskSignificance.MODERATE);
    }

    private Map<String, String> toMap(Collection<ServerModpackManifest.Addon> addons) {
        return addons.stream().collect(Collectors.toMap(ServerModpackManifest.Addon::getId, ServerModpackManifest.Addon::getVersion));
    }

    /// Returns the stage for modpack downloads.
    @Override
    public TaskStage getStage() {
        return InstallStages.MODPACK_DOWNLOAD;
    }

    /// Synchronizes game components and files against the remote manifest.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        @Nullable ModpackConfiguration<ServerModpackManifest> installed = manifest;
        if (installed == null) {
            try {
                if (Files.exists(configurationFile))
                    installed = JsonUtils.fromJsonFile(configurationFile, ModpackConfiguration.typeOf(ServerModpackManifest.class));
            } catch (Exception e) {
                LOG.warning("Unable to read Server modpack manifest.json", e);
            }
        }
        if (installed == null || StringUtils.isBlank(installed.getManifest().getFileApi())) return null;

        String json = context.run(new GetTask(WebURL.parse(installed.getManifest().getFileApi() + "/server-manifest.json")));
        ServerModpackManifest remoteManifest;
        try {
            remoteManifest = JsonUtils.fromNonNullJson(json, ServerModpackManifest.class);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }

        if (!toMap(installed.getManifest().getAddons()).equals(toMap(remoteManifest.getAddons()))) {
            try (DefaultGameBuilder builder = dependencyManager.newGameBuilder(instance)) {
                for (ServerModpackManifest.Addon addon : remoteManifest.getAddons()) {
                    @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
                    if (componentType != null)
                        builder.component(componentType, addon.getVersion());
                }
                syncFiles(context, installed, remoteManifest, false);
                builder.setModpackConfiguration(configurationFor(installed, remoteManifest));
                builder.buildAsTaskResult(context);
            }
        } else {
            syncFiles(context, installed, remoteManifest, true);
        }
        return null;
    }

    /// Synchronizes overrides and records their installed hashes for the next update.
    /// When a component build is pending, its draft publishes the remote component versions.
    private void syncFiles(TaskExecutionContext context, ModpackConfiguration<ServerModpackManifest> installed,
                           ServerModpackManifest remoteManifest, boolean finalPublication) throws Exception {
        Files.createDirectories(configurationFile.getParent());
        Map<FileInformation, Task<?>> downloads = new LinkedHashMap<>();
        Path rootPath = instance.getInstanceRoot().toAbsolutePath().normalize();
        Map<String, FileInformation> previousFiles = installed.getManifest().getFiles().stream()
                .collect(Collectors.toMap(FileInformation::getPath, Function.identity()));
        Set<String> remotePaths = new HashSet<>();

        Path runDirectory = instance.getRunDirectory().toAbsolutePath().normalize();
        Path modsDirectory = runDirectory.resolve("mods");

        // for files in new modpack
        for (FileInformation file : remoteManifest.getFiles()) {
            remotePaths.add(file.getPath());
            @Nullable FileInformation previous = previousFiles.get(file.getPath());
            Path actualPath = rootPath.resolve(file.getPath()).toAbsolutePath().normalize();
            String fileName = actualPath.getFileName().toString();

            if (!actualPath.startsWith(rootPath)) {
                throw new IOException("Unsecure path: " + file.getPath());
            }

            boolean download;

            boolean isModDisabled = modsDirectory.equals(actualPath.getParent()) &&
                    (Files.exists(actualPath.resolveSibling(fileName + LocalAddonManager.DISABLED_EXTENSION)) ||
                            Files.exists(actualPath.resolveSibling(fileName + LocalAddonManager.OLD_EXTENSION)));

            if (isModDisabled) {
                download = false;
            } else if (previous == null) {
                // If old modpack does not have this entry, download it
                download = true;
            } else if (!Files.exists(actualPath)) {
                // If both old and new modpacks have this entry, but the file is missing...
                // Re-download it since network problem may cause file missing
                download = true;
            } else {
                // If user modified this entry file, we will not replace this file since this modified file is that user expects.
                String fileHash = DigestUtils.digestToString("SHA-1", actualPath);
                String oldHash = previous.getHash();
                download = !file.getHash().equalsIgnoreCase(oldHash) && fileHash.equalsIgnoreCase(oldHash);
            }

            if (download) {
                downloads.put(file, new FileDownloadTask(
                        remoteManifest.getFileApi() + "/overrides/" + file.getPath(),
                        actualPath,
                        new FileDownloadTask.IntegrityCheck("SHA-1", file.getHash()))
                        .withCounter());
            }
        }

        Set<String> removed = new HashSet<>();
        try {
            for (FileInformation previous : installed.getManifest().getFiles()) {
                if (!remotePaths.contains(previous.getPath())) {
                    Modpack.removeUnmodifiedOverride(rootPath, previous.getPath(), previous.getHash(), context);
                    removed.add(previous.getPath());
                }
            }
            context.addStageItems(downloads.size());
            context.runAll(downloads.values(), FetchTask.getDownloadExecutorConcurrency());
            if (finalPublication) {
                context.commit();
                configurationFor(installed, remoteManifest).writeTo(configurationFile);
                return;
            }
        } catch (Exception | Error failure) {
            // All admitted downloads have stopped. Record only files that actually reached the destination.
            boolean interrupted = Thread.interrupted();
            try {
                saveFileProgress(installed, downloads, removed, rootPath, failure);
            } catch (Exception | Error writeFailure) {
                failure.addSuppressed(writeFailure);
            } finally {
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
            throw failure;
        }

        saveFileProgress(installed, downloads, removed, rootPath, null);
    }

    /// Updates the two installed file lists without publishing pending component or pack versions.
    private void saveFileProgress(ModpackConfiguration<ServerModpackManifest> installed,
                                  Map<FileInformation, Task<?>> downloads, Set<String> removed,
                                  Path rootPath, @Nullable Throwable failure) throws IOException {
        Map<String, FileInformation> written = new LinkedHashMap<>();
        for (var entry : downloads.entrySet()) {
            FileInformation file = entry.getKey();
            if (failure == null || wasDownloaded(entry.getValue(), rootPath.resolve(file.getPath()), file.getHash(), failure))
                written.put(file.getPath(), file);
        }
        if (written.isEmpty() && removed.isEmpty())
            return;

        ServerModpackManifest previous = installed.getManifest();
        ServerModpackManifest synced = previous.withFiles(updatedFiles(previous.getFiles(), written, removed));
        new ModpackConfiguration<>(synced, installed.getType(), installed.getName(), installed.getVersion(),
                updatedFiles(installed.getOverrides(), written, removed)).writeTo(configurationFile);
    }

    /// Applies file replacements and removals, retaining entries specific to the supplied list.
    private static @Unmodifiable List<FileInformation> updatedFiles(List<FileInformation> previous,
                                                                    Map<String, FileInformation> written, Set<String> removed) {
        Map<String, FileInformation> result = new LinkedHashMap<>();
        for (FileInformation file : previous) {
            if (!removed.contains(file.getPath()))
                result.put(file.getPath(), file);
        }
        result.putAll(written);
        return List.copyOf(result.values());
    }

    /// Returns whether a download installed the expected content, including before a late cancellation.
    private static boolean wasDownloaded(Task<?> download, Path path, String hash, Throwable failure) {
        if (download.getState() == Task.State.SUCCEEDED)
            return true;
        if (download.getState() == Task.State.NEW)
            return false;
        try {
            return Files.isRegularFile(path) && hash.equalsIgnoreCase(DigestUtils.digestToString("SHA-1", path));
        } catch (IOException e) {
            failure.addSuppressed(e);
            return false;
        }
    }

    /// Pairs the manifest and its override hashes with the installed pack's metadata.
    private static ModpackConfiguration<ServerModpackManifest> configurationFor(
            ModpackConfiguration<ServerModpackManifest> installed, ServerModpackManifest manifest) {
        return new ModpackConfiguration<>(manifest, installed.getType(), installed.getName(), installed.getVersion(), manifest.getFiles());
    }
}
