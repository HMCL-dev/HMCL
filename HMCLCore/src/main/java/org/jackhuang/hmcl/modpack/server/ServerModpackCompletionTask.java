// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
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

    /// The installed configuration supplied by the caller or loaded from disk.
    private @Nullable ModpackConfiguration<ServerModpackManifest> manifest;

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

        if (manifest == null) {
            try {
                if (Files.exists(configurationFile)) {
                    this.manifest = JsonUtils.fromJsonFile(configurationFile, ModpackConfiguration.typeOf(ServerModpackManifest.class));
                }
            } catch (Exception e) {
                LOG.warning("Unable to read Server modpack manifest.json", e);
            }
        } else {
            this.manifest = manifest;
        }

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
        if (installed == null || StringUtils.isBlank(installed.getManifest().getFileApi())) return null;

        String json = context.run(new GetTask(WebURL.parse(installed.getManifest().getFileApi() + "/server-manifest.json")));
        ServerModpackManifest remoteManifest;
        try {
            remoteManifest = JsonUtils.fromNonNullJson(json, ServerModpackManifest.class);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }

        List<Task<?>> dependencies = new ArrayList<>();
        boolean updateComponents = !toMap(installed.getManifest().getAddons()).equals(toMap(remoteManifest.getAddons()));

        Path rootPath = instance.getInstanceRoot().toAbsolutePath().normalize();
        Map<String, ModpackConfiguration.FileInformation> files = installed.getManifest().getFiles().stream()
                .collect(Collectors.toMap(ModpackConfiguration.FileInformation::getPath,
                        Function.identity()));

        Set<String> remoteFiles = remoteManifest.getFiles().stream().map(ModpackConfiguration.FileInformation::getPath)
                .collect(Collectors.toSet());

        Path runDirectory = instance.getRunDirectory().toAbsolutePath().normalize();
        Path modsDirectory = runDirectory.resolve("mods");

        // for files in new modpack
        for (ModpackConfiguration.FileInformation file : remoteManifest.getFiles()) {
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
            } else if (!files.containsKey(file.getPath())) {
                // If old modpack does not have this entry, download it
                download = true;
            } else if (!Files.exists(actualPath)) {
                // If both old and new modpacks have this entry, but the file is missing...
                // Re-download it since network problem may cause file missing
                download = true;
            } else {
                // If user modified this entry file, we will not replace this file since this modified file is that user expects.
                String fileHash = DigestUtils.digestToString("SHA-1", actualPath);
                String oldHash = files.get(file.getPath()).getHash();
                download = !Objects.equals(oldHash, file.getHash()) && Objects.equals(oldHash, fileHash);
            }

            if (download) {
                dependencies.add(new FileDownloadTask(
                        remoteManifest.getFileApi() + "/overrides/" + file.getPath(),
                        actualPath,
                        new FileDownloadTask.IntegrityCheck("SHA-1", file.getHash()))
                        .withCounter());
            }
        }

        // If old modpack have this entry, and new modpack deleted it. Delete this file.
        for (ModpackConfiguration.FileInformation file : installed.getManifest().getFiles()) {
            Path actualPath = rootPath.resolve(file.getPath());
            if (Files.exists(actualPath) && !remoteFiles.contains(file.getPath()))
                Files.deleteIfExists(actualPath);
        }

        context.addStageItems(dependencies.size());

        try {
            context.runAll(dependencies);
            writeConfiguration(installed, remoteManifest);
            if (updateComponents) {
                Modpack.installWithGameBuilder(dependencyManager, instance.getId(), instance, false, builder -> {
                    for (ServerModpackManifest.Addon addon : remoteManifest.getAddons()) {
                        @Nullable GameComponentType componentType = GameComponentType.fromPatchId(addon.getId());
                        if (componentType != null)
                            builder.component(componentType, addon.getVersion());
                    }
                    context.run(builder.buildAsync());
                });
            }
        } catch (Exception | Error failure) {
            // Track the downloaded files while leaving an incomplete component update eligible for retry.
            ServerModpackManifest partial = updateComponents
                    ? new ServerModpackManifest(remoteManifest.getName(), remoteManifest.getAuthor(), remoteManifest.getVersion(),
                            remoteManifest.getDescription(), remoteManifest.getFileApi(), remoteManifest.getFiles(), installed.getManifest().getAddons())
                    : remoteManifest;
            try {
                writeConfiguration(installed, partial);
            } catch (Exception | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return null;
    }

    /// Records the remote file list alongside the component versions known to the installation.
    private void writeConfiguration(ModpackConfiguration<ServerModpackManifest> installed, ServerModpackManifest remote) throws IOException {
        Files.createDirectories(configurationFile.getParent());
        JsonUtils.writeToJsonFile(configurationFile, new ModpackConfiguration<>(remote, installed.getType(),
                installed.getName(), installed.getVersion(), remote.getFiles()));
    }
}
