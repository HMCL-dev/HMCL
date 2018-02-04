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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.game.AssetIndexInfo;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * This task is to download asset index file provided in minecraft.json.
 *
 * @author huangyuhui
 */
public final class GameAssetIndexDownloadTask extends Task {

    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides proxy settings and {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public GameAssetIndexDownloadTask(AbstractDependencyManager dependencyManager, Version version) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public List<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws Exception {
        AssetIndexInfo assetIndexInfo = version.getAssetIndex();
        File assetDir = dependencyManager.getGameRepository().getAssetDirectory(version.getId(), assetIndexInfo.getId());
        if (!FileUtils.makeDirectory(assetDir))
            throw new IOException("Cannot create directory: " + assetDir);
        File assetIndexFile = dependencyManager.getGameRepository().getIndexFile(version.getId(), assetIndexInfo.getId());
        dependencies.add(new FileDownloadTask(
                NetworkUtils.toURL(dependencyManager.getDownloadProvider().injectURL(assetIndexInfo.getUrl())),
                assetIndexFile, dependencyManager.getProxy()
        ));
    }

}
