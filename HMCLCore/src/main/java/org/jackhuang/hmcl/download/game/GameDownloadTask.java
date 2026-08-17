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
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// Obtains a Minecraft client JAR from content-addressed cache storage.
@NotNullByDefault
public final class GameDownloadTask extends Task<Path> {

    /// The dependency manager supplying downloads and cache access.
    private final DefaultDependencyManager dependencyManager;

    /// The resolved manifest that supplies client download metadata.
    private final GameInstanceManifest manifest;

    /// The cache task created during execution.
    private final List<Task<?>> dependencies = new ArrayList<>();

    /// Creates a task that returns a cached Minecraft client JAR.
    ///
    /// @param dependencyManager the dependency manager used for resolution and downloading
    /// @param manifest          the manifest supplying client download metadata
    public GameDownloadTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest) {
        this.dependencyManager = dependencyManager;
        this.manifest = dependencyManager.getGameRepository().resolve(manifest).launchManifest();

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the cache operation created by [#execute()], if execution has started.
    ///
    /// @return the live dependency collection
    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    /// Creates the checksum-aware cache download.
    @Override
    public void execute() {
        DownloadInfo downloadInfo = manifest.getDownloadInfo();
        @Nullable String sha1 = downloadInfo.getSha1();
        CacheFileTask cacheTask = sha1 != null
                ? new CacheFileTask(
                        dependencyManager.getDownloadProvider()
                                .injectURLWithCandidates(downloadInfo.getUrl()),
                        sha1)
                : new CacheFileTask(
                        dependencyManager.getDownloadProvider()
                                .injectURLWithCandidates(downloadInfo.getUrl()));
        cacheTask.setCacheRepository(dependencyManager.getCacheRepository());
        cacheTask.storeTo(this::setResult);
        dependencies.add(cacheTask);
    }

    /// Requests post-execution so the completed destination can be returned.
    @Override
    public boolean doPostExecute() {
        return true;
    }

    /// Returns the downloaded or previously validated client JAR.
    ///
    /// @throws IOException if no regular cached JAR is available
    @Override
    public void postExecute() throws IOException {
        @Nullable Path result = getResult();
        //noinspection ConstantValue
        if (result == null || !Files.isRegularFile(result)) {
            throw new IOException("Minecraft client JAR was not downloaded");
        }
    }
}
