/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public Task<?> checkGameCompletionAsync(Version version, boolean integrityCheck) {
        return Task.allOf(
                Task.composeAsync(() -> {
                    File versionJar = repository.getVersionJar(version);
                    if (!versionJar.exists() || versionJar.length() == 0)
                        return new GameDownloadTask(this, null, version);
                    else
                        return null;
                }).thenComposeAsync(checkPatchCompletionAsync(version, integrityCheck)),
                new GameAssetDownloadTask(this, version, GameAssetDownloadTask.DOWNLOAD_INDEX_IF_NECESSARY, integrityCheck),
                new GameLibrariesTask(this, version, integrityCheck)
        );
    }

    @Override
    public Task<?> checkLibraryCompletionAsync(Version version, boolean integrityCheck) {
        return new GameLibrariesTask(this, version, integrityCheck, version.getLibraries());
    }

    @Override
    public Task<?> checkPatchCompletionAsync(Version version, boolean integrityCheck) {
        return Task.composeAsync(() -> {
            List<Task<?>> tasks = new ArrayList<>(0);

            String gameVersion = repository.getGameVersion(version).orElse(null);
            if (gameVersion == null) return null;

            Version original = repository.getVersion(version.getId());
            Version resolved = original.resolvePreservingPatches(repository);

            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolved);
            for (LibraryAnalyzer.LibraryType type : LibraryAnalyzer.LibraryType.values()) {
                if (!analyzer.has(type))
                    continue;

                if (type == LibraryAnalyzer.LibraryType.OPTIFINE) {
                    String optifinePatchVersion = analyzer.getVersion(type)
                            .map(optifineVersion -> {
                                Matcher matcher = Pattern.compile("^([0-9.]+)_(?<optifine>HD_.+)$").matcher(optifineVersion);
                                return matcher.find() ? matcher.group("optifine") : optifineVersion;
                            })
                            .orElseGet(() -> resolved.getPatches().stream()
                                    .filter(patch -> "optifine".equals(patch.getId()))
                                    .findAny()
                                    .map(Version::getVersion)
                                    .orElse(null));

                    boolean needsReInstallation = version.getLibraries().stream()
                            .anyMatch(library -> !library.hasDownloadURL()
                                    && "optifine".equals(library.getGroupId())
                                    && GameLibrariesTask.shouldDownloadLibrary(repository, version, library, integrityCheck));

                    if (needsReInstallation) {
                        Library installer = new Library(new Artifact("optifine", "OptiFine", gameVersion + "_" + optifinePatchVersion, "installer"));
                        if (GameLibrariesTask.shouldDownloadLibrary(repository, version, installer, integrityCheck)) {
                            tasks.add(installLibraryAsync(gameVersion, original, "optifine", optifinePatchVersion));
                        } else {
                            tasks.add(OptiFineInstallTask.install(this, original, repository.getLibraryFile(version, installer).toPath()));
                        }
                    }
                }
            }

            return Task.allOf(tasks);
        });
    }

    @Override
    public Task<Version> installLibraryAsync(String gameVersion, Version baseVersion, String libraryId, String libraryVersion) {
        if (baseVersion.isResolved()) throw new IllegalArgumentException("Version should not be resolved");

        VersionList<?> versionList = getVersionList(libraryId);
        return Task.fromCompletableFuture(versionList.loadAsync(gameVersion))
                .thenComposeAsync(() -> installLibraryAsync(baseVersion, versionList.getVersion(gameVersion, libraryVersion)
                        .orElseThrow(() -> new IOException("Remote library " + libraryId + " has no version " + libraryVersion))))
                .withStage(String.format("hmcl.install.%s:%s", libraryId, libraryVersion));
    }

    @Override
    public Task<Version> installLibraryAsync(Version baseVersion, RemoteVersion libraryVersion) {
        if (baseVersion.isResolved()) throw new IllegalArgumentException("Version should not be resolved");

        AtomicReference<Version> removedLibraryVersion = new AtomicReference<>();

        return removeLibraryAsync(baseVersion.resolvePreservingPatches(repository), libraryVersion.getLibraryId())
                .thenComposeAsync(version -> {
                    removedLibraryVersion.set(version);
                    return libraryVersion.getInstallTask(this, version);
                })
                .thenApplyAsync(patch -> {
                    if (patch == null) {
                        return removedLibraryVersion.get();
                    } else {
                        return removedLibraryVersion.get().addPatch(patch);
                    }
                })
                .withStage(String.format("hmcl.install.%s:%s", libraryVersion.getLibraryId(), libraryVersion.getSelfVersion()));
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

                    throw new UnsupportedLibraryInstallerException();
                })
                .thenApplyAsync(oldVersion::addPatch);
    }

    public static class UnsupportedLibraryInstallerException extends Exception {
    }

    /**
     * Remove installed library.
     * Will try to remove libraries and patches.
     *
     * @param version not resolved version
     * @param libraryId forge/liteloader/optifine/fabric
     * @return task to remove the specified library
     */
    public Task<Version> removeLibraryAsync(Version version, String libraryId) {
        // MaintainTask requires version that does not inherits from any version.
        // If we want to remove a library in dependent version, we should keep the dependents not changed
        // So resolving this game version to preserve all information in this version.json is necessary.
        if (version.isResolved())
            throw new IllegalArgumentException("removeLibraryWithoutSavingAsync requires non-resolved version");
        Version independentVersion = version.resolvePreservingPatches(repository);

        return Task.supplyAsync(() -> LibraryAnalyzer.analyze(independentVersion).removeLibrary(libraryId).build());
    }

}
