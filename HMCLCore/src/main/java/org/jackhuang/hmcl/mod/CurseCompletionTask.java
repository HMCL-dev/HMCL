/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.*;
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
                    this.manifest = JsonUtils.GSON.fromJson(FileUtils.readText(manifestFile), CurseManifest.class);
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
        AtomicBoolean notFound = new AtomicBoolean(false);

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
                                        Logging.LOG.log(Level.WARNING, "Could not query cursemeta for deleted mods: " + file.getUrl(), e2);
                                        notFound.set(true);
                                        return file;
                                    }

                                } catch (IOException ioe) {
                                    Logging.LOG.log(Level.WARNING, "Unable to fetch the file name of URL: " + file.getUrl(), ioe);
                                    flag.set(false);
                                    return file;
                                }
                            } else
                                return file;
                        })
                        .collect(Collectors.toList()));
        FileUtils.writeText(new File(root, "manifest.json"), JsonUtils.GSON.toJson(newManifest));

        for (CurseManifestFile file : newManifest.getFiles())
            if (StringUtils.isNotBlank(file.getFileName())) {
                File dest = new File(run, "mods/" + file.getFileName());
                if (!dest.exists())
                    dependencies.add(new FileDownloadTask(file.getUrl(), dest));
            }

        // Let this task fail if the curse manifest has not been completed.
        // But continue other downloads.
        if (!flag.get() || notFound.get())
            dependencies.add(Task.of(() -> {
                if (notFound.get())
                    throw new CurseCompletionException(new FileNotFoundException());
                else
                    throw new CurseCompletionException();
            }));
    }

}
