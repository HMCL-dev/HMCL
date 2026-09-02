/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.modpack.curse;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.modpack.ModpackCompletionException;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Completes missing files for an installed CurseForge modpack.
@NotNullByDefault
public final class CurseCompletionTask extends Task<Void> {

    /// The dependency manager used to resolve and download remote files.
    private final DefaultDependencyManager dependency;

    /// The fixed registered instance completed by this task.
    private final DefaultGameInstance instance;

    /// The mod manager associated with [#instance].
    private final ModManager modManager;

    /// The manifest supplied by the caller or loaded from disk, if available.
    private @Nullable CurseManifest manifest;

    /// Download tasks produced during [#execute()].
    private List<Task<?>> dependencies = List.of();

    /// Whether every manifest file name could be resolved.
    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);

    /// The number of manifest entries processed in the current phase.
    private final AtomicInteger finished = new AtomicInteger(0);

    /// Whether a manifest entry refers to a deleted remote file.
    private final AtomicBoolean notFound = new AtomicBoolean(false);

    /// Creates a task that completes the installed CurseForge modpack.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    public CurseCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        this(dependencyManager, instance, null);
    }

    /// Creates a task that completes the installed CurseForge modpack using an optional manifest.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    /// @param manifest          the CurseForge manifest, or `null` to read it from disk
    public CurseCompletionTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            @Nullable CurseManifest manifest) {
        dependencyManager.validateGameInstance(instance);
        this.dependency = dependencyManager;
        this.instance = instance;
        this.modManager = instance.getModManager();
        this.manifest = manifest;

        if (manifest == null)
            try {
                Path manifestFile = instance.getInstanceRoot().resolve("manifest.json");
                if (Files.exists(manifestFile))
                    this.manifest = JsonUtils.fromJsonFile(manifestFile, CurseManifest.class);
            } catch (Exception e) {
                LOG.warning("Unable to read CurseForge modpack manifest.json", e);
            }

        setStage("hmcl.modpack.download");
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        if (manifest == null)
            return;

        Path root = instance.getInstanceRoot();

        // Because in China, Curse is too difficult to visit,
        // if failed, ignore it and retry next time.
        CurseManifest newManifest = manifest.setFiles(
                manifest.files().parallelStream()
                        .map(file -> {
                            updateProgress(finished.incrementAndGet(), manifest.files().size());
                            boolean mandatory = StringUtils.isBlank(file.fileName()) || file.url() == null;
                            boolean needHashes = file.hashes() == null || file.hashes().isEmpty();
                            if (mandatory || needHashes) {
                                RemoteAddon.File remoteFile = null;
                                Exception lastException = null;
                                for (int attempt = 0; attempt < 3; attempt++) {
                                    try {
                                        remoteFile = CurseForgeRemoteAddonRepository.MODS.getAddonFile(Integer.toString(file.projectID()), Integer.toString(file.fileID()));
                                        break;
                                    } catch (FileNotFoundException fof) {
                                        LOG.warning("Could not query api.curseforge.com for deleted mods: " + file.projectID() + ", " + file.fileID(), fof);
                                        if (mandatory) {
                                            notFound.set(true);
                                        }
                                        return file;
                                    } catch (IOException | JsonParseException e) {
                                        lastException = e;
                                        if (attempt < 2) {
                                            try {
                                                Thread.sleep(500L * (attempt + 1));
                                            } catch (InterruptedException ignored) {
                                                Thread.currentThread().interrupt();
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (remoteFile != null) {
                                    return file.withFileName(remoteFile.filename()).withURL(remoteFile.url()).withHashes(remoteFile.hashes());
                                } else {
                                    LOG.warning("Unable to fetch the file info projectID=" + file.projectID() + ", fileID=" + file.fileID(), lastException);
                                    if (mandatory) {
                                        allNameKnown.set(false);
                                    }
                                    return file;
                                }
                            } else {
                                return file;
                            }
                        })
                        .collect(Collectors.toList()));
        JsonUtils.writeToJsonFile(root.resolve("manifest.json"), newManifest);

        Path versionRoot = instance.getInstanceRoot();
        Path resourcePacksRoot = versionRoot.resolve("resourcepacks");
        Path shaderPacksRoot = versionRoot.resolve("shaderpacks");
        finished.set(0);
        dependencies = newManifest.files()
                .stream().parallel()
                .filter(f -> f.fileName() != null)
                .flatMap(f -> {
                    try {
                        Path path = null;
                        for (int attempt = 0; attempt < 3; attempt++) {
                            try {
                                path = guessFilePath(f, dependency.getDownloadProvider(), resourcePacksRoot, shaderPacksRoot);
                                break;
                            } catch (IOException e) {
                                if (attempt == 2) throw e;
                                try {
                                    Thread.sleep(500L * (attempt + 1));
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                        if (path == null) {
                            return Stream.empty();
                        }

                        var task = new FileDownloadTask(f.url(), path, f.getIntegrityCheck());
                        task.setCacheRepository(dependency.getCacheRepository());
                        task.setCaching(true);
                        return Stream.of(task.withCounter("hmcl.modpack.download"));
                    } catch (IOException e) {
                        LOG.warning("Could not query api.curseforge.com for mod: " + f.projectID() + ", " + f.fileID(), e);
                        return Stream.empty(); // Ignore this file.
                    } finally {
                        updateProgress(finished.incrementAndGet(), newManifest.files().size());
                    }
                })
                .collect(Collectors.toList());

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
        }
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
        RemoteAddon mod = CurseForgeRemoteAddonRepository.MODS.getAddonById(downloadProvider, Integer.toString(file.projectID()));
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

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        // Let this task fail if the curse manifest has not been completed.
        // But continue other downloads.
        if (notFound.get())
            throw new ModpackCompletionException(new FileNotFoundException());
        if (!allNameKnown.get() || !isDependenciesSucceeded())
            throw new ModpackCompletionException();
    }
}
