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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class HMCLModpackInstallTask extends Task {
    private final File zipFile;
    private final String version;
    private final HMCLGameRepository repository;
    private final DefaultDependencyManager dependency;
    private final List<Task> dependencies = new LinkedList<>();
    private final List<Task> dependents = new LinkedList<>();

    public HMCLModpackInstallTask(Profile profile, File zipFile, Modpack modpack, String id) throws IOException {
        dependency = profile.getDependency();
        repository = profile.getRepository();
        this.zipFile = zipFile;
        this.version = id;

        if (repository.hasVersion(id))
            throw new IllegalArgumentException("Version " + id + " already exists");

        String json = CompressingUtils.readTextZipEntry(zipFile, "minecraft/pack.json");
        Version version = Constants.GSON.fromJson(json, Version.class).setJar(null);
        dependents.add(dependency.gameBuilder().name(id).gameVersion(modpack.getGameVersion()).buildAsync());
        dependencies.add(new VersionJsonSaveTask(repository, version));

        onDone().register(event -> {
            if (event.isFailed()) repository.removeVersionFromDisk(id);
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
        CompressingUtils.unzip(zipFile, repository.getRunDirectory(version),
                "minecraft/", it -> !Objects.equals(it, "minecraft/pack.json"), false);
    }
}
