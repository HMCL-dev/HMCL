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
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ModrinthCompletionTask extends Task<Void> {

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final ModManager modManager;
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
        this.modManager = repository.getModManager(version);
        this.version = version;
        this.manifest = manifest;

        if (manifest == null)
            try {
                Path manifestFile = repository.getVersionRoot(version).resolve("modrinth.index.json");
                if (Files.exists(manifestFile))
                    this.manifest = JsonUtils.fromJsonFile(manifestFile, ModrinthManifest.class);
            } catch (Exception e) {
                LOG.warning("Unable to read Modrinth modpack manifest.json", e);
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

        Path runDirectory = FileUtils.toAbsolute(repository.getRunDirectory(version));
        Path modsDirectory = runDirectory.resolve("mods");

        for (ModrinthManifest.File file : manifest.getFiles()) {
            if (file.getEnv() != null && file.getEnv().getOrDefault("client", "required").equals("unsupported"))
                continue;
            if (file.getDownloads().isEmpty())
                continue;

            Path filePath = runDirectory.resolve(file.getPath()).toAbsolutePath().normalize();
            if (!filePath.startsWith(runDirectory))
                throw new IOException("Unsecure path: " + file.getPath());

            if (Files.exists(filePath))
                continue;
            if (modsDirectory.equals(filePath.getParent()) && this.modManager.hasSimpleMod(FileUtils.getName(filePath)))
                continue;

            var task = new FileDownloadTask(
                    file.getDownloads().stream().map(NetworkUtils::toURI).collect(Collectors.toList()),
                    filePath);
            task.setCacheRepository(dependency.getCacheRepository());
            task.setCaching(true);
            dependencies.add(task.withCounter("hmcl.modpack.download"));
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
