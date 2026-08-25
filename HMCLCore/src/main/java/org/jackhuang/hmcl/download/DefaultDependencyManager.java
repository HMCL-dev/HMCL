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
import java.util.regex.Matcher;

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
                    Path instanceJar = instance.getInstanceJarFile();

                    return Files.notExists(instanceJar) || FileUtils.size(instanceJar) == 0L
                            ? new GameDownloadTask(this, manifest).thenAcceptAsync(
                            cachedJar -> FileUtils.copyFile(cachedJar, instanceJar))
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

            optifine:
            {
                @Nullable GameComponentAnalyzer.Mark mark = instance.getAnalyzer().getMark(GameComponentType.OPTIFINE);
                if (mark == null || mark.version() == null)
                    break optifine;

                String fullVersion = mark.version();
                String patchVersion;

                Matcher matcher = GameComponentAnalyzer.OPTIFINE_VERSION_PATTERN.matcher(fullVersion);
                if (matcher.matches()) {
                    patchVersion = matcher.group("optifine");
                } else {
                    @Nullable GameInstancePatch patch = manifest.findPatch(GameComponentType.OPTIFINE.getPatchId());
                    if (patch != null && patch.version() != null)
                        patchVersion = patch.version();
                    else
                        break optifine;
                }

                boolean needsReInstallation = manifest.getLibraries().stream()
                        .anyMatch(library -> !library.hasDownloadURL()
                                && "optifine".equals(library.groupId())
                                && GameLibrariesTask.shouldDownloadLibrary(repository, manifest, library, integrityCheck));

                if (needsReInstallation) {
                    Library installer = new Library("optifine", "OptiFine", fullVersion, "installer");
                    if (GameLibrariesTask.shouldDownloadLibrary(repository, manifest, installer, integrityCheck)) {
                        tasks.add(installComponentRemoteAsync(instance, original, gameVersion, GameComponentType.OPTIFINE, patchVersion));
                    } else {
                        tasks.add(OptiFineInstallTask.install(
                                this,
                                original,
                                gameVersion,
                                repository.getLayout().getLibraryFile(manifest.id(), installer)));
                    }
                }
            }

            return Task.allOf(tasks);
        });
    }

    /// Installs a component into an unpublished new instance without constructing a
    /// [GameInstance].
    ///
    /// @param baseManifest     the working manifest for this step
    /// @param componentVersion the remote component to install
    /// @return the task producing the updated manifest (not yet committed)
    Task<GameInstanceManifest> installNewInstanceComponentAsync(
            GameInstanceManifest baseManifest,
            RemoteVersion componentVersion) {
        Path modsDirectory = repository.getRunDirectoryForInstallation(baseManifest.id()).resolve("mods");
        return Task.supplyAsync(() -> baseManifest.removeComponent(componentVersion.getComponentType()))
                .thenComposeAsync(manifest -> componentVersion
                        .getInstallTask(this, manifest, modsDirectory)
                        .thenApplyAsync(patch -> patch == null ? manifest : manifest.addPatch(patch)))
                .withStage("hmcl.install.%s:%s".formatted(
                        componentVersion.getComponentType().getPatchId(),
                        componentVersion.getSelfVersion()));
    }

    /// Resolves and installs a component into an unpublished new instance.
    ///
    /// @param baseManifest     the working manifest for this step
    /// @param gameVersion      the Minecraft version used to look up the remote list
    /// @param componentType    the component list id, such as `game` or `forge`
    /// @param componentVersion the component version id
    /// @return the installation task
    Task<GameInstanceManifest> installNewInstanceComponentAsync(
            GameInstanceManifest baseManifest,
            String gameVersion,
            GameComponentType componentType,
            String componentVersion) {
        VersionList<?> versionList = getVersionList(componentType);
        return versionList.loadAsync(gameVersion)
                .thenComposeAsync(() -> installNewInstanceComponentAsync(
                        baseManifest,
                        versionList.getVersion(gameVersion, componentVersion)
                                .orElseThrow(() -> new IOException(
                                        "Remote component " + componentType + " has no version " + componentVersion))))
                .withStage("hmcl.install.%s:%s".formatted(componentType, componentVersion));
    }

    /// Installs a component using a working manifest that may be ahead of the instance's stored state.
    ///
    /// Used by multi-step install pipelines after a previous in-memory remove/install. `instance`
    /// supplies repository identity, mods directory, and detected game version; `baseManifest` is
    /// the draft JSON being edited.
    ///
    /// @param instance         the registered instance being modified
    /// @param baseManifest     the working standalone-oriented manifest for this step
    /// @param componentVersion the remote component to install
    /// @return the task producing the updated manifest (not yet saved)
    public Task<GameInstanceManifest> installComponentRemoteAsync(
            GameInstance instance,
            GameInstanceManifest baseManifest,
            RemoteVersion componentVersion) {
        validateGameInstance(instance);
        if (!instance.getId().equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instance");
        }
        if (!baseManifest.isModifiable()) {
            throw new IllegalArgumentException("Cannot install component into a non-modifiable manifest");
        }

        Path modsDirectory = instance.getModsDirectory();
        return Task.supplyAsync(() -> baseManifest.removeComponent(componentVersion.getComponentType()))
                .thenComposeAsync(manifest -> componentVersion
                        .getInstallTask(this, manifest, modsDirectory)
                        .thenApplyAsync(patch -> patch == null
                                ? manifest
                                : manifest.addPatch(patch).reconstructByPatches()))
                .withStage("hmcl.install.%s:%s".formatted(componentVersion.getComponentType().getPatchId(), componentVersion.getSelfVersion()));
    }

    /// Resolves a remote component by id/version and installs it into the working manifest.
    ///
    /// @param instance         the registered instance being modified
    /// @param baseManifest     the working manifest for this step
    /// @param gameVersion      the Minecraft version used to look up the remote list
    /// @param componentType    the component list id, such as `game` or `forge`
    /// @param componentVersion the component version id
    /// @return the installation task
    public Task<GameInstanceManifest> installComponentRemoteAsync(
            GameInstance instance,
            GameInstanceManifest baseManifest,
            String gameVersion,
            GameComponentType componentType,
            String componentVersion) {
        validateGameInstance(instance);
        if (!instance.getId().equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instance");
        }

        VersionList<?> versionList = getVersionList(componentType);
        return versionList.loadAsync(gameVersion)
                .thenComposeAsync(() -> installComponentRemoteAsync(
                        instance,
                        baseManifest,
                        versionList.getVersion(gameVersion, componentVersion)
                                .orElseThrow(() -> new IOException(
                                        "Remote component " + componentType + " has no version " + componentVersion))))
                .withStage("hmcl.install.%s:%s".formatted(componentType, componentVersion));
    }

    /// Installs a component from a local installer jar into a registered instance.
    ///
    /// @param instance  the target instance
    /// @param installer the local installer jar
    /// @return the task producing the updated manifest (not yet saved)
    public Task<GameInstanceManifest> installComponentLocalAsync(GameInstance instance, Path installer) {
        validateGameInstance(instance);

        GameInstanceManifest baseManifest = instance.getManifest();

        if (!baseManifest.isModifiable()) {
            throw new IllegalArgumentException("Cannot install component into a non-modifiable manifest");
        }

        String gameVersion = instance.getVersion().toString();

        return Task.composeAsync(() -> {
                    try {
                        return CleanroomInstallTask.install(this, baseManifest, gameVersion, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return NeoForgeInstallTask.install(this, baseManifest, gameVersion, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return ForgeInstallTask.install(this, baseManifest, gameVersion, installer);
                    } catch (IOException ignore) {
                    }

                    try {
                        return OptiFineInstallTask.install(this, baseManifest, gameVersion, installer);
                    } catch (IOException ignore) {
                    }

                    throw new UnsupportedLibraryInstallerException();
                })
                .thenApplyAsync(patch -> patch == null
                        ? baseManifest
                        : baseManifest.addPatch(patch).reconstructByPatches());
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
        GameInstanceManifest workingManifest = instance.getManifest();
        validateGameInstance(instance);
        if (!instance.getId().equals(workingManifest.id())) {
            throw new IllegalArgumentException("workingManifest id does not match instance");
        }

        if (!workingManifest.isModifiable()) {
            throw new IllegalArgumentException("Cannot remove component from a non-modifiable manifest");
        }

        return Task.completed(workingManifest.removeComponent(componentType));
    }

}
