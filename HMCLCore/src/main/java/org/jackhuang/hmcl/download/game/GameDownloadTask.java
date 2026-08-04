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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// Downloads a Minecraft client jar to a repository-resolved or explicitly fixed destination.
@NotNullByDefault
public final class GameDownloadTask extends Task<Void> {

    /// The dependency manager supplying downloads and cache access.
    private final DefaultDependencyManager dependencyManager;

    /// The optional Minecraft version used to locate a cached jar candidate.
    private final @Nullable String gameVersion;

    /// The resolved manifest that supplies client download metadata.
    private final GameInstanceManifest manifest;

    /// The explicit destination fixed when this task is created, or `null` to resolve it at execution.
    private final @Nullable Path jar;

    /// The file-download task created during execution.
    private final List<Task<?>> dependencies = new ArrayList<>();

    /// Creates a task whose destination is resolved from the repository when execution starts.
    ///
    /// @param dependencyManager the dependency manager used for resolution and downloading
    /// @param gameVersion       the Minecraft version used as a cache key, or `null`
    /// @param manifest          the manifest supplying client download metadata
    public GameDownloadTask(
            DefaultDependencyManager dependencyManager,
            @Nullable String gameVersion,
            GameInstanceManifest manifest) {
        this.dependencyManager = dependencyManager;
        this.gameVersion = gameVersion;
        this.manifest = manifest.resolve(dependencyManager.getGameRepository());
        this.jar = null;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Creates a task that writes the client jar to an explicit fixed destination.
    ///
    /// @param dependencyManager the dependency manager used for resolution and downloading
    /// @param gameVersion       the Minecraft version used as a cache key, or `null`
    /// @param manifest          the manifest supplying client download metadata
    /// @param jar               the destination jar path
    public GameDownloadTask(
            DefaultDependencyManager dependencyManager,
            @Nullable String gameVersion,
            GameInstanceManifest manifest,
            Path jar) {
        this.dependencyManager = dependencyManager;
        this.gameVersion = gameVersion;
        this.manifest = manifest.resolve(dependencyManager.getGameRepository());
        this.jar = jar;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the download created by [#execute()], if execution has started.
    ///
    /// @return the live dependency collection
    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    /// Creates the file-download dependency for the configured destination.
    @Override
    public void execute() {
        Path destination = jar != null
                ? jar
                : dependencyManager.getGameRepository().getInstanceJar(manifest);
        var task = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLWithCandidates(manifest.getDownloadInfo().getUrl()),
                destination,
                FileDownloadTask.IntegrityCheck.of(CacheRepository.SHA1, manifest.getDownloadInfo().getSha1()));
        task.setCaching(true);
        task.setCacheRepository(dependencyManager.getCacheRepository());

        if (gameVersion != null)
            task.setCandidate(dependencyManager.getCacheRepository().getCommonDirectory().resolve("jars").resolve(gameVersion + ".jar"));

        dependencies.add(task);
    }
}
