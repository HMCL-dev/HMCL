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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.game.*;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.ParallelTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.AutoTypingMap;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.gson.JsonUtils;

/**
 *
 * @author huangyuhui
 */
public class DefaultGameBuilder extends GameBuilder {

    private final DefaultDependencyManager dependencyManager;
    private final DownloadProvider downloadProvider;

    public DefaultGameBuilder(DefaultDependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
        this.downloadProvider = dependencyManager.getDownloadProvider();
    }

    public DefaultDependencyManager getDependencyManager() {
        return dependencyManager;
    }

    public DownloadProvider getDownloadProvider() {
        return downloadProvider;
    }

    @Override
    public Task buildAsync() {
        return new VersionJsonDownloadTask(gameVersion, dependencyManager).then(variables -> {
            Version version = JsonUtils.GSON.fromJson(variables.<String>get(VersionJsonDownloadTask.ID), Version.class);
            version = version.setId(name).setJar(null);
            variables.set("version", version);
            Task result = downloadGameAsync(gameVersion, version).then(new ParallelTask(
                    new GameAssetDownloadTask(dependencyManager, version),
                    new GameLibrariesTask(dependencyManager, version) // Game libraries will be downloaded for multiple times partly, this time is for vanilla libraries.
            ).with(new VersionJsonSaveTask(dependencyManager.getGameRepository(), version))); // using [with] because download failure here are tolerant.

            if (toolVersions.containsKey("forge"))
                result = result.then(libraryTaskHelper(gameVersion, "forge"));
            if (toolVersions.containsKey("liteloader"))
                result = result.then(libraryTaskHelper(gameVersion, "liteloader"));
            if (toolVersions.containsKey("optifine"))
                result = result.then(libraryTaskHelper(gameVersion, "optifine"));

            for (RemoteVersion remoteVersion : remoteVersions)
                result = result.then(dependencyManager.installLibraryAsync(remoteVersion));

            return result;
        }).finalized((variables, isDependentsSucceeded) -> {
            if (!isDependentsSucceeded)
                dependencyManager.getGameRepository().getVersionRoot(name).delete();
        });
    }

    private ExceptionalFunction<AutoTypingMap<String>, Task, ?> libraryTaskHelper(String gameVersion, String libraryId) {
        return variables -> dependencyManager.installLibraryAsync(gameVersion, variables.get("version"), libraryId, toolVersions.get(libraryId));
    }

    protected Task downloadGameAsync(String gameVersion, Version version) {
        return new GameDownloadTask(dependencyManager, gameVersion, version);
    }

}
