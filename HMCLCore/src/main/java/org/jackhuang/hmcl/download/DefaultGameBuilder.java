/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.download.game.VersionJsonDownloadTask;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.util.Map;

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
    public Task<?> buildAsync() {
        return new VersionJsonDownloadTask(gameVersion, dependencyManager).thenComposeAsync(rawJson -> {
            Version original = JsonUtils.GSON.fromJson(rawJson, Version.class);
            Version version = original.setId(name).setJar(null);
            Task<?> vanillaTask = downloadGameAsync(gameVersion, version).thenComposeAsync(Task.allOf(
                    new GameAssetDownloadTask(dependencyManager, version, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY),
                    new GameLibrariesTask(dependencyManager, version) // Game libraries will be downloaded for multiple times partly, this time is for vanilla libraries.
            ).withComposeAsync(dependencyManager.getGameRepository().save(version))); // using [with] because download failure here are tolerant.

            Task<Version> libraryTask = vanillaTask.thenSupplyAsync(() -> version);

            for (Map.Entry<String, String> entry : toolVersions.entrySet())
                libraryTask = libraryTask.thenComposeAsync(libraryTaskHelper(gameVersion, entry.getKey(), entry.getValue()));

            for (RemoteVersion remoteVersion : remoteVersions)
                libraryTask = libraryTask.thenComposeAsync(dependencyManager.installLibraryAsync(remoteVersion));

            return libraryTask;
        }).whenComplete(exception -> {
            if (exception != null)
                dependencyManager.getGameRepository().removeVersionFromDisk(name);
        });
    }

    private ExceptionalFunction<Version, Task<Version>, ?> libraryTaskHelper(String gameVersion, String libraryId, String libraryVersion) {
        return version -> dependencyManager.installLibraryAsync(gameVersion, version, libraryId, libraryVersion);
    }

    protected Task<Void> downloadGameAsync(String gameVersion, Version version) {
        return new GameDownloadTask(dependencyManager, gameVersion, version);
    }

}
