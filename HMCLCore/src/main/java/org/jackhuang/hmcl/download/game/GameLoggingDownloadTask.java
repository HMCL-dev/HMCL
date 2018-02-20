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

import org.jackhuang.hmcl.download.DependencyManager;
import org.jackhuang.hmcl.game.DownloadType;
import org.jackhuang.hmcl.game.LoggingInfo;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * This task is to download log4j configuration file provided in minecraft.json.
 *
 * @author huangyuhui
 */
public final class GameLoggingDownloadTask extends Task {

    private final DependencyManager dependencyManager;
    private final Version version;
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides proxy settings and {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public GameLoggingDownloadTask(DependencyManager dependencyManager, Version version) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public Collection<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() {
        if (version.getLogging() == null || !version.getLogging().containsKey(DownloadType.CLIENT))
            return;

        LoggingInfo logging = version.getLogging().get(DownloadType.CLIENT);
        File file = dependencyManager.getGameRepository().getLoggingObject(version.getId(), version.getAssetIndex().getId(), logging);
        if (!file.exists())
            dependencies.add(new FileDownloadTask(NetworkUtils.toURL(logging.getFile().getUrl()), file, dependencyManager.getProxy()));
    }

}
