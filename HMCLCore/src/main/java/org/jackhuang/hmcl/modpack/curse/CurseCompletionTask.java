// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.modpack.ModpackCompletionException;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskProgress;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Completes missing files for an installed CurseForge modpack.
@NotNullByDefault
public final class CurseCompletionTask extends Task<@Nullable Void> {

    /// The dependency manager used to resolve and download remote files.
    private final DefaultDependencyManager dependency;

    /// The fixed registered instance completed by this task.
    private final DefaultGameInstance instance;

    /// The mod manager associated with [#instance].
    private final ModManager modManager;

    /// The supplied manifest, or `null` to load the installed manifest during execution.
    private final @Nullable CurseManifest manifest;

    /// Keys of optional files the user chose not to install; `null` means install all.
    private final @Nullable @Unmodifiable Set<String> excludedFiles;

    /// Creates a task that completes the installed CurseForge modpack.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    public CurseCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        this(dependencyManager, instance, null, null);
    }

    /// Creates a task that completes the installed CurseForge modpack using an optional manifest.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    /// @param manifest          the CurseForge manifest, or `null` to read it from disk
    /// @param excludedFiles     keys of optional files the user chose not to install; `null` means install all.
    ///                          When non-null, must not contain `null` elements.
    public CurseCompletionTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            @Nullable CurseManifest manifest,
            @Nullable Set<String> excludedFiles) {
        dependencyManager.validateGameInstance(instance);
        this.dependency = dependencyManager;
        this.instance = instance;
        this.modManager = instance.getModManager();
        this.manifest = manifest;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the stage for modpack downloads.
    @Override
    public TaskStage getStage() {
        return InstallStages.MODPACK_DOWNLOAD;
    }

    /// Resolves missing files and waits for all requested downloads to finish.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        @Nullable CurseManifest manifest = this.manifest;
        @Nullable @Unmodifiable Set<String> excludedFiles = this.excludedFiles;
        if (manifest == null) {
            try {
                Path root = instance.getInstanceRoot();
                Path manifestFile = root.resolve("manifest.json");
                if (Files.exists(manifestFile))
                    manifest = JsonUtils.fromJsonFile(manifestFile, CurseManifest.class);
                Path excludedFile = root.resolve("excluded.json");
                if (Files.exists(excludedFile)) {
                    excludedFiles = Set.copyOf(Objects.requireNonNull(
                            JsonUtils.fromJsonFile(excludedFile, JsonUtils.listTypeOf(String.class))));
                } else {
                    excludedFiles = null;
                }
            } catch (Exception e) {
                LOG.warning("Unable to read CurseForge modpack manifest.json", e);
            }
        }
        if (manifest != null)
            complete(context, manifest, excludedFiles);
        return null;
    }

    /// Resolves the selected files and records the resulting manifest in the instance.
    private void complete(TaskExecutionContext context, CurseManifest manifest, @Nullable @Unmodifiable Set<String> excludedFiles) throws Exception {
        AtomicBoolean notFound = new AtomicBoolean();
        AtomicBoolean allNameKnown = new AtomicBoolean(true);
        TaskProgress progress = context.progress();
        AtomicInteger finished = new AtomicInteger();
        Path root = instance.getInstanceRoot();

        // Because in China, Curse is too difficult to visit,
        // if failed, ignore it and retry next time.
        CurseManifest newManifest = manifest.setFiles(context.call(Schedulers.io(), () ->
                manifest.files().parallelStream()
                        .map(file -> {
                            progress.update(finished.incrementAndGet(), manifest.files().size());
                            if (StringUtils.isBlank(file.fileName()) || file.url() == null) {
                                try {
                                    RemoteAddon.File remoteFile = CurseForgeRemoteAddonRepository.getInstance().getAddonFile(Integer.toString(file.projectID()), Integer.toString(file.fileID()));
                                    return file
                                            .withFileName(remoteFile.filename())
                                            .withURL(remoteFile.url())
                                            .withHashes(remoteFile.hashes());
                                } catch (FileNotFoundException fof) {
                                    LOG.warning("Could not query api.curseforge.com for deleted mods: " + file.projectID() + ", " + file.fileID(), fof);
                                    notFound.set(true);
                                    return file;
                                } catch (IOException | JsonParseException e) {
                                    LOG.warning("Unable to fetch the file name projectID=" + file.projectID() + ", fileID=" + file.fileID(), e);
                                    allNameKnown.set(false);
                                    return file;
                                }
                            } else {
                                return file;
                            }
                        })
                        .toList()));
        JsonUtils.writeToJsonFile(root.resolve("manifest.json"), newManifest);
        if (excludedFiles != null) {
            JsonUtils.writeToJsonFile(
                    root.resolve("excluded.json"),
                    List.copyOf(excludedFiles));
        }

        Path versionRoot = instance.getInstanceRoot();
        Path resourcePacksRoot = versionRoot.resolve("resourcepacks");
        Path shaderPacksRoot = versionRoot.resolve("shaderpacks");
        finished.set(0);
        @Unmodifiable List<Task<?>> dependencies = context.call(Schedulers.io(), () -> newManifest.files()
                .stream().parallel()
                .filter(f -> f.fileName() != null)
                .filter(f -> excludedFiles == null || !excludedFiles.contains(f.key()))
                .flatMap(f -> {
                    try {
                        Path path = guessFilePath(f, dependency.getDownloadProvider(), resourcePacksRoot, shaderPacksRoot);
                        if (path == null) {
                            return Stream.empty();
                        }

                        var task = new FileDownloadTask(f.url(), path, f.getIntegrityCheck());
                        task.setCacheRepository(dependency.getCacheRepository());
                        task.setCaching(true);
                        return Stream.<Task<?>>of(task.withCounter());
                    } catch (IOException e) {
                        LOG.warning("Could not query api.curseforge.com for mod: " + f.projectID() + ", " + f.fileID(), e);
                        return Stream.empty(); // Ignore this file.
                    } finally {
                        progress.update(finished.incrementAndGet(), newManifest.files().size());
                    }
                })
                .toList());

        if (!dependencies.isEmpty()) {
            context.addStageItems(dependencies.size());
        }

        try {
            context.runAll(dependencies, FetchTask.getDownloadExecutorConcurrency());
        } catch (InterruptedException | CancellationException cancelled) {
            throw cancelled;
        } catch (Exception failure) {
            if (notFound.get())
                throw new ModpackCompletionException(new FileNotFoundException());
            ModpackCompletionException incomplete = new ModpackCompletionException();
            incomplete.addSuppressed(failure);
            throw incomplete;
        }
        if (notFound.get())
            throw new ModpackCompletionException(new FileNotFoundException());
        if (!allNameKnown.get())
            throw new ModpackCompletionException();
    }

    /// Returns the destination for a missing CurseForge file based on its project class.
    ///
    /// @param file              the manifest file
    /// @param downloadProvider  the download provider used for CurseForge requests
    /// @param resourcePacksRoot the resource-pack directory
    /// @param shaderPacksRoot   the shader-pack directory
    /// @return the destination, or `null` when the file already exists
    /// @throws IOException if CurseForge metadata cannot be read
    private @Nullable Path guessFilePath(CurseManifestFile file, DownloadProvider downloadProvider, Path resourcePacksRoot, Path shaderPacksRoot) throws IOException {
        RemoteAddon mod = CurseForgeRemoteAddonRepository.getInstance().getAddonById(downloadProvider, Integer.toString(file.projectID()));
        int classID = ((CurseForgeRemoteAddonRepository.CurseAddon) mod.data()).classId();
        String fileName = file.fileName();
        return switch (classID) {
            case 12,       // Resource pack
                 6945 -> { // Data pack
                Path path = resourcePacksRoot.resolve(fileName);
                yield Files.exists(path) ? null : path;
            }
            case 6552 -> { // Shader pack
                Path path = shaderPacksRoot.resolve(fileName);
                yield Files.exists(path) ? null : path;
            }
            default -> {
                if (modManager.hasSimpleMod(fileName)) {
                    yield null;
                }
                yield modManager.getSimpleModPath(fileName);
            }
        };
    }
}
