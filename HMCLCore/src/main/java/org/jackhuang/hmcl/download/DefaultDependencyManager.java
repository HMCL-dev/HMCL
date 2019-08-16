/*
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.forge.ForgeInstallTask;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;

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
    public Task<?> checkGameCompletionAsync(Version version) {
        return Task.allOf(
                Task.composeAsync(() -> {
                    if (!repository.getVersionJar(version).exists())
                        return new GameDownloadTask(this, null, version);
                    else
                        return null;
                }),
                new GameAssetDownloadTask(this, version, GameAssetDownloadTask.DOWNLOAD_INDEX_IF_NECESSARY),
                new GameLibrariesTask(this, version)
        );
    }

    @Override
    public Task<?> checkLibraryCompletionAsync(Version version) {
        return new GameLibrariesTask(this, version);
    }

    @Override
    public Task<Version> installLibraryAsync(String gameVersion, Version baseVersion, String libraryId, String libraryVersion) {
        if (baseVersion.isResolved()) throw new IllegalArgumentException("Version should not be resolved");

        VersionList<?> versionList = getVersionList(libraryId);
        return versionList.loadAsync(gameVersion, getDownloadProvider())
                .thenComposeAsync(() -> installLibraryAsync(baseVersion, versionList.getVersion(gameVersion, libraryVersion)
                        .orElseThrow(() -> new IllegalStateException("Remote library " + libraryId + " has no version " + libraryVersion))));
    }

    @Override
    public Task<Version> installLibraryAsync(Version baseVersion, RemoteVersion libraryVersion) {
        if (baseVersion.isResolved()) throw new IllegalArgumentException("Version should not be resolved");

        return libraryVersion.getInstallTask(this, baseVersion)
                .thenApplyAsync(baseVersion::addPatch)
                .thenComposeAsync(repository::save);
    }


    public ExceptionalFunction<Version, Task<Version>, ?> installLibraryAsync(RemoteVersion libraryVersion) {
        return version -> installLibraryAsync(version, libraryVersion);
    }

    public Task<Version> installLibraryAsync(Version oldVersion, Path installer) {
        if (oldVersion.isResolved()) throw new IllegalArgumentException("Version should not be resolved");

        return Task
                .composeAsync(() -> {
                    try {
                        return ForgeInstallTask.install(this, oldVersion, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return OptiFineInstallTask.install(this, oldVersion, installer);
                    } catch (IOException ignore) {
                    }

                    throw new UnsupportedOperationException("Library cannot be recognized");
                })
                .thenApplyAsync(oldVersion::addPatch)
                .thenComposeAsync(repository::save);
    }

    /**
     * Remove installed library.
     * Will try to remove libraries and patches.
     *
     * @param versionId version id
     * @param libraryId forge/liteloader/optifine
     * @return task to remove the specified library
     */
    public Task<Version> removeLibraryWithoutSavingAsync(String versionId, String libraryId) {
        Version version = repository.getVersion(versionId); // to ensure version is not resolved

        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version);
        LinkedList<Library> newList = new LinkedList<>(version.getLibraries());

        switch (libraryId) {
            case "forge":
                analyzer.ifPresent(LibraryAnalyzer.LibraryType.FORGE, (library, libraryVersion) -> newList.remove(library));
                version = version.removePatchById(LibraryAnalyzer.LibraryType.FORGE.getPatchId());
                break;
            case "liteloader":
                analyzer.ifPresent(LibraryAnalyzer.LibraryType.LITELOADER, (library, libraryVersion) -> newList.remove(library));
                version = version.removePatchById(LibraryAnalyzer.LibraryType.LITELOADER.getPatchId());
                break;
            case "optifine":
                analyzer.ifPresent(LibraryAnalyzer.LibraryType.OPTIFINE, (library, libraryVersion) -> newList.remove(library));
                version = version.removePatchById(LibraryAnalyzer.LibraryType.OPTIFINE.getPatchId());
                break;
            case "fabric":
                analyzer.ifPresent(LibraryAnalyzer.LibraryType.FABRIC, (library, libraryVersion) -> newList.remove(library));
                version = version.removePatchById(LibraryAnalyzer.LibraryType.FABRIC.getPatchId());
                break;
        }
        return new MaintainTask(version.setLibraries(newList));
    }

    /**
     * Remove installed library.
     * Will try to remove libraries and patches.
     *
     * @param versionId version id
     * @param libraryId forge/liteloader/optifine
     * @return task to remove the specified library
     */
    public Task<Version> removeLibraryAsync(String versionId, String libraryId) {
        return removeLibraryWithoutSavingAsync(versionId, libraryId).thenComposeAsync(repository::save);
    }
}
