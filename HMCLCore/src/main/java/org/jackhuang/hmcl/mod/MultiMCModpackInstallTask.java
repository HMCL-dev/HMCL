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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.IOUtils;
import org.jackhuang.hmcl.util.Lang;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author huangyuhui
 */
public final class MultiMCModpackInstallTask extends Task {

    private final DefaultDependencyManager dependencyManager;
    private final File zipFile;
    private final MultiMCInstanceConfiguration manifest;
    private final String name;
    private final File run;
    private final DefaultGameRepository repository;
    private final List<Task> dependencies = new LinkedList<>();
    private final List<Task> dependents = new LinkedList<>();
    
    public MultiMCModpackInstallTask(DefaultDependencyManager dependencyManager, File zipFile, MultiMCInstanceConfiguration manifest, String name) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.manifest = manifest;
        this.name = name;
        this.repository = dependencyManager.getGameRepository();
        this.run = repository.getRunDirectory(name);
        
        if (repository.hasVersion(name))
            throw new IllegalArgumentException("Version " + name + " already exists.");
        dependents.add(dependencyManager.gameBuilder().name(name).gameVersion(manifest.getGameVersion()).buildAsync());
        onDone().register(event -> {
            if (event.isFailed())
                repository.removeVersionFromDisk(name);
        });
    }
    
    @Override
    public List<Task> getDependencies() {
        return dependencies;
    }
    
    @Override
    public List<Task> getDependents() {
        return dependents;
    }
    
    @Override
    public void execute() throws Exception {
        Version version = Objects.requireNonNull(repository.readVersionJson(name));
        CompressingUtils.unzip(zipFile, run, manifest.getName() + "/minecraft/", null, false, true);
        
        try (ZipFile zip = new ZipFile(zipFile)) {
            for (ZipArchiveEntry entry : Lang.asIterable(zip.getEntries())) {
                // ensure that this entry is in folder 'patches' and is a json file.
                if (!entry.isDirectory() && entry.getName().startsWith(manifest.getName() + "/patches/") && entry.getName().endsWith(".json")) {
                    MultiMCInstancePatch patch = Constants.GSON.fromJson(IOUtils.readFullyAsString(zip.getInputStream(entry)), MultiMCInstancePatch.class);
                    List<String> newArguments = new LinkedList<>();
                    for (String arg : patch.getTweakers()) {
                        newArguments.add("--tweakClass");
                        newArguments.add(arg);
                    }
                    version = version
                            .setLibraries(Lang.merge(version.getLibraries(), patch.getLibraries()))
                            .setMainClass(patch.getMainClass())
                            .setArguments(Arguments.addGameArguments(version.getArguments().orElse(null), newArguments));
                }
            }
        }
        
        dependencies.add(new VersionJsonSaveTask(repository, version));
    }
    
}
