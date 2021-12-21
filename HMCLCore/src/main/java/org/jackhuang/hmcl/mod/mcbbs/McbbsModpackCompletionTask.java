/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.mcbbs;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.curse.CurseCompletionException;
import org.jackhuang.hmcl.mod.curse.CurseMetaMod;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.DigestUtils.digest;
import static org.jackhuang.hmcl.util.Hex.encodeHex;
import static org.jackhuang.hmcl.util.Lang.wrap;
import static org.jackhuang.hmcl.util.Lang.wrapConsumer;

public class McbbsModpackCompletionTask extends CompletableFutureTask<Void> {

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final ModManager modManager;
    private final String version;
    private final File configurationFile;
    private ModpackConfiguration<McbbsModpackManifest> configuration;
    private McbbsModpackManifest manifest;
    private final List<Task<?>> dependencies = new ArrayList<>();

    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);
    private final AtomicInteger finished = new AtomicInteger(0);
    private final AtomicBoolean notFound = new AtomicBoolean(false);

    public McbbsModpackCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        this(dependencyManager, version, null);
    }

    public McbbsModpackCompletionTask(DefaultDependencyManager dependencyManager, String version, ModpackConfiguration<McbbsModpackManifest> configuration) {
        this.dependency = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.modManager = repository.getModManager(version);
        this.version = version;
        this.configurationFile = repository.getModpackConfiguration(version);
        this.configuration = configuration;

        setStage("hmcl.modpack.download");
    }

    @Override
    public CompletableFuture<Void> getFuture(TaskCompletableFuture executor) {
        return breakable(CompletableFuture.runAsync(wrap(() -> {
            if (configuration == null) {
                // Load configuration from disk
                try {
                    configuration = JsonUtils.fromNonNullJson(FileUtils.readText(configurationFile), new TypeToken<ModpackConfiguration<McbbsModpackManifest>>() {
                    }.getType());
                } catch (IOException | JsonParseException e) {
                    throw new IOException("Malformed modpack configuration");
                }
            }
            manifest = configuration.getManifest();
            if (manifest == null) throw new CustomException();
        })).thenComposeAsync(unused -> {
            // we first download latest manifest
            return breakable(CompletableFuture.runAsync(wrap(() -> {
                if (StringUtils.isBlank(manifest.getFileApi())) {
                    // skip this phase
                    throw new CustomException();
                }
            })).thenComposeAsync(wrap(unused1 -> {
                return executor.one(new GetTask(new URL(manifest.getFileApi() + "/manifest.json")));
            })).thenComposeAsync(wrap(remoteManifestJson -> {
                McbbsModpackManifest remoteManifest;
                // We needs to update modpack from online server.
                try {
                    remoteManifest = JsonUtils.fromNonNullJson(remoteManifestJson, McbbsModpackManifest.class);
                } catch (JsonParseException e) {
                    throw new IOException("Unable to parse server manifest.json from " + manifest.getFileApi(), e);
                }

                Path rootPath = repository.getVersionRoot(version).toPath();

                Map<McbbsModpackManifest.File, McbbsModpackManifest.File> localFiles = manifest.getFiles().stream().collect(Collectors.toMap(Function.identity(), Function.identity()));

                // for files in new modpack
                List<McbbsModpackManifest.File> newFiles = new ArrayList<>(remoteManifest.getFiles().size());
                List<Task<?>> tasks = new ArrayList<>();
                for (McbbsModpackManifest.File file : remoteManifest.getFiles()) {
                    Path actualPath = getFilePath(file);
                    McbbsModpackManifest.File oldFile = localFiles.remove(file);
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
                            String fileHash = encodeHex(digest("SHA-1", actualPath));
                            String oldHash = getFileHash(oldFile);
                            String newHash = getFileHash(file);
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
                    } else {
                        // we resolve files with unknown path later.
                    }

                    if (download) {
                        tasks.add(downloadFile(remoteManifest, file));
                    }

                    newFiles.add(mergeFile(oldFile, file));
                }

                // If old modpack have this entry, and new modpack deleted it. Delete this file.
                // for-loop above removes still existing file in localFiles. Remaining elements
                // are files removed by next modpack version.
                // Notice that this loop will also remove Curse mods.
                for (McbbsModpackManifest.File file : localFiles.keySet()) {
                    Path actualPath = getFilePath(file);
                    if (actualPath != null && Files.exists(actualPath))
                        Files.deleteIfExists(actualPath);
                }

                manifest = remoteManifest.setFiles(newFiles);
                return executor.all(tasks.stream().filter(Objects::nonNull).collect(Collectors.toList()));
            })).thenAcceptAsync(wrapConsumer(unused1 -> {
                File manifestFile = repository.getModpackConfiguration(version);
                FileUtils.writeText(manifestFile, JsonUtils.GSON.toJson(
                        new ModpackConfiguration<>(manifest, this.configuration.getType(), this.manifest.getName(), this.manifest.getVersion(),
                                this.manifest.getFiles().stream()
                                        .flatMap(file -> file instanceof McbbsModpackManifest.AddonFile
                                                ? Stream.of((McbbsModpackManifest.AddonFile) file)
                                                : Stream.empty())
                                        .map(file -> new ModpackConfiguration.FileInformation(file.getPath(), file.getHash()))
                                        .collect(Collectors.toList()))));
            })));
        }).thenComposeAsync(unused -> {
            AtomicBoolean allNameKnown = new AtomicBoolean(true);
            AtomicInteger finished = new AtomicInteger(0);
            AtomicBoolean notFound = new AtomicBoolean(false);

            return breakable(CompletableFuture.completedFuture(null)
                    .thenComposeAsync(wrap(unused1 -> {
                        List<Task<?>> dependencies = new ArrayList<>();
                        // Because in China, Curse is too difficult to visit,
                        // if failed, ignore it and retry next time.
                        McbbsModpackManifest newManifest = manifest.setFiles(
                                manifest.getFiles().parallelStream()
                                        .map(rawFile -> {
                                            updateProgress(finished.incrementAndGet(), manifest.getFiles().size());
                                            if (rawFile instanceof McbbsModpackManifest.CurseFile) {
                                                McbbsModpackManifest.CurseFile file = (McbbsModpackManifest.CurseFile) rawFile;
                                                if (StringUtils.isBlank(file.getFileName())) {
                                                    try {
                                                        return file.withFileName(NetworkUtils.detectFileName(file.getUrl()));
                                                    } catch (IOException e) {
                                                        try {
                                                            String result = NetworkUtils.doGet(NetworkUtils.toURL(String.format("https://cursemeta.dries007.net/%d/%d.json", file.getProjectID(), file.getFileID())));
                                                            CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
                                                            return file.withFileName(mod.getFileNameOnDisk()).withURL(mod.getDownloadURL());
                                                        } catch (FileNotFoundException fof) {
                                                            Logging.LOG.log(Level.WARNING, "Could not query cursemeta for deleted mods: " + file.getUrl(), fof);
                                                            notFound.set(true);
                                                            return file;
                                                        } catch (IOException | JsonParseException e2) {
                                                            try {
                                                                String result = NetworkUtils.doGet(NetworkUtils.toURL(String.format("https://addons-ecs.forgesvc.net/api/v2/addon/%d/file/%d", file.getProjectID(), file.getFileID())));
                                                                CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
                                                                return file.withFileName(mod.getFileName()).withURL(mod.getDownloadURL());
                                                            } catch (FileNotFoundException fof) {
                                                                Logging.LOG.log(Level.WARNING, "Could not query forgesvc for deleted mods: " + file.getUrl(), fof);
                                                                notFound.set(true);
                                                                return file;
                                                            } catch (IOException | JsonParseException e3) {
                                                                Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), e);
                                                                Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), e2);
                                                                Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), e3);
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
                                        .collect(Collectors.toList()));

                        manifest = newManifest;
                        configuration = configuration.setManifest(newManifest);
                        FileUtils.writeText(configurationFile, JsonUtils.GSON.toJson(configuration));

                        for (McbbsModpackManifest.File file : newManifest.getFiles())
                            if (file instanceof McbbsModpackManifest.CurseFile) {
                                McbbsModpackManifest.CurseFile curseFile = (McbbsModpackManifest.CurseFile) file;
                                if (StringUtils.isNotBlank(curseFile.getFileName())) {
                                    if (!modManager.hasSimpleMod(curseFile.getFileName())) {
                                        FileDownloadTask task = new FileDownloadTask(curseFile.getUrl(), modManager.getSimpleModPath(curseFile.getFileName()).toFile());
                                        task.setCacheRepository(dependency.getCacheRepository());
                                        task.setCaching(true);
                                        dependencies.add(task.withCounter("hmcl.modpack.download"));
                                    }
                                }
                            }

                        if (!dependencies.isEmpty()) {
                            getProperties().put("total", dependencies.size());
                            notifyPropertiesChanged();
                        }

                        return executor.all(dependencies);
                    })).whenComplete(wrap((unused1, ex) -> {
                        // Let this task fail if the curse manifest has not been completed.
                        // But continue other downloads.
                        if (notFound.get())
                            throw new CurseCompletionException(new FileNotFoundException());
                        if (!allNameKnown.get() || ex != null)
                            throw new CurseCompletionException();
                    })));
        }));
    }

    @Nullable
    private Path getFilePath(McbbsModpackManifest.File file) {
        if (file instanceof McbbsModpackManifest.AddonFile) {
            return modManager.getRepository().getRunDirectory(modManager.getVersion()).toPath().resolve(((McbbsModpackManifest.AddonFile) file).getPath());
        } else if (file instanceof McbbsModpackManifest.CurseFile) {
            String fileName = ((McbbsModpackManifest.CurseFile) file).getFileName();
            if (fileName == null) return null;
            return modManager.getSimpleModPath(fileName);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private String getFileHash(McbbsModpackManifest.File file) {
        if (file instanceof McbbsModpackManifest.AddonFile) {
            return ((McbbsModpackManifest.AddonFile) file).getHash();
        } else {
            return null;
        }
    }

    private Task<?> downloadFile(McbbsModpackManifest remoteManifest, McbbsModpackManifest.File file) throws IOException {
        if (file instanceof McbbsModpackManifest.AddonFile) {
            McbbsModpackManifest.AddonFile addonFile = (McbbsModpackManifest.AddonFile) file;
            return new FileDownloadTask(
                    new URL(remoteManifest.getFileApi() + "/overrides/" + NetworkUtils.encodeLocation(addonFile.getPath())),
                    modManager.getSimpleModPath(addonFile.getPath()).toFile(),
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
