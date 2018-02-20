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

import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.game.AssetIndex;
import org.jackhuang.hmcl.game.AssetIndexInfo;
import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Pair;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * This task is to extract all asset objects described in asset index json.
 *
 * @author huangyuhui
 */
public final class GameAssetRefreshTask extends TaskResult<Collection<Pair<File, AssetObject>>> {

    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final AssetIndexInfo assetIndexInfo;
    private final File assetIndexFile;
    private final List<Task> dependents = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides proxy settings and {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public GameAssetRefreshTask(AbstractDependencyManager dependencyManager, Version version) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.assetIndexInfo = version.getAssetIndex();
        this.assetIndexFile = dependencyManager.getGameRepository().getIndexFile(version.getId(), assetIndexInfo.getId());

        if (!assetIndexFile.exists())
            dependents.add(new GameAssetIndexDownloadTask(dependencyManager, version));
    }

    @Override
    public List<Task> getDependents() {
        return dependents;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void execute() throws Exception {
        AssetIndex index = Constants.GSON.fromJson(FileUtils.readText(assetIndexFile), AssetIndex.class);
        List<Pair<File, AssetObject>> res = new LinkedList<>();
        int progress = 0;
        if (index != null)
            for (AssetObject assetObject : index.getObjects().values()) {
                if (Thread.interrupted())
                    throw new InterruptedException();

                res.add(new Pair<>(dependencyManager.getGameRepository().getAssetObject(version.getId(), assetIndexInfo.getId(), assetObject), assetObject));
                updateProgress(++progress, index.getObjects().size());
            }
        setResult(res);
    }

    public static final String ID = "game_asset_refresh_task";
}
