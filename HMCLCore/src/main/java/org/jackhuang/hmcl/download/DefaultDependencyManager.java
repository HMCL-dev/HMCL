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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.forge.ForgeInstallTask;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.download.game.LibrariesUniqueTask;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderInstallTask;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.ParallelTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.AutoTypingMap;
import org.jackhuang.hmcl.util.ExceptionalFunction;

/**
 * Note: This class has no state.
 *
 * @author huangyuhui
 */
public class DefaultDependencyManager extends AbstractDependencyManager {

    private final DefaultGameRepository repository;
    private final DownloadProvider downloadProvider;
    private final DefaultCacheRepository cacheRepository;

    public DefaultDependencyManager(DefaultGameRepository repository, DownloadProvider downloadProvider, DefaultCacheRepository cacheRepository) {
        this.repository = repository;
        this.downloadProvider = downloadProvider;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public DefaultGameRepository getGameRepository() {
        return repository;
    }

    @Override
    public DownloadProvider getDownloadProvider() {
        return downloadProvider;
    }

    @Override
    public DefaultCacheRepository getCacheRepository() {
        return cacheRepository;
    }

    @Override
    public GameBuilder gameBuilder() {
        return new DefaultGameBuilder(this);
    }

    @Override
    public Task checkGameCompletionAsync(Version version) {
        return new ParallelTask(
                new GameAssetDownloadTask(this, version),
                new GameLibrariesTask(this, version)
        );
    }

    @Override
    public Task checkLibraryCompletionAsync(Version version) {
        return new GameLibrariesTask(this, version);
    }

    @Override
    public Task installLibraryAsync(String gameVersion, Version version, String libraryId, String libraryVersion) {
        VersionList<?> versionList = getVersionList(libraryId);
        return versionList.loadAsync(getDownloadProvider())
                .then(variables -> installLibraryAsync(version, versionList.getVersion(gameVersion, libraryVersion)
                        .orElseThrow(() -> new IllegalStateException("Remote library " + libraryId + " has no version " + libraryVersion))));
    }

    @Override
    public Task installLibraryAsync(Version version, RemoteVersion libraryVersion) {
        if (libraryVersion instanceof ForgeRemoteVersion)
            return new ForgeInstallTask(this, version, (ForgeRemoteVersion) libraryVersion)
                    .then(variables -> new LibrariesUniqueTask(variables.get("version")))
                    .then(variables -> new MaintainTask(variables.get("version")))
                    .then(variables -> new VersionJsonSaveTask(repository, variables.get("version")));
        else if (libraryVersion instanceof LiteLoaderRemoteVersion)
            return new LiteLoaderInstallTask(this, version, (LiteLoaderRemoteVersion) libraryVersion)
                    .then(variables -> new LibrariesUniqueTask(variables.get("version")))
                    .then(variables -> new MaintainTask(variables.get("version")))
                    .then(variables -> new VersionJsonSaveTask(repository, variables.get("version")));
        else if (libraryVersion instanceof OptiFineRemoteVersion)
            return new OptiFineInstallTask(this, version, (OptiFineRemoteVersion) libraryVersion)
                    .then(variables -> new LibrariesUniqueTask(variables.get("version")))
                    .then(variables -> new MaintainTask(variables.get("version")))
                    .then(variables -> new VersionJsonSaveTask(repository, variables.get("version")));
        else
            throw new IllegalArgumentException("Remote library " + libraryVersion + " is unrecognized.");
    }


    public ExceptionalFunction<AutoTypingMap<String>, Task, ?> installLibraryAsync(RemoteVersion libraryVersion) {
        return var -> installLibraryAsync(var.get("version"), libraryVersion);
    }
}
