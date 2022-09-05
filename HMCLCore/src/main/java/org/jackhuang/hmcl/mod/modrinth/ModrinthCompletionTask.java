/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.modrinth;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class ModrinthCompletionTask extends Task<Void> {

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final String version;
    private ModrinthManifest manifest;
    private final List<Task<?>> dependencies = new ArrayList<>();

    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);
    private final AtomicInteger finished = new AtomicInteger(0);
    private final AtomicBoolean notFound = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     */
    public ModrinthCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        this(dependencyManager, version, null);
    }

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     * @param manifest          the CurseForgeModpack manifest.
     */
    public ModrinthCompletionTask(DefaultDependencyManager dependencyManager, String version, ModrinthManifest manifest) {
        this.dependency = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.version = version;
        this.manifest = manifest;

        if (manifest == null)
            try {
                File manifestFile = new File(repository.getVersionRoot(version), "modrinth.index.json");
                if (manifestFile.exists())
                    this.manifest = JsonUtils.GSON.fromJson(FileUtils.readText(manifestFile), ModrinthManifest.class);
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read Modrinth modpack manifest.json", e);
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

        Path runDirectory = repository.getRunDirectory(version).toPath();

        for (ModrinthManifest.File file : manifest.getFiles()) {
            if (file.getEnv() != null && file.getEnv().getOrDefault("client", null).equals("unsupported"))
                continue;
            Path filePath = runDirectory.resolve(file.getPath());
            if (!Files.exists(filePath) && !file.getDownloads().isEmpty()) {
                FileDownloadTask task = new FileDownloadTask(file.getDownloads().get(0), filePath.toFile());
                task.setCacheRepository(dependency.getCacheRepository());
                task.setCaching(true);
                dependencies.add(task.withCounter("hmcl.modpack.download"));
            }
        }

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
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
