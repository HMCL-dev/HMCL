/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.game.AssetIndex;
import org.jackhuang.hmcl.game.AssetIndexInfo;
import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public final class GameAssetDownloadTask extends Task {
    
    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final AssetIndexInfo assetIndexInfo;
    private final File assetIndexFile;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public GameAssetDownloadTask(AbstractDependencyManager dependencyManager, Version version) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.assetIndexInfo = version.getAssetIndex();
        this.assetIndexFile = dependencyManager.getGameRepository().getIndexFile(version.getId(), assetIndexInfo.getId());

        if (!assetIndexFile.exists())
            dependents.add(new GameAssetIndexDownloadTask(dependencyManager, version));
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
    public void execute() throws Exception {
        AssetIndex index = JsonUtils.GSON.fromJson(FileUtils.readText(assetIndexFile), AssetIndex.class);
        int progress = 0;
        if (index != null)
            for (AssetObject assetObject : index.getObjects().values()) {
                if (Thread.interrupted())
                    throw new InterruptedException();

                File file = dependencyManager.getGameRepository().getAssetObject(version.getId(), assetIndexInfo.getId(), assetObject);
                if (file.isFile())
                    dependencyManager.getCacheRepository().tryCacheFile(file.toPath(), CacheRepository.SHA1, assetObject.getHash());
                else {
                    String url = dependencyManager.getDownloadProvider().getAssetBaseURL() + assetObject.getLocation();
                    FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(url), file, new FileDownloadTask.IntegrityCheck("SHA-1", assetObject.getHash()));
                    task.setName(assetObject.getHash());
                    dependencies.add(task
                            .setCacheRepository(dependencyManager.getCacheRepository())
                            .setCaching(true)
                            .setCandidate(dependencyManager.getCacheRepository().getCommonDirectory()
                                    .resolve("assets").resolve("objects").resolve(assetObject.getLocation())));
                }

                updateProgress(++progress, index.getObjects().size());
            }
    }
    
}
