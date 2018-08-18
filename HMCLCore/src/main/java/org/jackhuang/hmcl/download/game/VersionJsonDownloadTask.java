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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public final class VersionJsonDownloadTask extends Task {
    private final String gameVersion;
    private final DefaultDependencyManager dependencyManager;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();
    private final VersionList<?> gameVersionList;

    public VersionJsonDownloadTask(String gameVersion, DefaultDependencyManager dependencyManager) {
        this.gameVersion = gameVersion;
        this.dependencyManager = dependencyManager;
        this.gameVersionList = dependencyManager.getVersionList("game");
        
        if (!gameVersionList.isLoaded())
            dependents.add(gameVersionList.refreshAsync(dependencyManager.getDownloadProvider()));

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public Collection<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public void execute() {
        RemoteVersion remoteVersion = gameVersionList.getVersions(gameVersion).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find specific version " + gameVersion + " in remote repository"));
        String jsonURL = dependencyManager.getDownloadProvider().injectURL(remoteVersion.getUrl());
        dependencies.add(new GetTask(NetworkUtils.toURL(jsonURL), ID));
    }
    
    public static final String ID = "raw_version_json";
}
