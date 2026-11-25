// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    public DefaultGameBuilder newGameBuilder(GameInstanceID instanceId) {
        GameInstanceManifest initialManifest = new GameInstanceManifest(instanceId);
        DefaultGameRepositoryDraft draft = openGameBuilderDraft(initialManifest, null);
        return new DefaultGameBuilder(this, instanceId, null, draft, initialManifest);
    }

    @Override
    public DefaultGameBuilder newGameBuilder(GameInstance instance) {
        validateGameInstance(instance);
        DefaultGameInstance updateTarget = (DefaultGameInstance) instance;

        GameInstanceManifest initialManifest = new GameInstanceManifest(updateTarget.getId());
        DefaultGameRepositoryDraft draft = openGameBuilderDraft(initialManifest, updateTarget);
        return new DefaultGameBuilder(
                this, updateTarget.getId(), updateTarget, draft, initialManifest);
    }

    /// Reserves the empty target manifest in an exclusive draft. A null `updateTarget` requires a
    /// new id. An update requires the exact instance from the current snapshot.
    ///
    /// @throws IllegalArgumentException if `updateTarget` belongs to another repository
    /// @throws IllegalStateException    if the target conflicts with the selected operation, cannot
    ///                                  be reserved, or another repository draft is open
    protected DefaultGameRepositoryDraft openGameBuilderDraft(
            GameInstanceManifest initialManifest,
            @Nullable DefaultGameInstance updateTarget) {
        if (updateTarget != null) {
            validateGameInstance(updateTarget);
        }

        GameInstanceID instanceId = initialManifest.id();
        DefaultGameRepositoryDraft draft = repository.openDraft();
        try {
            @Nullable DefaultGameInstance currentInstance = draft.getBaseSnapshot().findInstance(instanceId);
            if (updateTarget == null) {
                if (currentInstance != null) {
                    throw new IllegalStateException("Game instance already exists: " + instanceId);
                }
            } else if (currentInstance == null) {
                throw new IllegalStateException("Game instance no longer exists: " + instanceId);
            } else if (currentInstance != updateTarget) {
                throw new IllegalStateException("Game instance has changed: " + instanceId);
            }
            draft.put(initialManifest);
            return draft;
        } catch (IOException e) {
            abortDraftAfterFailure(draft, e);
            throw new IllegalStateException("Cannot reserve game instance " + instanceId, e);
        } catch (RuntimeException | Error failure) {
            abortDraftAfterFailure(draft, failure);
            throw failure;
        }
    }

    /// Aborts a draft and attaches any cleanup failure to the triggering failure.
    private static void abortDraftAfterFailure(
            DefaultGameRepositoryDraft draft,
            Throwable failure) {
        try {
            draft.abort();
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /// Updates the published instance with a manifest of the same id. The manifest and any new
    /// client JAR are committed together. Failure or cancellation before commit preserves the
    /// installed files and releases the draft.
    ///
    /// The instance's own JAR is replaced when switching from a referenced JAR or changing the
    /// client download. Downloads are compared by SHA-1 when either has a checksum, otherwise by
    /// URL and size. Other instances' JARs and shared cache files are retained.
    public <E extends Exception> Task<@Nullable Void> updateInstanceAsync(
            GameInstanceID instanceId,
            ExceptionalFunction<GameInstance, Task<GameInstanceManifest>, E> updater) {
        return new Task<@Nullable Void>() {
            /// {@inheritDoc}
            @Override
            public @Nullable Void execute(TaskExecutionContext context) throws Exception {
                try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
                    GameInstance instance = draft.getBaseSnapshot().getInstance(instanceId);
                    Task<GameInstanceManifest> update = Objects.requireNonNull(updater.apply(instance), "Instance updater returned null");
                    GameInstanceManifest manifest = context.run(update);
                    if (!instanceId.equals(manifest.id())) {
                        throw new IllegalArgumentException(
                                "Instance updater changed id from " + instanceId + " to " + manifest.id());
                    }
                    GameInstanceManifest previous = instance.getResolvedManifest();
                    GameInstanceManifest resolved = draft.getBaseSnapshot().resolve(manifest);
                    draft.put(manifest);
                    if (instanceId.equals(resolved.jar())
                            && (!instanceId.equals(previous.jar())
                            || !isSameClientDownload(previous.getDownloadInfo(), resolved.getDownloadInfo()))) {
                        draft.putPrimaryJar(instanceId, context.run(new GameDownloadTask(DefaultDependencyManager.this, resolved)));
                    }
                    context.commit();
                    draft.commit();
                    return null;
                }
            }
        }.setName("Update instance " + instanceId).setSignificance(TaskSignificance.MODERATE);
    }

    /// Compares client identity by checksum, falling back to URL and size when neither has one.
    private static boolean isSameClientDownload(DownloadInfo previous, DownloadInfo updated) {
        @Nullable String previousSha1 = previous.getSha1();
        @Nullable String updatedSha1 = updated.getSha1();
        if (previousSha1 != null || updatedSha1 != null) {
            return previousSha1 != null && previousSha1.equalsIgnoreCase(updatedSha1);
        }
        return previous.getUrl().equals(updated.getUrl()) && previous.getSize() == updated.getSize();
    }

    @Override
    public Task<?> checkGameCompletionAsync(
            GameInstance instance,
            GameInstanceManifest manifest,
            boolean integrityCheck) {
        validateGameInstance(instance);

        return Task.allOf(
                Task.composeOptionalAsync(() -> {
                    Path instanceJar = instance.getInstanceJarFile();

                    return Files.notExists(instanceJar) || FileUtils.size(instanceJar) == 0L
                            ? new GameDownloadTask(this, manifest).thenAcceptAsync(
                            cachedJar -> FileUtils.copyFile(cachedJar, instanceJar))
                            : null;
                }).thenCompose(checkPatchCompletionAsync(instance, manifest, integrityCheck)),
                new GameAssetDownloadTask(this, manifest, GameAssetDownloadTask.DOWNLOAD_INDEX_IF_NECESSARY, integrityCheck)
                        .setSignificance(TaskSignificance.MODERATE),
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

        return Task.composeOptionalAsync(() -> {
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

                @Nullable GameInstancePatch patch = original.findPatch(GameComponentType.OPTIFINE);
                String fullVersion;
                String patchVersion;
                if (patch != null && patch.version() != null) {
                    patchVersion = patch.version();
                    fullVersion = gameVersion + "_" + patchVersion;
                } else {
                    fullVersion = mark.version();
                    Matcher matcher = GameComponentAnalyzer.OPTIFINE_VERSION_PATTERN.matcher(fullVersion);
                    if (matcher.matches()) {
                        patchVersion = matcher.group("optifine");
                    } else {
                        break optifine;
                    }
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

    /// Installs a component into an unpublished working manifest without constructing a
    /// [GameInstance].
    ///
    /// Installing a game patch selects this instance's own client JAR so that
    /// [#updateInstanceAsync] can materialize the new client when publishing the update.
    /// Other component installations retain the existing JAR reference.
    ///
    /// @param baseManifest     the working manifest for this step
    /// @param modsDirectory    the mods directory to use during installation
    /// @param componentVersion the remote component to install
    /// @return the task producing the updated manifest (not yet committed)
    Task<GameInstanceManifest> installUnpublishedComponentAsync(
            GameInstanceManifest baseManifest,
            Path modsDirectory,
            ComponentRemoteVersion componentVersion) {
        return Task.composeAsync(() -> {
                    GameInstanceManifest manifest = baseManifest.removeComponent(componentVersion.getComponentType());
                    return componentVersion
                            .getInstallTask(this, manifest, modsDirectory)
                            .thenApplyAsync(patch -> {
                                if (patch == null) {
                                    return manifest;
                                }

                                GameInstanceManifest updated = manifest
                                        .withBaseGamePatch(componentVersion.getGameVersion().toString())
                                        .addPatch(patch).reconstructByPatches();
                                return componentVersion.getComponentType() == GameComponentType.GAME
                                        ? updated.withJar(updated.id())
                                        : updated;
                            });
                })
                .withStage(InstallStages.component(componentVersion.getComponentType(), componentVersion.getSelfVersion()));
    }

    /// Resolves and installs a component into an unpublished working manifest.
    ///
    /// @param baseManifest     the working manifest for this step
    /// @param modsDirectory    the mods directory to use during installation
    /// @param gameVersion      the Minecraft version used to look up the remote list
    /// @param componentType    the component list id, such as `game` or `forge`
    /// @param componentVersion the component version id
    /// @return the installation task
    Task<GameInstanceManifest> installUnpublishedComponentAsync(
            GameInstanceManifest baseManifest,
            Path modsDirectory,
            String gameVersion,
            GameComponentType componentType,
            String componentVersion) {
        return downloadProvider.getVersionsAsync(componentType, componentType == GameComponentType.GAME ? null : GameVersionNumber.asGameVersion(gameVersion), false)
                .thenComposeAsync(versions -> installUnpublishedComponentAsync(
                        baseManifest,
                        modsDirectory,
                        Optional.ofNullable(versions.getRemoteVersion(componentVersion))
                                .orElseThrow(() -> new IOException(
                                        "Remote component " + componentType + " has no version " + componentVersion))))
                .withStage(InstallStages.component(componentType, componentVersion));
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
            ComponentRemoteVersion componentVersion) {
        validateGameInstance(instance);
        if (!instance.getId().equals(baseManifest.id())) {
            throw new IllegalArgumentException("baseManifest id does not match instance");
        }
        if (!baseManifest.isModifiable()) {
            throw new IllegalArgumentException("Cannot install component into a non-modifiable manifest");
        }

        Path modsDirectory = instance.getModsDirectory();
        return installUnpublishedComponentAsync(baseManifest, modsDirectory, componentVersion);
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

        return downloadProvider.getVersionsAsync(componentType, componentType == GameComponentType.GAME ? null : GameVersionNumber.asGameVersion(gameVersion), false)
                .thenComposeAsync(versions -> installComponentRemoteAsync(
                        instance,
                        baseManifest,
                        Optional.ofNullable(versions.getRemoteVersion(componentVersion))
                                .orElseThrow(() -> new IOException(
                                        "Remote component " + componentType + " has no version " + componentVersion))))
                .withStage(InstallStages.component(componentType, componentVersion));
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

        return Task.composeAsync(() -> {
            String gameVersion = instance.getVersion().toString();
            return detectLocalInstaller(baseManifest, gameVersion, installer)
                    .thenApplyAsync(patch -> patch == null
                            ? baseManifest
                            : baseManifest.withBaseGamePatch(gameVersion).addPatch(patch).reconstructByPatches());
        });
    }

    /// Selects the first installer that recognizes the local archive.
    private Task<GameInstancePatch> detectLocalInstaller(GameInstanceManifest baseManifest, String gameVersion, Path installer)
            throws VersionMismatchException, UnsupportedInstallationException, UnsupportedLibraryInstallerException {
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
        if (!instance.getId().equals(workingManifest.id())) {
            throw new IllegalArgumentException("workingManifest id does not match instance");
        }

        if (!workingManifest.isModifiable()) {
            throw new IllegalArgumentException("Cannot remove component from a non-modifiable manifest");
        }

        return new Task<>() {
            /// {@inheritDoc}
            @Override
            public GameInstanceManifest execute(TaskExecutionContext context) {
                return workingManifest.removeComponent(componentType);
            }
        };
    }

}
