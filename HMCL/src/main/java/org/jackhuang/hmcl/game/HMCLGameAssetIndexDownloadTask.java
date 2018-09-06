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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class HMCLGameAssetIndexDownloadTask extends Task {

    private final HMCLDependencyManager dependencyManager;
    private final Version version;
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public HMCLGameAssetIndexDownloadTask(HMCLDependencyManager dependencyManager, Version version) {
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
        File assetIndexFile = dependencyManager.getGameRepository().getIndexFile(version.getId(), assetIndexInfo.getId());

        Optional<Path> path = HMCLLocalRepository.REPOSITORY.getAssetIndex(assetIndexInfo);
        if (path.isPresent()) {
            FileUtils.copyFile(path.get().toFile(), assetIndexFile);
        } else {
            URL url = NetworkUtils.toURL(dependencyManager.getDownloadProvider().injectURL(assetIndexInfo.getUrl()));
            dependencies.add(new FileDownloadTask(url, assetIndexFile)
                    .finalized((v, succ) -> {
                        if (succ)
                            HMCLLocalRepository.REPOSITORY.cacheAssetIndex(assetIndexInfo, assetIndexFile.toPath());
                    }));
        }
    }

}
