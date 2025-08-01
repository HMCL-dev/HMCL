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
package org.jackhuang.hmcl.mod.curse;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Complete the CurseForge version.
 *
 * @author huangyuhui
 */
public final class CurseCompletionTask extends Task<Void> {

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final ModManager modManager;
    private final String version;
    private CurseManifest manifest;
    private List<Task<?>> dependencies;

    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);
    private final AtomicInteger finished = new AtomicInteger(0);
    private final AtomicBoolean notFound = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     */
    public CurseCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        this(dependencyManager, version, null);
    }

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     * @param manifest          the CurseForgeModpack manifest.
     */
    public CurseCompletionTask(DefaultDependencyManager dependencyManager, String version, CurseManifest manifest) {
        this.dependency = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.modManager = repository.getModManager(version);
        this.version = version;
        this.manifest = manifest;

        if (manifest == null)
            try {
                File manifestFile = new File(repository.getVersionRoot(version), "manifest.json");
                if (manifestFile.exists())
                    this.manifest = JsonUtils.fromJsonFile(manifestFile.toPath(), CurseManifest.class);
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

        File root = repository.getVersionRoot(version);

        // Because in China, Curse is too difficult to visit,
        // if failed, ignore it and retry next time.
        CurseManifest newManifest = manifest.setFiles(
                manifest.getFiles().parallelStream()
                        .map(file -> {
                            updateProgress(finished.incrementAndGet(), manifest.getFiles().size());
                            if (StringUtils.isBlank(file.getFileName()) || file.getUrl() == null) {
                                try {
                                    RemoteMod.File remoteFile = CurseForgeRemoteModRepository.MODS.getModFile(Integer.toString(file.getProjectID()), Integer.toString(file.getFileID()));
                                    return file.withFileName(remoteFile.getFilename()).withURL(remoteFile.getUrl());
                                } catch (FileNotFoundException fof) {
                                    LOG.warning("Could not query api.curseforge.com for deleted mods: " + file.getProjectID() + ", " + file.getFileID(), fof);
                                    notFound.set(true);
                                    return file;
                                } catch (IOException | JsonParseException e) {
                                    LOG.warning("Unable to fetch the file name projectID=" + file.getProjectID() + ", fileID=" + file.getFileID(), e);
                                    allNameKnown.set(false);
                                    return file;
                                }
                            } else {
                                return file;
                            }
                        })
                        .collect(Collectors.toList()));
        JsonUtils.writeToJsonFile(root.toPath().resolve("manifest.json"), newManifest);

        File versionRoot = repository.getVersionRoot(modManager.getVersion());
        File resourcePacksRoot = new File(versionRoot, "resourcepacks"), shaderPacksRoot = new File(versionRoot, "shaderpacks");
        finished.set(0);
        dependencies = newManifest.getFiles()
                .stream().parallel()
                .filter(f -> f.getFileName() != null)
                .flatMap(f -> {
                    try {
                        File path = guessFilePath(f, resourcePacksRoot, shaderPacksRoot);
                        if (path == null) {
                            return Stream.empty();
                        }

                        var task = new FileDownloadTask(f.getUrl(), path.toPath());
                        task.setCacheRepository(dependency.getCacheRepository());
                        task.setCaching(true);
                        return Stream.of(task.withCounter("hmcl.modpack.download"));
                    } catch (IOException e) {
                        LOG.warning("Could not query api.curseforge.com for mod: " + f.getProjectID() + ", " + f.getFileID(), e);
                        return Stream.empty(); // Ignore this file.
                    } finally {
                        updateProgress(finished.incrementAndGet(), newManifest.getFiles().size());
                    }
                })
                .collect(Collectors.toList());

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
        }
    }

    /**
     * Guess where to store the file.
     * @param file The file.
     * @param resourcePacksRoot ./resourcepacks.
     * @param shaderPacksRoot ./shaderpacks.
     * @return ./resourcepacks/$filename or ./shaderpacks/$filename or ./mods/$filename if the file doesn't exist. null if the file existed.
     * @throws IOException If IOException was encountered during getting data from CurseForge.
     */
    private File guessFilePath(CurseManifestFile file, File resourcePacksRoot, File shaderPacksRoot) throws IOException {
        RemoteMod mod = CurseForgeRemoteModRepository.MODS.getModById(Integer.toString(file.getProjectID()));
        int classID = ((CurseAddon) mod.getData()).getClassId();
        String fileName = file.getFileName();
        switch (classID) {
            case 12: // Resource pack
            case 6552: { // Shader pack
                File res = new File(classID == 12 ? resourcePacksRoot : shaderPacksRoot, fileName);
                return res.exists() ? null : res;
            }
            default: {
                if (modManager.hasSimpleMod(fileName)) {
                    return null;
                }
                return modManager.getSimpleModPath(fileName).toFile();
            }
        }
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
