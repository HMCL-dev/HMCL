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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Provides downloads and game-component installation for one game repository.
@NotNullByDefault
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
    public Task<?> checkComponentCompletionAsync(GameInstanceManifest manifest, boolean integrityCheck) {
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
            if (detectedVersion.equals(GameVersionNumber.unknown())) return null;
            String gameVersion = detectedVersion.toString();

            GameInstanceManifest original = instance.getManifest();
            for (GameComponentType type : GameComponentType.values()) {
                if (!instance.hasComponent(type))
                    continue;

                if (type == GameComponentType.OPTIFINE) {
                    @Nullable String optifinePatchVersion = Optional.ofNullable(instance.getComponentVersion(type)).map(optifineVersion -> {
                                Matcher matcher = Pattern.compile("^([0-9.]+)_(?<optifine>HD_.+)$").matcher(optifineVersion);
                                return matcher.find() ? matcher.group("optifine") : optifineVersion;
                            })
                            .orElseGet(() -> instance.getResolvedManifest().standaloneManifest().getPatches().stream()
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
                            tasks.add(installComponentAsync(instance, original, gameVersion, "optifine", optifinePatchVersion));
                        } else {
                            tasks.add(OptiFineInstallTask.install(this, original, repository.getLayout().getLibraryFile(manifest.id(), installer)));
                        }
                    }
                }
            }

            return Task.allOf(tasks);
        });
    }

    /// Installs a component into a registered instance using its stored manifest as the base.
    ///
    /// @param instance       the target instance; must belong to this manager's repository
    /// @param libraryVersion the remote component to install
    /// @return the task producing the updated standalone manifest (not yet saved)
    public Task<GameInstanceManifest> installComponentAsync(GameInstance instance, RemoteVersion libraryVersion) {
        validateGameInstance(instance);
        return installComponentAsync(instance, instance.getManifest(), libraryVersion);
    }

    /// Installs a component using a working manifest that may be ahead of the instance's stored state.
    ///
    /// Used by multi-step install pipelines after a previous in-memory remove/install. `instance`
    /// supplies repository identity, mods directory, and detected game version; `baseManifest` is
    /// the draft JSON being edited.
    ///
    /// @param instance       the registered instance being modified
    /// @param baseManifest   the working standalone-oriented manifest for this step
    /// @param libraryVersion the remote component to install
    /// @return the task producing the updated manifest (not yet saved)
    public Task<GameInstanceManifest> installComponentAsync(
            GameInstance instance,
            GameInstanceManifest baseManifest,
            RemoteVersion libraryVersion) {
        validateGameInstance(instance);
        if (!instance.getId().equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instance");
        }

        Path modsDirectory = instance.getModsDirectory();

        return removeComponentAsync(instance, baseManifest, libraryVersion.getComponentType())
                .thenComposeAsync(manifest -> libraryVersion
                        .getInstallTask(this, manifest, modsDirectory)
                        .thenApplyAsync(patch -> patch == null ? manifest : manifest.addPatch(patch)))
                .withStage(String.format("hmcl.install.%s:%s", libraryVersion.getLibraryId(), libraryVersion.getSelfVersion()));
    }

    /// Installs a component into an unpublished new instance without constructing a
    /// [GameInstance].
    ///
    /// @param instanceId     the unpublished instance id
    /// @param baseManifest   the working manifest for this step
    /// @param gameVersion    the Minecraft version used for component analysis
    /// @param libraryVersion the remote component to install
    /// @return the task producing the updated manifest (not yet committed)
    Task<GameInstanceManifest> installNewInstanceComponentAsync(
            GameInstanceID instanceId,
            GameInstanceManifest baseManifest,
            String gameVersion,
            RemoteVersion libraryVersion) {
        if (!instanceId.equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instanceId");
        }

        Path modsDirectory = repository.getRunDirectoryForInstallation(instanceId).resolve("mods");
        return removeNewInstanceComponentAsync(
                baseManifest,
                GameVersionNumber.asGameVersion(gameVersion),
                libraryVersion.getComponentType())
                .thenComposeAsync(manifest -> libraryVersion
                        .getInstallTask(this, manifest, modsDirectory)
                        .thenApplyAsync(patch -> patch == null ? manifest : manifest.addPatch(patch)))
                .withStage(String.format(
                        "hmcl.install.%s:%s",
                        libraryVersion.getLibraryId(),
                        libraryVersion.getSelfVersion()));
    }

    /// Resolves and installs a component into an unpublished new instance.
    ///
    /// @param instanceId     the unpublished instance id
    /// @param baseManifest   the working manifest for this step
    /// @param gameVersion    the Minecraft version used to look up the remote list
    /// @param libraryId      the component list id, such as `game` or `forge`
    /// @param libraryVersion the component version id
    /// @return the installation task
    Task<GameInstanceManifest> installNewInstanceComponentAsync(
            GameInstanceID instanceId,
            GameInstanceManifest baseManifest,
            String gameVersion,
            String libraryId,
            String libraryVersion) {
        if (!instanceId.equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instanceId");
        }

        VersionList<?> versionList = getVersionList(libraryId);
        return versionList.loadAsync(gameVersion)
                .thenComposeAsync(() -> installNewInstanceComponentAsync(
                        instanceId,
                        baseManifest,
                        gameVersion,
                        versionList.getVersion(gameVersion, libraryVersion)
                                .orElseThrow(() -> new IOException(
                                        "Remote library " + libraryId + " has no version " + libraryVersion))))
                .withStage(String.format("hmcl.install.%s:%s", libraryId, libraryVersion));
    }

    /// Removes one component from an unpublished new instance manifest.
    ///
    /// @param workingManifest the manifest being edited
    /// @param gameVersion     the Minecraft version used for component analysis
    /// @param componentType   the component to remove
    /// @return the task producing the updated standalone manifest
    private Task<GameInstanceManifest> removeNewInstanceComponentAsync(
            GameInstanceManifest workingManifest,
            GameVersionNumber gameVersion,
            GameComponentType componentType) {
        return Task.supplyAsync(() -> {
            GameInstanceManifest standalone = workingManifest.inheritsFrom() == null
                    ? workingManifest
                    : repository.resolve(workingManifest).standaloneManifest();
            return GameComponentAnalyzer.analyze(standalone, gameVersion).removeLibrary(componentType);
        });
    }

    /// Resolves a remote component by id/version and installs it into the working manifest.
    ///
    /// @param instance       the registered instance being modified
    /// @param baseManifest   the working manifest for this step
    /// @param gameVersion    the Minecraft version used to look up the remote list
    /// @param libraryId      the component list id, such as `game` or `forge`
    /// @param libraryVersion the component version id
    /// @return the installation task
    public Task<GameInstanceManifest> installComponentAsync(
            GameInstance instance,
            GameInstanceManifest baseManifest,
            String gameVersion,
            String libraryId,
            String libraryVersion) {
        validateGameInstance(instance);
        if (!instance.getId().equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instance");
        }

        VersionList<?> versionList = getVersionList(libraryId);
        return versionList.loadAsync(gameVersion)
                .thenComposeAsync(() -> installComponentAsync(
                        instance,
                        baseManifest,
                        versionList.getVersion(gameVersion, libraryVersion)
                                .orElseThrow(() -> new IOException(
                                        "Remote library " + libraryId + " has no version " + libraryVersion))))
                .withStage(String.format("hmcl.install.%s:%s", libraryId, libraryVersion));
    }

    @Override
    public Task<GameInstanceManifest> installComponentAsync(
            String gameVersion,
            GameInstanceManifest baseManifest,
            String libraryId,
            String libraryVersion) {
        return installComponentAsync(
                repository.getInstance(baseManifest.id()),
                baseManifest,
                gameVersion,
                libraryId,
                libraryVersion);
    }

    @Override
    public Task<GameInstanceManifest> installComponentAsync(
            GameInstanceManifest baseVersion,
            RemoteVersion libraryVersion) {
        return installComponentAsync(repository.getInstance(baseVersion.id()), baseVersion, libraryVersion);
    }

    /// Installs a component from a local installer jar into a registered instance.
    ///
    /// @param instance  the target instance
    /// @param installer the local installer jar
    /// @return the task producing the updated manifest (not yet saved)
    public Task<GameInstanceManifest> installComponentAsync(GameInstance instance, Path installer) {
        validateGameInstance(instance);
        return installComponentAsync(instance, instance.getManifest(), installer);
    }

    /// Installs a component from a local installer jar into a working manifest.
    ///
    /// @param instance     the registered instance (paths / identity)
    /// @param baseManifest the working manifest for this step
    /// @param installer    the local installer jar
    /// @return the task producing the updated manifest (not yet saved)
    public Task<GameInstanceManifest> installComponentAsync(
            GameInstance instance,
            GameInstanceManifest baseManifest,
            Path installer) {
        validateGameInstance(instance);
        if (!instance.getId().equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instance");
        }

        return Task
                .composeAsync(() -> {
                    try {
                        return CleanroomInstallTask.install(this, baseManifest, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return NeoForgeInstallTask.install(this, baseManifest, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return ForgeInstallTask.install(this, baseManifest, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return OptiFineInstallTask.install(this, baseManifest, installer);
                    } catch (IOException ignore) {
                    }

                    throw new UnsupportedLibraryInstallerException();
                })
                .thenApplyAsync(patch -> patch == null ? baseManifest : baseManifest.addPatch(patch));
    }

    /// Indicates that a local library installer is not recognized by any supported installer.
    public static class UnsupportedLibraryInstallerException extends Exception {

        /// Creates an unsupported-installer exception.
        public UnsupportedLibraryInstallerException() {
        }
    }

    /// Removes a component from a registered instance using its current stored manifest.
    ///
    /// @param instance      the target instance
    /// @param componentType the component to remove
    /// @return the task producing the updated standalone manifest (not yet saved)
    public Task<GameInstanceManifest> removeComponentAsync(GameInstance instance, GameComponentType componentType) {
        validateGameInstance(instance);
        return removeComponentAsync(instance, instance.getManifest(), componentType);
    }

    /// Removes a component from a working manifest bound to a registered instance.
    ///
    /// When `workingManifest` is the instance's stored manifest, edits its resolved standalone view;
    /// otherwise edits the independent draft (resolving inheritance if still present).
    ///
    /// @param instance        the registered instance
    /// @param workingManifest the draft being edited
    /// @param componentType   the component to remove
    /// @return the task producing the updated standalone manifest (not yet saved)
    public Task<GameInstanceManifest> removeComponentAsync(
            GameInstance instance,
            GameInstanceManifest workingManifest,
            GameComponentType componentType) {
        validateGameInstance(instance);
        if (!instance.getId().equals(workingManifest.id())) {
            throw new IllegalArgumentException("workingManifest id does not match instance");
        }

        return Task.supplyAsync(() -> {
            GameInstanceManifest standalone;
            if (workingManifest.equals(instance.getManifest())) {
                standalone = instance.getResolvedManifest().standaloneManifest();
            } else if (workingManifest.inheritsFrom() == null) {
                standalone = workingManifest;
            } else {
                standalone = repository.resolve(workingManifest).standaloneManifest();
            }

            return GameComponentAnalyzer.analyze(standalone, instance.getVersion()).removeLibrary(componentType);
        });
    }

}
