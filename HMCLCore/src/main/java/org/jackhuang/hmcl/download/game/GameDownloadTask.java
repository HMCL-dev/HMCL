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
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Task to download Minecraft jar
 * @author huangyuhui
 */
public final class GameDownloadTask extends Task<Void> {
    private final DefaultDependencyManager dependencyManager;
    private final String gameVersion;
    private final GameInstanceManifest manifest;
    private final List<Task<?>> dependencies = new ArrayList<>();

    public GameDownloadTask(DefaultDependencyManager dependencyManager, String gameVersion, GameInstanceManifest manifest) {
        this.dependencyManager = dependencyManager;
        this.gameVersion = gameVersion;
        this.manifest = manifest.resolve(dependencyManager.getGameRepository());

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() {
        Path jar = dependencyManager.getGameRepository().getInstanceJar(manifest);

        var task = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLWithCandidates(manifest.getDownloadInfo().getUrl()),
                jar,
                FileDownloadTask.IntegrityCheck.of(CacheRepository.SHA1, manifest.getDownloadInfo().getSha1()));
        task.setCaching(true);
        task.setCacheRepository(dependencyManager.getCacheRepository());

        if (gameVersion != null)
            task.setCandidate(dependencyManager.getCacheRepository().getCommonDirectory().resolve("jars").resolve(gameVersion + ".jar"));

        dependencies.add(task);
    }
    
}
