// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.modrinth;

import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.modpack.ModpackCompletionException;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.StageId;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Completes missing files for an installed Modrinth modpack.
@NotNullByDefault
public class ModrinthCompletionTask extends Task<@Nullable Void> {

    /// The dependency manager used to download remote files.
    private final DefaultDependencyManager dependency;

    /// The fixed registered instance completed by this task.
    private final DefaultGameInstance instance;

    /// The mod manager associated with [#instance].
    private final ModManager modManager;

    /// The manifest supplied by the caller or loaded from disk, if available.
    private @Nullable ModrinthManifest manifest;

    /// Keys of optional files the user chose not to install; `null` means install all.
    private @Nullable Set<String> excludedFiles;

    /// Creates a task that completes the installed Modrinth modpack.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    public ModrinthCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        this(dependencyManager, instance, null, null);
    }

    /// Creates a task that completes the installed Modrinth modpack using an optional manifest.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    /// @param manifest          the Modrinth manifest, or `null` to read it from disk
    /// @param excludedFiles     keys of optional files the user chose not to install; `null` means install all.
    ///                          When non-null, must not contain `null` elements.
    public ModrinthCompletionTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            @Nullable ModrinthManifest manifest,
            @Nullable Set<String> excludedFiles) {
        dependencyManager.validateGameInstance(instance);
        this.dependency = dependencyManager;
        this.instance = instance;
        this.modManager = instance.getModManager();
        this.manifest = manifest;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);

        if (manifest == null)
            try {
                Path root = instance.getInstanceRoot();
                Path manifestFile = root.resolve("modrinth.index.json");
                if (Files.exists(manifestFile))
                    this.manifest = JsonUtils.fromJsonFile(manifestFile, ModrinthManifest.class);
                Path excludedFile = root.resolve("excluded.json");
                if (Files.exists(excludedFile)) {
                    this.excludedFiles = Set.copyOf(Objects.requireNonNull(
                            JsonUtils.fromJsonFile(excludedFile, JsonUtils.listTypeOf(String.class))));
                } else {
                    this.excludedFiles = null;
                }
            } catch (Exception e) {
                LOG.warning("Unable to read Modrinth modpack manifest.json", e);
            }

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the stage for modpack downloads.
    @Override
    public StageId getStage() {
        return InstallStages.MODPACK_DOWNLOAD;
    }

    /// Resolves missing files and waits for all requested downloads to finish.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        if (manifest == null)
            return null;

        List<Task<?>> dependencies = new ArrayList<>();
        Path runDirectory = FileUtils.toAbsolute(instance.getRunDirectory());
        Path modsDirectory = runDirectory.resolve("mods");

        if (excludedFiles != null) {
            JsonUtils.writeToJsonFile(
                    instance.getInstanceRoot().resolve("excluded.json"),
                    List.copyOf(excludedFiles));
        }

        for (ModrinthManifest.File file : manifest.getFiles()) {
            if (file.env() != null && file.env().getOrDefault("client", "required").equals("unsupported"))
                continue;
            if (file.downloads().isEmpty())
                continue;
            if (excludedFiles != null && excludedFiles.contains(file.key()))
                continue;

            Path filePath = runDirectory.resolve(file.path()).toAbsolutePath().normalize();
            if (!filePath.startsWith(runDirectory))
                throw new IOException("Unsecure path: " + file.path());

            if (Files.exists(filePath))
                continue;
            if (modsDirectory.equals(filePath.getParent()) && this.modManager.hasSimpleMod(FileUtils.getName(filePath)))
                continue;

            DownloadProvider downloadProvider = dependency.getDownloadProvider();
            var task = new FileDownloadTask(
                    downloadProvider.getDownloadCandidates(file.downloads()),
                    filePath);
            task.setCacheRepository(dependency.getCacheRepository());
            task.setCaching(true);
            dependencies.add(task.withCounter());
        }

        if (!dependencies.isEmpty()) {
            context.addStageItems(dependencies.size());
        }

        try {
            context.runAll(dependencies);
        } catch (InterruptedException | CancellationException cancelled) {
            throw cancelled;
        } catch (Exception failure) {
            ModpackCompletionException incomplete = new ModpackCompletionException();
            incomplete.addSuppressed(failure);
            throw incomplete;
        }
        return null;
    }
}
