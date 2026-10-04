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

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public final class GameInstanceJsonDownloadTask extends Task<String> {
    private final String gameVersion;
    private final DefaultDependencyManager dependencyManager;
    private final Task<ComponentRemoteVersionList<?>> getGameVersionsTask;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    public GameInstanceJsonDownloadTask(String gameVersion, DefaultDependencyManager dependencyManager) {
        this.gameVersion = gameVersion;
        this.dependencyManager = dependencyManager;

        getGameVersionsTask = dependencyManager.getDownloadProvider()
                .getVersionsAsync(GameComponentType.GAME, null, false);

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return List.of(getGameVersionsTask);
    }

    @Override
    public void execute() throws IOException {
        ComponentRemoteVersion remoteVersion = getGameVersionsTask.getResult().getRemoteVersion(gameVersion);
        if (remoteVersion == null)
            throw new IOException(new IOException("Cannot find specific version " + gameVersion + " in remote repository"));
        DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
        dependencies.add(new GetTask(downloadProvider.getDownloadCandidates(remoteVersion)).storeTo(this::setResult));
    }
}
