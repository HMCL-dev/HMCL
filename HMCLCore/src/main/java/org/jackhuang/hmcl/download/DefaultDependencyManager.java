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

import org.jackhuang.hmcl.download.cleanroom.CleanroomInstallTask;
import org.jackhuang.hmcl.download.forge.ForgeInstallTask;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.download.neoforge.NeoForgeInstallTask;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
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
    public GameBuilder newGameBuilder() {
        return new DefaultGameBuilder(this);
    }

    @Override
    public Task<?> checkGameCompletionAsync(GameInstanceManifest manifest, boolean integrityCheck) {
        return Task.allOf(
                Task.composeAsync(() -> {
                    Path versionJar = repository.getInstanceJar(manifest);

                    return Files.notExists(versionJar) || FileUtils.size(versionJar) == 0L
                            ? new GameDownloadTask(this, null, manifest)
                            : null;
                }).thenComposeAsync(checkPatchCompletionAsync(manifest, integrityCheck)),
                new GameAssetDownloadTask(this, manifest, GameAssetDownloadTask.DOWNLOAD_INDEX_IF_NECESSARY, integrityCheck)
                        .setSignificance(Task.TaskSignificance.MODERATE),
                new GameLibrariesTask(this, manifest, integrityCheck)
        );
    }

    @Override
    public Task<?> checkLibraryCompletionAsync(GameInstanceManifest manifest, boolean integrityCheck) {
        return new GameLibrariesTask(this, manifest, integrityCheck, manifest.getLibraries());
    }

    @Override
    public Task<?> checkPatchCompletionAsync(GameInstanceManifest manifest, boolean integrityCheck) {
        return Task.composeAsync(() -> {
            List<Task<?>> tasks = new ArrayList<>(0);

            String gameVersion = repository.getGameVersion(manifest).orElse(null);
            if (gameVersion == null) return null;

            GameInstanceManifest original = repository.getInstanceManifest(manifest.id());
            GameInstanceManifest.Resolved resolvedInstanceManifest = repository.getResolvedInstanceManifest(manifest.id());

            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolvedInstanceManifest, gameVersion);
            for (LibraryAnalyzer.LibraryType type : LibraryAnalyzer.LibraryType.values()) {
                if (!analyzer.has(type))
                    continue;

                if (type == LibraryAnalyzer.LibraryType.OPTIFINE) {
                    String optifinePatchVersion = analyzer.getVersion(type)
                            .map(optifineVersion -> {
                                Matcher matcher = Pattern.compile("^([0-9.]+)_(?<optifine>HD_.+)$").matcher(optifineVersion);
                                return matcher.find() ? matcher.group("optifine") : optifineVersion;
                            })
                            .orElseGet(() -> resolvedInstanceManifest.standaloneManifest().getPatches().stream()
                                    .filter(patch -> "optifine".equals(patch.id()))
                                    .findAny()
                                    .map(gameInstancePatch -> gameInstancePatch.version())
                                    .orElse(null));

                    boolean needsReInstallation = manifest.getLibraries().stream()
                            .anyMatch(library -> !library.hasDownloadURL()
                                    && "optifine".equals(library.groupId())
                                    && GameLibrariesTask.shouldDownloadLibrary(repository, manifest, library, integrityCheck));

                    if (needsReInstallation) {
                        Library installer = new Library(new Artifact("optifine", "OptiFine", gameVersion + "_" + optifinePatchVersion, "installer"));
                        if (GameLibrariesTask.shouldDownloadLibrary(repository, manifest, installer, integrityCheck)) {
                            tasks.add(installLibraryAsync(gameVersion, original, "optifine", optifinePatchVersion));
                        } else {
                            tasks.add(OptiFineInstallTask.install(this, original, repository.getLibraryFile(manifest, installer)));
                        }
                    }
                }
            }

            return Task.allOf(tasks);
        });
    }

    @Override
    public Task<GameInstanceManifest> installLibraryAsync(String gameVersion, GameInstanceManifest baseVersion, String libraryId, String libraryVersion) {
        VersionList<?> versionList = getVersionList(libraryId);
        return versionList.loadAsync(gameVersion)
                .thenComposeAsync(() -> installLibraryAsync(baseVersion, versionList.getVersion(gameVersion, libraryVersion)
                        .orElseThrow(() -> new IOException("Remote library " + libraryId + " has no version " + libraryVersion))))
                .withStage(String.format("hmcl.install.%s:%s", libraryId, libraryVersion));
    }

    /// Checks if a matching library patch is already installed for the target instance on disk and intact.
    ///
    /// @param baseVersion the base game instance manifest
    /// @param libraryVersion the remote library version to install
    /// @return the existing intact matching patch, or `null` if the library is not installed, version mismatches, or files need repair
    private @Nullable GameInstancePatch getIntactMatchingPatch(GameInstanceManifest baseVersion, RemoteVersion libraryVersion) {
        if (LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId().equals(libraryVersion.getLibraryId()) || !repository.hasInstance(baseVersion.id())) {
            return null;
        }

        try {
            GameInstanceManifest existingManifest = repository.getInstanceManifest(baseVersion.id());
            String currentGameVersion = repository.getGameVersion(existingManifest).orElse(null);
            if (!java.util.Objects.equals(currentGameVersion, libraryVersion.getGameVersion())) {
                return null;
            }

            GameInstancePatch matchingPatch = existingManifest.getPatches().stream()
                    .filter(patch -> libraryVersion.getLibraryId().equals(patch.id())
                            && java.util.Objects.equals(patch.version(), libraryVersion.getSelfVersion()))
                    .findFirst()
                    .orElse(null);
            if (matchingPatch == null) {
                return null;
            }

            List<Library> patchLibraries = matchingPatch.libraries();
            boolean needsRepair = patchLibraries != null && patchLibraries.stream()
                    .filter(Library::appliesToCurrentEnvironment)
                    .anyMatch(lib -> GameLibrariesTask.shouldDownloadLibrary(repository, existingManifest, lib, true));

            return needsRepair ? null : matchingPatch;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public Task<GameInstanceManifest> installLibraryAsync(GameInstanceManifest baseVersion, RemoteVersion libraryVersion) {
        GameInstancePatch matchingPatch = getIntactMatchingPatch(baseVersion, libraryVersion);
        if (matchingPatch != null) {
            return Task.completed(baseVersion.addPatch(matchingPatch));
        }

        AtomicReference<GameInstanceManifest> removedLibraryVersion = new AtomicReference<>();

        return removeLibraryAsync(baseVersion, libraryVersion.getLibraryId())
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

    public Task<GameInstanceManifest> installLibraryAsync(GameInstanceManifest oldVersion, Path installer) {
        return Task
                .composeAsync(() -> {
                    try {
                        return CleanroomInstallTask.install(this, oldVersion, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return NeoForgeInstallTask.install(this, oldVersion, installer);
                    } catch (IOException ignore) {
                    }

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
                .thenApplyAsync(patch -> patch == null ? oldVersion : oldVersion.addPatch(patch));
    }

    public static class UnsupportedLibraryInstallerException extends Exception {
    }

    /**
     * Remove installed library.
     * Will try to remove libraries and patches.
     *
     * @param manifest not resolved instance manifest
     * @param libraryId forge/liteloader/optifine/fabric
     * @return task to remove the specified library
     */
    public Task<GameInstanceManifest> removeLibraryAsync(GameInstanceManifest manifest, String libraryId) {
        // MaintainTask requires version that does not inherits from any version.
        // If we want to remove a library in dependent version, we should keep the dependents not changed
        // So resolving this game version to preserve all information in this version.json is necessary.
        return Task.supplyAsync(() -> {
            GameInstanceManifest independentVersion = repository.resolve(manifest).standaloneManifest();
            String gameVersion = repository.getGameVersion(independentVersion).orElse(null);
            return LibraryAnalyzer.analyze(independentVersion, gameVersion).removeLibrary(libraryId).build();
        });
    }

}
