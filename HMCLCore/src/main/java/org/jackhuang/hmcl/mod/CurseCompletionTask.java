/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.File;
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
public final class CurseCompletionTask extends Task {

    private final DefaultDependencyManager dependencyManager;
    private final GameRepository repository;
    private final String version;
    private CurseManifest manifest = null;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

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
        this.dependencyManager = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.version = version;
        this.manifest = manifest;

        if (manifest == null)
            try {
                File manifestFile = new File(repository.getVersionRoot(version), "manifest.json");
                if (manifestFile.exists())
                    this.manifest = Constants.GSON.fromJson(FileUtils.readText(manifestFile), CurseManifest.class);
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read CurseForge modpack manifest.json", e);
            }
    }

    @Override
    public Collection<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public void execute() throws Exception {
        if (manifest == null)
            return;

        File root = repository.getVersionRoot(version);
        File run = repository.getRunDirectory(version);

        AtomicBoolean flag = new AtomicBoolean(true);
        AtomicInteger finished = new AtomicInteger(0);

        // Because in China, Curse is too difficult to visit,
        // if failed, ignore it and retry next time.
        CurseManifest newManifest = manifest.setFiles(
                manifest.getFiles().parallelStream()
                        .map(file -> {
                            updateProgress(finished.incrementAndGet(), manifest.getFiles().size());
                            if (StringUtils.isBlank(file.getFileName())) {
                                try {
                                    return file.withFileName(NetworkUtils.detectFileName(file.getUrl()));
                                } catch (IOException ioe) {
                                    Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), ioe);
                                    flag.set(false);
                                    return file;
                                }
                            } else
                                return file;
                        })
                        .collect(Collectors.toList()));
        FileUtils.writeText(new File(root, "manifest.json"), Constants.GSON.toJson(newManifest));

        for (CurseManifestFile file : newManifest.getFiles())
            if (StringUtils.isNotBlank(file.getFileName())) {
                File dest = new File(run, "mods/" + file.getFileName());
                if (!dest.exists())
                    dependencies.add(new FileDownloadTask(file.getUrl(), dest));
            }

        // Let this task fail if the curse manifest has not been completed.
        if (!flag.get())
            dependencies.add(Task.of(() -> {
                throw new CurseCompletionException();
            }));
    }

}
