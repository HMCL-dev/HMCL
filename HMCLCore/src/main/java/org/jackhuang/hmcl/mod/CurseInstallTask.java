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

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

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
    private final File run;
    private final ModpackConfiguration<CurseManifest> config;
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
        this.repository = dependencyManager.getGameRepository();
        this.run = repository.getRunDirectory(name);

        File json = new File(run, "modpack.json");
        if (repository.hasVersion(name) && !json.exists())
            throw new IllegalArgumentException("Version " + name + " already exists.");

        GameBuilder builder = dependencyManager.gameBuilder().name(name).gameVersion(manifest.getMinecraft().getGameVersion());
        for (CurseManifestModLoader modLoader : manifest.getMinecraft().getModLoaders())
            if (modLoader.getId().startsWith("forge-"))
                builder.version("forge", modLoader.getId().substring("forge-".length()));
        dependents.add(builder.buildAsync());

        ModpackConfiguration<CurseManifest> config = null;
        try {
            if (json.exists())
                config = Constants.GSON.fromJson(FileUtils.readText(json), new TypeToken<ModpackConfiguration<CurseManifest>>(){}.getType());
        } catch (JsonParseException | IOException ignore) {
        }
        this.config = config;
        dependents.add(new ModpackInstallTask<>(zipFile, run, manifest.getOverrides(), Constants.truePredicate(), config));
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
        if (config != null)
            for (CurseManifestFile oldCurseManifestFile : config.getManifest().getFiles()) {
                if (oldCurseManifestFile.getFileName() == null) continue;
                File oldFile = new File(run, "mods/" + oldCurseManifestFile.getFileName());
                if (!oldFile.exists()) continue;
                if (manifest.getFiles().stream().noneMatch(oldCurseManifestFile::equals))
                    oldFile.delete();
            }

        dependencies.add(new CurseCompletionTask(dependencyManager, name));
        dependencies.add(new MinecraftInstanceTask<>(zipFile, manifest.getOverrides(), false, manifest, new File(run, "modpack.json")));
    }

}
