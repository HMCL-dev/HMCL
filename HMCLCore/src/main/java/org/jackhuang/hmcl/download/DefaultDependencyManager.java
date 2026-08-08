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
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Provides downloads and game-component installation for one game repository.
public class DefaultDependencyManager extends AbstractDependencyManager {

    /// The repository whose layout and registered instances are managed.
    private final DefaultGameRepository repository;

    /// The provider used to resolve remote download URLs and version lists.
    private final DownloadProvider downloadProvider;

    /// The cache used to source and retain downloaded artifacts.
    private final DefaultCacheRepository cacheRepository;

    /// Creates a dependency manager for a repository and download context.
    ///
    /// @param repository       the associated game repository
    /// @param downloadProvider the remote download provider
    /// @param cacheRepository  the artifact cache
    public DefaultDependencyManager(DefaultGameRepository repository, DownloadProvider downloadProvider, DefaultCacheRepository cacheRepository) {
        this.repository = repository;
        this.downloadProvider = downloadProvider;
        this.cacheRepository = cacheRepository;
    }

    /// Ensures that an instance belongs to this manager's repository.
    ///
    /// @param instance the instance to validate
    /// @throws IllegalArgumentException if the instance belongs to another repository
    public void validateGameInstance(GameInstance instance) {
        if (instance.getRepository() != repository) {
            throw new IllegalArgumentException("Game instance and dependency manager belong to different repositories");
        }
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
    public Task<?> checkGameCompletionAsync(
            GameInstance instance,
            GameInstanceManifest manifest,
            boolean integrityCheck) {
        validateGameInstance(instance);

        return Task.allOf(
                Task.composeAsync(() -> {
                    Path versionJar = instance.getInstanceJarFile();

                    return Files.notExists(versionJar) || FileUtils.size(versionJar) == 0L
                            ? new GameDownloadTask(this, null, manifest, versionJar)
                            : null;
                }).thenComposeAsync(checkPatchCompletionAsync(instance, manifest, integrityCheck)),
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
    public Task<?> checkPatchCompletionAsync(
            GameInstance instance,
            GameInstanceManifest manifest,
            boolean integrityCheck) {
        validateGameInstance(instance);

        return Task.composeAsync(() -> {
            List<Task<?>> tasks = new ArrayList<>(0);

            GameVersionNumber detectedVersion = instance.getVersion();
            if (detectedVersion == GameVersionNumber.unknown()) return null;
            String gameVersion = detectedVersion.toString();

            GameInstanceManifest original = instance.getManifest();
            GameInstanceManifest.Resolved resolvedInstanceManifest = instance.getResolvedManifest();
            GameComponentAnalyzer analyzer = instance.getAnalyzer();
            for (GameComponentType type : GameComponentType.values()) {
                if (!analyzer.has(type))
                    continue;

                if (type == GameComponentType.OPTIFINE) {
                    String optifinePatchVersion = Optional.ofNullable(analyzer.getVersion(type))                            .map(optifineVersion -> {
                                Matcher matcher = Pattern.compile("^([0-9.]+)_(?<optifine>HD_.+)$").matcher(optifineVersion);
                                return matcher.find() ? matcher.group("optifine") : optifineVersion;
                            })
                            .orElseGet(() -> resolvedInstanceManifest.standaloneManifest().getPatches().stream()
                                    .filter(patch -> "optifine".equals(patch.id()))
                                    .findAny()
                                    .map(GameInstancePatch::version)
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
                            tasks.add(OptiFineInstallTask.install(this, original, repository.getLayout().getLibraryFile(manifest.id(), installer)));
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

    @Override
    public Task<GameInstanceManifest> installLibraryAsync(GameInstanceManifest baseVersion, RemoteVersion libraryVersion) {
        AtomicReference<GameInstanceManifest> removedLibraryManifest = new AtomicReference<>();

        return removeLibraryAsync(baseVersion, libraryVersion.getComponentType())
                .thenComposeAsync(manifest -> {
                    removedLibraryManifest.set(manifest);
                    return libraryVersion.getInstallTask(this, manifest, modsDirectoryFor(manifest));
                })
                .thenApplyAsync(patch -> {
                    if (patch == null) {
                        return removedLibraryManifest.get();
                    } else {
                        return removedLibraryManifest.get().addPatch(patch);
                    }
                })
                .withStage(String.format("hmcl.install.%s:%s", libraryVersion.getLibraryId(), libraryVersion.getSelfVersion()));
    }

    /// Resolves the mods directory for the instance identified by `manifest`.
    ///
    /// Prefer the registered [GameInstance] when present so isolation/run-directory policy is
    /// honored. Falls back to the shared repository base directory when the instance is not yet
    /// indexed (should be rare after [org.jackhuang.hmcl.download.DefaultGameBuilder] registers a
    /// placeholder instance).
    ///
    /// @param manifest the install target manifest
    /// @return the mods directory path
    private Path modsDirectoryFor(GameInstanceManifest manifest) {
        DefaultGameInstance instance = repository.getSnapshot().findInstance(manifest.id());
        if (instance != null) {
            return instance.getModsDirectory();
        }
        return repository.getBaseDirectory().resolve("mods");
    }

    /// Creates a task that detects and runs a supported local library installer.
    ///
    /// @param oldVersion the manifest to which the installed patch will be added
    /// @param installer  the local installer jar
    /// @return the task producing the updated manifest
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

    /// Indicates that a local library installer is not recognized by any supported installer.
    public static class UnsupportedLibraryInstallerException extends Exception {

        /// Creates an unsupported-installer exception.
        public UnsupportedLibraryInstallerException() {
        }
    }

    /// Creates a task that removes a loader's libraries and patch from a manifest.
    ///
    /// @param manifest  the unresolved instance manifest
    /// @param componentType the patch identifier, such as `forge`, `optifine`, or `fabric`
    /// @return the task producing the updated independent manifest
    public Task<GameInstanceManifest> removeLibraryAsync(GameInstanceManifest manifest, GameComponentType componentType) {
        // Library removal operates on a standalone manifest so inherited launch metadata is retained.
        return Task.supplyAsync(() -> {
            GameInstanceManifest independentVersion = repository.resolve(manifest).standaloneManifest();
            GameVersionNumber gameVersion = repository.getGameVersion(independentVersion)
                    .map(GameVersionNumber::asGameVersion)
                    .orElse(null);
            return GameComponentAnalyzer.analyze(independentVersion, gameVersion).removeLibrary(componentType);
        });
    }

}
