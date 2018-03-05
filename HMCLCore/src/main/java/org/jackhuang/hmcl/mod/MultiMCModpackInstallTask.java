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

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.IOUtils;
import org.jackhuang.hmcl.util.Lang;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author huangyuhui
 */
public final class MultiMCModpackInstallTask extends Task {

    private final File zipFile;
    private final MultiMCInstanceConfiguration manifest;
    private final String name;
    private final DefaultGameRepository repository;
    private final List<Task> dependencies = new LinkedList<>();
    private final List<Task> dependents = new LinkedList<>();
    
    public MultiMCModpackInstallTask(DefaultDependencyManager dependencyManager, File zipFile, MultiMCInstanceConfiguration manifest, String name) {
        this.zipFile = zipFile;
        this.manifest = manifest;
        this.name = name;
        this.repository = dependencyManager.getGameRepository();

        File run = repository.getRunDirectory(name);
        File json = repository.getModpackConfiguration(name);
        if (repository.hasVersion(name) && !json.exists())
            throw new IllegalArgumentException("Version " + name + " already exists.");
        dependents.add(dependencyManager.gameBuilder().name(name).gameVersion(manifest.getGameVersion()).buildAsync());
        onDone().register(event -> {
            if (event.isFailed())
                repository.removeVersionFromDisk(name);
        });

        ModpackConfiguration<MultiMCInstanceConfiguration> config = null;
        try {
            if (json.exists()) {
                config = Constants.GSON.fromJson(FileUtils.readText(json), new TypeToken<ModpackConfiguration<MultiMCInstanceConfiguration>>() {
                }.getType());

                if (!MODPACK_TYPE.equals(config.getType()))
                    throw new IllegalArgumentException("Version " + name + " is not a MultiMC modpack. Cannot update this version.");
            }
        } catch (JsonParseException | IOException ignore) {
        }

        dependents.add(new ModpackInstallTask<>(zipFile, run, manifest.getName() + "/minecraft/", Constants.truePredicate(), config));
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
        dependencies.add(new MinecraftInstanceTask<>(zipFile, manifest.getName() + "/minecraft/", manifest, MODPACK_TYPE, repository.getModpackConfiguration(name)));
    }

    public static final String MODPACK_TYPE = "MultiMC";
}
