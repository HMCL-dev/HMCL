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
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// Downloads a Minecraft client JAR to shared cache storage or an explicitly fixed destination.
@NotNullByDefault
public final class GameDownloadTask extends Task<Path> {

    /// The dependency manager supplying downloads and cache access.
    private final DefaultDependencyManager dependencyManager;

    /// The resolved manifest that supplies client download metadata.
    private final GameInstanceManifest manifest;

    /// Destination fixed when this task is created.
    private final Path jar;

    /// Optional pre-existing file that may seed an explicit destination.
    private final @Nullable Path candidate;

    /// The file-download task created during execution.
    private final List<Task<?>> dependencies = new ArrayList<>();

    /// Creates a task that downloads a versioned client JAR into shared cache storage.
    ///
    /// @param dependencyManager the dependency manager used for resolution and downloading
    /// @param gameVersion       the Minecraft version used as the shared-cache key
    /// @param manifest          the manifest supplying client download metadata
    public GameDownloadTask(
            DefaultDependencyManager dependencyManager,
            String gameVersion,
            GameInstanceManifest manifest) {
        this.dependencyManager = dependencyManager;
        this.manifest = dependencyManager.getGameRepository().resolve(manifest).launchManifest();
        this.jar = getSharedJarPath(dependencyManager, gameVersion);
        this.candidate = null;

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
        this.manifest = dependencyManager.getGameRepository().resolve(manifest).launchManifest();
        this.jar = jar;
        @Nullable Path sharedJar = gameVersion != null ? getSharedJarPath(dependencyManager, gameVersion) : null;
        this.candidate = sharedJar != null && !sameNormalizedPath(sharedJar, jar) ? sharedJar : null;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the shared client-JAR path for a Minecraft version.
    ///
    /// @param dependencyManager the dependency manager owning the shared cache
    /// @param gameVersion       the Minecraft version used as the file name
    /// @return the normalized path below the cache's `jars` directory
    /// @throws IllegalArgumentException if the version is blank or would escape the `jars`
    ///                                  directory
    private static Path getSharedJarPath(
            DefaultDependencyManager dependencyManager,
            String gameVersion) {
        if (gameVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version must not be blank");
        }
        Path directory = dependencyManager.getCacheRepository()
                .getCommonDirectory()
                .resolve("jars")
                .toAbsolutePath()
                .normalize();
        Path destination = directory.resolve(gameVersion + ".jar").normalize();
        if (!directory.equals(destination.getParent())) {
            throw new IllegalArgumentException("Invalid Minecraft version for cache path: " + gameVersion);
        }
        return destination;
    }

    /// Returns whether two paths identify the same normalized absolute path.
    ///
    /// @param first  the first path
    /// @param second the second path
    /// @return whether the normalized paths are equal
    private static boolean sameNormalizedPath(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }

    /// Returns the download created by [#execute()], if execution has started.
    ///
    /// @return the live dependency collection
    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    /// Creates the file-download dependency unless the destination already has the expected content.
    @Override
    public void execute() throws IOException {
        DownloadInfo downloadInfo = manifest.getDownloadInfo();
        if (Files.isRegularFile(jar) && downloadInfo.validateChecksum(jar, false)) {
            return;
        }

        var task = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLWithCandidates(downloadInfo.getUrl()),
                jar,
                FileDownloadTask.IntegrityCheck.of(CacheRepository.SHA1, downloadInfo.getSha1()));
        task.setCaching(true);
        task.setCacheRepository(dependencyManager.getCacheRepository());

        if (candidate != null) {
            task.setCandidate(candidate);
        }

        dependencies.add(task);
    }

    /// Requests post-execution so the completed destination can be returned.
    ///
    /// @return `true`
    @Override
    public boolean doPostExecute() {
        return true;
    }

    /// Returns the downloaded or previously validated client JAR.
    ///
    /// @throws IOException if the destination was not materialized
    @Override
    public void postExecute() throws IOException {
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Minecraft client JAR was not downloaded: " + jar);
        }
        setResult(jar);
    }
}
