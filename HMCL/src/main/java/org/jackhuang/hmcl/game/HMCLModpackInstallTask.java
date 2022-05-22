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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.mod.MinecraftInstanceTask;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackInstallTask;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HMCLModpackInstallTask extends Task<Void> {
    private final File zipFile;
    private final String name;
    private final HMCLGameRepository repository;
    private final DefaultDependencyManager dependency;
    private final Modpack modpack;
    private final List<Task<?>> dependencies = new ArrayList<>(1);
    private final List<Task<?>> dependents = new ArrayList<>(4);

    public HMCLModpackInstallTask(Profile profile, File zipFile, Modpack modpack, String name) {
        dependency = profile.getDependency();
        repository = profile.getRepository();
        this.zipFile = zipFile;
        this.name = name;
        this.modpack = modpack;

        File run = repository.getRunDirectory(name);
        File json = repository.getModpackConfiguration(name);
        if (repository.hasVersion(name) && !json.exists())
            throw new IllegalArgumentException("Version " + name + " already exists");

        dependents.add(dependency.gameBuilder().name(name).gameVersion(modpack.getGameVersion()).buildAsync());

        onDone().register(event -> {
            if (event.isFailed()) repository.removeVersionFromDisk(name);
        });

        ModpackConfiguration<Modpack> config = null;
        try {
            if (json.exists()) {
                config = JsonUtils.GSON.fromJson(FileUtils.readText(json), new TypeToken<ModpackConfiguration<Modpack>>() {
                }.getType());

                if (!HMCLModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Version " + name + " is not a HMCL modpack. Cannot update this version.");
            }
        } catch (JsonParseException | IOException ignore) {
        }
        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), Collections.singletonList("/minecraft"), it -> !"pack.json".equals(it), config));
        dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), Collections.singletonList("/minecraft"), modpack, HMCLModpackProvider.INSTANCE, modpack.getName(), modpack.getVersion(), repository.getModpackConfiguration(name)).withStage("hmcl.modpack"));
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public List<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public void execute() throws Exception {
        String json = CompressingUtils.readTextZipEntry(zipFile, "minecraft/pack.json");
        Version originalVersion = JsonUtils.GSON.fromJson(json, Version.class).setId(name).setJar(null);
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(originalVersion);
        Task<Version> libraryTask = Task.supplyAsync(() -> originalVersion);
        // reinstall libraries
        // libraries of Forge and OptiFine should be obtained by installation.
        for (LibraryAnalyzer.LibraryMark mark : analyzer) {
            if (LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId().equals(mark.getLibraryId()))
                continue;
            libraryTask = libraryTask.thenComposeAsync(version -> dependency.installLibraryAsync(modpack.getGameVersion(), version, mark.getLibraryId(), mark.getLibraryVersion()));
        }

        dependencies.add(libraryTask.thenComposeAsync(repository::saveAsync));
    }
}
