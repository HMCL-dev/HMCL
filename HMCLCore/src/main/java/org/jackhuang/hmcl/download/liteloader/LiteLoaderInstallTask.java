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
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.LibrariesDownloadInfo;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.LibraryDownloadInfo;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.Lang;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Note: LiteLoader must be installed after Forge.
 *
 * @author huangyuhui
 */
public final class LiteLoaderInstallTask extends TaskResult<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final LiteLoaderRemoteVersion remote;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    public LiteLoaderInstallTask(DefaultDependencyManager dependencyManager, Version version, LiteLoaderRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;
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
    public String getId() {
        return "version";
    }

    @Override
    public void execute() {
        Library library = new Library(
                "com.mumfrey", "liteloader", remote.getSelfVersion(), null,
                "http://dl.liteloader.com/versions/",
                new LibrariesDownloadInfo(new LibraryDownloadInfo(null, remote.getUrl()))
        );

        Version tempVersion = version.setLibraries(Lang.merge(remote.getLibraries(), Collections.singleton(library)));

        String mcArg = version.getMinecraftArguments().orElse("");
        if (mcArg.contains("--tweakClass optifine.OptiFineTweaker"))
            mcArg = mcArg.replace("--tweakClass optifine.OptiFineTweaker", "");

        setResult(version
                .setMainClass("net.minecraft.launchwrapper.Launch")
                .setLibraries(Lang.merge(tempVersion.getLibraries(), version.getLibraries()))
                .setLogging(Collections.emptyMap())
                .setMinecraftArguments(mcArg + " --tweakClass " + remote.getTweakClass())
                //.setArguments(Arguments.addGameArguments(Lang.get(version.getArguments()), "--tweakClass", remote.getTag().getTweakClass()))
        );

        dependencies.add(new GameLibrariesTask(dependencyManager, tempVersion));
    }

}
