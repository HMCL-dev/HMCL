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
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
    private final List<Task<?>> dependencies = new LinkedList<>();

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
                    this.manifest = JsonUtils.GSON.fromJson(FileUtils.readText(manifestFile), CurseManifest.class);
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read CurseForge modpack manifest.json", e);
            }
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
                            if (StringUtils.isBlank(file.getFileName())) {
                                try {
                                    return file.withFileName(NetworkUtils.detectFileName(file.getUrl()));
                                } catch (FileNotFoundException e) {
                                    try {
                                        String result = NetworkUtils.doGet(NetworkUtils.toURL(String.format("https://cursemeta.dries007.net/%d/%d.json", file.getProjectID(), file.getFileID())));
                                        CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
                                        return file.withFileName(mod.getFileNameOnDisk()).withURL(mod.getDownloadURL());
                                    } catch (IOException | JsonParseException e2) {
                                        try {
                                            String result = NetworkUtils.doGet(NetworkUtils.toURL(String.format("https://addons-ecs.forgesvc.net/api/v2/addon/%d/file/%d", file.getProjectID(), file.getFileID())));
                                            CurseMetaMod mod = JsonUtils.fromNonNullJson(result, CurseMetaMod.class);
                                            return file.withFileName(mod.getFileName()).withURL(mod.getDownloadURL());
                                        } catch (IOException | JsonParseException e3) {
                                            Logging.LOG.log(Level.WARNING, "Could not query cursemeta for deleted mods: " + file.getUrl(), e2);
                                            notFound.set(true);
                                            return file;
                                        }
                                    }

                                } catch (IOException ioe) {
                                    Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), ioe);
                                    allNameKnown.set(false);
                                    return file;
                                }
                            } else
                                return file;
                        })
                        .collect(Collectors.toList()));
        FileUtils.writeText(new File(root, "manifest.json"), JsonUtils.GSON.toJson(newManifest));

        for (CurseManifestFile file : newManifest.getFiles())
            if (StringUtils.isNotBlank(file.getFileName())) {
                if (!modManager.hasSimpleMod(file.getFileName())) {
                    dependencies.add(new FileDownloadTask(file.getUrl(), modManager.getSimpleModPath(file.getFileName()).toFile())
                            .setCacheRepository(dependency.getCacheRepository())
                            .setCaching(true).withCounter());
                }
            }

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
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
            throw new CurseCompletionException(new FileNotFoundException());
        if (!allNameKnown.get() || !isDependenciesSucceeded())
            throw new CurseCompletionException();
    }
}
