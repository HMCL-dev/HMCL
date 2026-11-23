// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.mcbbs;

import com.google.gson.JsonParseException;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.modpack.ModpackCompletionException;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.curse.CurseMetaMod;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Completes and updates files for an installed MCBBS modpack.
@NotNullByDefault
public class McbbsModpackCompletionTask extends Task<@Nullable Void> {

    /// The dependency manager used to resolve and download remote files.
    private final DefaultDependencyManager dependency;

    /// The fixed registered instance completed by this task.
    private final DefaultGameInstance instance;

    /// The mod manager associated with [#instance].
    private final ModManager modManager;

    /// The fixed configuration-file path for [#instance].
    private final Path configurationFile;

    /// The supplied configuration, or `null` to read it during execution.
    private final @Nullable ModpackConfiguration<McbbsModpackManifest> configuration;

    /// Creates a task that loads the modpack configuration from disk.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    public McbbsModpackCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        this(dependencyManager, instance, null);
    }

    /// Creates a task using an optional preloaded modpack configuration.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    /// @param configuration     the configuration, or `null` to read it from disk
    public McbbsModpackCompletionTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            @Nullable ModpackConfiguration<McbbsModpackManifest> configuration) {
        dependencyManager.validateGameInstance(instance);
        this.dependency = dependencyManager;
        this.instance = instance;
        this.modManager = instance.getModManager();
        this.configurationFile = instance.getModpackConfigurationFile();
        this.configuration = configuration;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the stage for modpack downloads.
    @Override
    public TaskStage getStage() {
        return InstallStages.MODPACK_DOWNLOAD;
    }

    /// Updates the manifest and completes downloads for its CurseForge entries.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        @Nullable ModpackConfiguration<McbbsModpackManifest> installed = configuration;
        if (installed == null) {
            try {
                installed = JsonUtils.fromNonNullJson(Files.readString(configurationFile), ModpackConfiguration.typeOf(McbbsModpackManifest.class));
            } catch (IOException | JsonParseException e) {
                throw new IOException("Malformed modpack configuration", e);
            }
        }
        @Nullable McbbsModpackManifest manifest = installed.getManifest();
        if (manifest != null) {
            if (StringUtils.isNotBlank(manifest.getFileApi()))
                manifest = updateManifest(context, installed, manifest);
            completeCurseFiles(context, installed, manifest);
        }
        return null;
    }

    /// Fetches the remote manifest, applies its file changes and records the updated manifest.
    private McbbsModpackManifest updateManifest(TaskExecutionContext context,
                                                ModpackConfiguration<McbbsModpackManifest> configuration,
                                                McbbsModpackManifest manifest) throws Exception {
        String remoteManifestJson = context.run(new GetTask(WebURL.parse(manifest.getFileApi() + "/manifest.json")));
        McbbsModpackManifest remoteManifest;
        try {
            remoteManifest = JsonUtils.fromNonNullJson(remoteManifestJson, McbbsModpackManifest.class);
        } catch (JsonParseException e) {
            throw new IOException("Unable to parse server manifest.json from " + manifest.getFileApi(), e);
        }

        Path rootPath = instance.getInstanceRoot();
        Files.createDirectories(rootPath);

        Map<McbbsModpackManifest.File, McbbsModpackManifest.File> localFiles = manifest.getFiles().stream().collect(Collectors.toMap(Function.identity(), Function.identity()));

        // for files in new modpack
        List<McbbsModpackManifest.File> newFiles = new ArrayList<>(remoteManifest.getFiles().size());
        List<Task<?>> tasks = new ArrayList<>();
        for (McbbsModpackManifest.File file : remoteManifest.getFiles()) {
            @Nullable Path actualPath = getFilePath(file);
            @Nullable McbbsModpackManifest.File oldFile = localFiles.remove(file);
            boolean download = false;
            if (oldFile == null) {
                // If old modpack does not have this entry, download it
                download = true;
            } else if (actualPath != null) {
                if (!Files.exists(actualPath)) {
                    // If both old and new modpacks have this entry, but the file is missing...
                    // Re-download it since network problem may cause file missing
                    download = true;
                } else if (getFileHash(file) != null) {
                    // If user modified this entry file, we will not replace this file since this modified file is what user expects.
                    // Or we have downloaded latest file in previous completion task, this time we have no need to download it again.
                    String fileHash = DigestUtils.digestToString("SHA-1", actualPath);
                    @Nullable String oldHash = getFileHash(oldFile);
                    @Nullable String newHash = getFileHash(file);
                    if (oldHash == null) {
                        // We don't know whether the file is modified or not, just update it.
                        download = true;
                    } else if (!Objects.equals(fileHash, newHash)) {
                        if (file.isForce()) {
                            // this file is not allowed to be modified, required by modpack author.
                            download = true;
                        } else if (Objects.equals(oldHash, fileHash)) {
                            download = true;
                        }
                    }
                }
            }

            if (download) {
                @Nullable Task<?> downloadTask = downloadFile(remoteManifest, file);
                if (downloadTask != null) tasks.add(downloadTask);
            }

            newFiles.add(mergeFile(oldFile, file));
        }

        // If old modpack have this entry, and new modpack deleted it. Delete this file.
        // for-loop above removes still existing file in localFiles. Remaining elements
        // are files removed by next modpack version.
        // Notice that this loop will also remove Curse mods.
        for (McbbsModpackManifest.File file : localFiles.keySet()) {
            @Nullable Path actualPath = getFilePath(file);
            if (actualPath != null && Files.exists(actualPath))
                Files.deleteIfExists(actualPath);
        }

        McbbsModpackManifest updated = remoteManifest.setFiles(newFiles);
        context.runAll(tasks, FetchTask.getDownloadExecutorConcurrency());
        JsonUtils.writeToJsonFile(configurationFile,
                new ModpackConfiguration<>(updated, configuration.getType(), updated.getName(), updated.getVersion(),
                        updated.getFiles().stream()
                                .flatMap(file -> file instanceof McbbsModpackManifest.AddonFile
                                        ? Stream.of((McbbsModpackManifest.AddonFile) file)
                                        : Stream.empty())
                                .map(file -> new ModpackConfiguration.FileInformation(file.getPath(), file.getHash()))
                                .collect(Collectors.toList())));
        return updated;
    }

    /// Resolves names for CurseForge entries and downloads missing files concurrently.
    private void completeCurseFiles(TaskExecutionContext context,
                                    ModpackConfiguration<McbbsModpackManifest> configuration,
                                    McbbsModpackManifest manifest) throws Exception {
        AtomicBoolean allNameKnown = new AtomicBoolean(true);
        TaskProgress progress = context.progress();
        AtomicInteger finished = new AtomicInteger();
        AtomicBoolean notFound = new AtomicBoolean();
        List<Task<?>> dependencies = new ArrayList<>();
        // Because in China, Curse is too difficult to visit,
        // if failed, ignore it and retry next time.
        McbbsModpackManifest newManifest = manifest.setFiles(context.call(Schedulers.io(), () ->
                manifest.getFiles().parallelStream()
                        .map(rawFile -> {
                            progress.update(finished.incrementAndGet(), manifest.getFiles().size());
                            if (rawFile instanceof McbbsModpackManifest.CurseFile) {
                                McbbsModpackManifest.CurseFile file = (McbbsModpackManifest.CurseFile) rawFile;
                                if (StringUtils.isBlank(file.getFileName())) {
                                    try {
                                        return file.withFileName(NetworkUtils.detectFileName(WebURL.parse(file.getUrl())));
                                    } catch (IOException e) {
                                        try {
                                            String result = NetworkUtils.doGet(String.format("https://cursemeta.dries007.net/%d/%d.json", file.getProjectID(), file.getFileID()));
                                            CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
                                            return file.withFileName(mod.fileNameOnDisk()).withURL(mod.downloadURL());
                                        } catch (FileNotFoundException fof) {
                                            LOG.warning("Could not query cursemeta for deleted mods: " + file.getUrl(), fof);
                                            notFound.set(true);
                                            return file;
                                        } catch (IOException | JsonParseException e2) {
                                            try {
                                                String result = NetworkUtils.doGet(String.format("https://addons-ecs.forgesvc.net/api/v2/addon/%d/file/%d", file.getProjectID(), file.getFileID()));
                                                CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
                                                return file.withFileName(mod.fileName()).withURL(mod.downloadURL());
                                            } catch (FileNotFoundException fof) {
                                                LOG.warning("Could not query forgesvc for deleted mods: " + file.getUrl(), fof);
                                                notFound.set(true);
                                                return file;
                                            } catch (IOException | JsonParseException e3) {
                                                LOG.warning("Unable to fetch the file name of URL: " + file.getUrl(), e);
                                                LOG.warning("Unable to fetch the file name of URL: " + file.getUrl(), e2);
                                                LOG.warning("Unable to fetch the file name of URL: " + file.getUrl(), e3);
                                                allNameKnown.set(false);
                                                return file;
                                            }
                                        }
                                    }
                                } else {
                                    return file;
                                }
                            } else {
                                return rawFile;
                            }
                        })
                        .collect(Collectors.toList())));

        configuration = configuration.setManifest(newManifest);
        JsonUtils.writeToJsonFile(configurationFile, configuration);

        for (McbbsModpackManifest.File file : newManifest.getFiles())
            if (file instanceof McbbsModpackManifest.CurseFile) {
                McbbsModpackManifest.CurseFile curseFile = (McbbsModpackManifest.CurseFile) file;
                if (StringUtils.isNotBlank(curseFile.getFileName())) {
                    if (!modManager.hasSimpleMod(curseFile.getFileName())) {
                        var task = new FileDownloadTask(curseFile.getUrl(), modManager.getSimpleModPath(curseFile.getFileName()));
                        task.setCacheRepository(dependency.getCacheRepository());
                        task.setCaching(true);
                        dependencies.add(task.withCounter());
                    }
                }
            }

        if (!dependencies.isEmpty()) {
            context.addStageItems(dependencies.size());
        }

        context.runAll(dependencies, FetchTask.getDownloadExecutorConcurrency());
        if (notFound.get())
            throw new ModpackCompletionException(new FileNotFoundException());
        if (!allNameKnown.get())
            throw new ModpackCompletionException();
    }

    private @Nullable Path getFilePath(McbbsModpackManifest.File file) {
        if (file instanceof McbbsModpackManifest.AddonFile) {
            return instance.getRunDirectory().resolve(((McbbsModpackManifest.AddonFile) file).getPath());
        } else if (file instanceof McbbsModpackManifest.CurseFile) {
            String fileName = ((McbbsModpackManifest.CurseFile) file).getFileName();
            if (fileName == null) return null;
            return modManager.getSimpleModPath(fileName);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private @Nullable String getFileHash(McbbsModpackManifest.File file) {
        if (file instanceof McbbsModpackManifest.AddonFile) {
            return ((McbbsModpackManifest.AddonFile) file).getHash();
        } else {
            return null;
        }
    }

    private @Nullable Task<?> downloadFile(McbbsModpackManifest remoteManifest, McbbsModpackManifest.File file) throws IOException {
        if (file instanceof McbbsModpackManifest.AddonFile) {
            McbbsModpackManifest.AddonFile addonFile = (McbbsModpackManifest.AddonFile) file;
            return new FileDownloadTask(
                    remoteManifest.getFileApi() + "/overrides/" + addonFile.getPath(),
                    modManager.getSimpleModPath(addonFile.getPath()),
                    addonFile.getHash() != null ? new FileDownloadTask.IntegrityCheck("SHA-1", addonFile.getHash()) : null);
        } else if (file instanceof McbbsModpackManifest.CurseFile) {
            // we download it later.
            return null;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @NotNull
    private McbbsModpackManifest.File mergeFile(@Nullable McbbsModpackManifest.File oldFile, @NotNull McbbsModpackManifest.File newFile) {
        if (newFile instanceof McbbsModpackManifest.AddonFile) {
            return newFile;
        } else if (newFile instanceof McbbsModpackManifest.CurseFile) {
            // Preserves prefetched file names and urls.
            return oldFile != null ? oldFile : newFile;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
