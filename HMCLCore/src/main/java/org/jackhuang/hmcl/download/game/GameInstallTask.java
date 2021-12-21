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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.MINECRAFT;

public class GameInstallTask extends Task<Version> {

    private final DefaultGameRepository gameRepository;
    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final GameRemoteVersion remote;
    private final VersionJsonDownloadTask downloadTask;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    public GameInstallTask(DefaultDependencyManager dependencyManager, Version version, GameRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.gameRepository = dependencyManager.getGameRepository();
        this.version = version;
        this.remote = remoteVersion;
        this.downloadTask = new VersionJsonDownloadTask(remoteVersion.getGameVersion(), dependencyManager);
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(downloadTask);
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
        Version patch = JsonUtils.fromNonNullJson(downloadTask.getResult(), Version.class)
                .setId(MINECRAFT.getPatchId()).setVersion(remote.getGameVersion()).setJar(null).setPriority(0);
        setResult(patch);

        Version version = new Version(this.version.getId()).addPatch(patch);
        dependencies.add(Task.allOf(
                new GameDownloadTask(dependencyManager, remote.getGameVersion(), version),
                Task.allOf(
                        new GameAssetDownloadTask(dependencyManager, version, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true),
                        new GameLibrariesTask(dependencyManager, version, true)
                ).withRunAsync(() -> {
                    // ignore failure
                })
        ).thenComposeAsync(gameRepository.saveAsync(version)));
    }

}
