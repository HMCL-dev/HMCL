/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CompressingUtils;

/**
 * Install a downloaded CurseForge modpack.
 *
 * @author huangyuhui
 */
public final class CurseInstallTask extends Task {

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository repository;
    private final File zipFile;
    private final CurseManifest manifest;
    private final String name;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param zipFile the CurseForge modpack file.
     * @param manifest The manifest content of given CurseForge modpack.
     * @param name the new version name
     * @see CurseManifest#readCurseForgeModpackManifest
     */
    public CurseInstallTask(DefaultDependencyManager dependencyManager, File zipFile, CurseManifest manifest, String name) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.manifest = manifest;
        this.name = name;

        repository = dependencyManager.getGameRepository();

        if (repository.hasVersion(name))
            throw new IllegalArgumentException("Version " + name + " already exists.");

        GameBuilder builder = dependencyManager.gameBuilder().name(name).gameVersion(manifest.getMinecraft().getGameVersion());
        for (CurseManifestModLoader modLoader : manifest.getMinecraft().getModLoaders())
            if (modLoader.getId().startsWith("forge-"))
                builder.version("forge", modLoader.getId().substring("forge-".length()));
        dependents.add(builder.buildAsync());
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws Exception {
        File run = repository.getRunDirectory(name);
        CompressingUtils.unzip(zipFile, run, manifest.getOverrides());

        dependencies.add(new CurseCompletionTask(dependencyManager, name));
    }

}
