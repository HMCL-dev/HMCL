/**
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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public final class GameDownloadTask extends Task {
    private final DefaultDependencyManager dependencyManager;
    private final String gameVersion;
    private final Version version;
    private final List<Task> dependencies = new LinkedList<>();

    public GameDownloadTask(DefaultDependencyManager dependencyManager, String gameVersion, Version version) {
        this.dependencyManager = dependencyManager;
        this.gameVersion = gameVersion;
        this.version = version;

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public List<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() {
        File jar = dependencyManager.getGameRepository().getVersionJar(version);
        
        dependencies.add(new FileDownloadTask(
                NetworkUtils.toURL(dependencyManager.getDownloadProvider().injectURL(version.getDownloadInfo().getUrl())),
                jar,
                IntegrityCheck.of(CacheRepository.SHA1, version.getDownloadInfo().getSha1()))
                .setCaching(true)
                .setCacheRepository(dependencyManager.getCacheRepository())
                .setCandidate(dependencyManager.getCacheRepository().getCommonDirectory().resolve("jars").resolve(gameVersion + ".jar")));
    }
    
}
