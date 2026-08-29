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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/// Installs a new game instance or replaces an existing instance in an exclusive
/// [GameRepositoryDraft], then publishes the completed instance once.
///
/// Construction immediately reserves the target in an exclusive draft. An update requires the
/// supplied snapshot-bound instance to remain the exact currently published object.
///
/// Replacement starts from an empty manifest with the target id and installs only the configured
/// components. It retains the existing run directory unless isolation is explicitly enabled.
///
/// Shared libraries, assets, and download caches may remain after failure. The instance manifest
/// and primary JAR enter the instance tree only when the draft commits.
@NotNullByDefault
public class DefaultGameBuilder extends GameBuilder {

    /// Dependency manager used for component installation and repository access.
    private final DefaultDependencyManager dependencyManager;

    /// Id of the instance to create or replace.
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    /// Exclusive repository draft retained until the build task takes ownership or this builder closes.
    private final DefaultGameRepositoryDraft draft;

    /// Empty manifest reserved in the draft for the target instance.
    private final GameInstanceManifest initialManifest;

    /// Whether ownership of [#draft] has been transferred to the task returned by [#buildAsync()].
    private boolean draftTransferred;

    /// Whether instance isolation was requested for this build.
    protected boolean isolationEnabled;

    /// Creates a builder for installing a new instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param instanceId        the id of the new instance
    /// @throws IllegalStateException if the id is already registered, its root cannot be reserved,
    ///                               or another repository draft is open
    public DefaultGameBuilder(DefaultDependencyManager dependencyManager, GameInstanceID instanceId) {
        this.dependencyManager = dependencyManager;
        this.instanceId = instanceId;
        this.updateTarget = null;
        this.initialManifest = new GameInstanceManifest(instanceId);
        this.draft = openDraft(dependencyManager, instanceId, null, initialManifest);
    }

    /// Creates a builder for replacing the components of an existing instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param instance          the existing instance selecting update mode and the target id
    /// @throws IllegalArgumentException if `instance` belongs to another repository
    /// @throws IllegalStateException    if `instance` is no longer the exact published instance or
    ///                                  another repository draft is open
    public DefaultGameBuilder(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        dependencyManager.validateGameInstance(instance);
        this.dependencyManager = dependencyManager;
        this.instanceId = instance.getId();
        this.updateTarget = instance;
        this.initialManifest = new GameInstanceManifest(instanceId);
        this.draft = openDraft(dependencyManager, instanceId, instance, initialManifest);
    }

    /// Returns the dependency manager used by this builder.
    ///
    /// @return the dependency manager
    public DefaultDependencyManager getDependencyManager() {
        return dependencyManager;
    }

    /// {@inheritDoc}
    @Override
    @Contract("-> this")
    public DefaultGameBuilder enableIsolation() {
        checkOpen();
        isolationEnabled = true;
        return this;
    }

    /// {@inheritDoc}
    ///
    /// Installs the configured game and optional loaders into the draft reserved by this builder,
    /// resolves the launch view, and commits it with the original patches once. Failure or
    /// cancellation aborts the transferred draft.
    ///
    /// @return the build task
    /// @throws IllegalStateException if the configured game version is absent, the builder is
    ///                               closed, or this method has already returned a task
    @Override
    public Task<?> buildAsync() {
        checkOpen();
        try {
            @Nullable String gameVersion = (String) components.get(GameComponentType.GAME);
            if (gameVersion == null)
                throw new IllegalStateException("GameBuilder.gameVersion must be set");

            var hints = new ArrayList<Task.StagesHint>();

            components.forEach((componentType, version) -> {
                hints.add(new Task.StagesHint(
                        "hmcl.install.%s:%s".formatted(
                                componentType.getPatchId(),
                                version instanceof ComponentRemoteVersion remoteVersion
                                        ? remoteVersion.getSelfVersion()
                                        : (String) version)));

                if (componentType == GameComponentType.GAME) {
                    hints.add(new Task.StagesHint("hmcl.install.libraries"));
                    hints.add(new Task.StagesHint("hmcl.install.assets"));
                }
            });

            DefaultGameRepository repository = dependencyManager.getGameRepository();
            Path runDirectory = isolationEnabled
                    ? repository.getLayout().getInstanceRoot(instanceId)
                    : updateTarget != null
                            ? updateTarget.getRunDirectory()
                            : repository.getBaseDirectory();
            Path modsDirectory = runDirectory.resolve("mods");

            Task<GameInstanceManifest> libraryTask = dependencyManager.installUnpublishedComponentAsync(
                    initialManifest, modsDirectory, gameVersion, GameComponentType.GAME, gameVersion);

            for (Map.Entry<GameComponentType, Object> entry : components.entrySet()) {
                GameComponentType componentType = entry.getKey();
                if (componentType == GameComponentType.GAME)
                    continue;

                if (entry.getValue() instanceof ComponentRemoteVersion remoteVersion) {
                    libraryTask = libraryTask.thenComposeAsync(manifest ->
                            dependencyManager.installUnpublishedComponentAsync(
                                    manifest, modsDirectory, remoteVersion));
                } else if (entry.getValue() instanceof String version) {
                    libraryTask = libraryTask.thenComposeAsync(manifest ->
                            dependencyManager.installUnpublishedComponentAsync(
                                    manifest, modsDirectory, gameVersion, componentType, version));
                } else {
                    throw new AssertionError("Unexpected version type: " + entry.getValue().getClass());
                }
            }

            Task<?> buildTask = libraryTask.thenComposeAsync(manifest -> {
                        GameInstanceManifest.Resolved resolved = draft.getBaseSnapshot().resolve(manifest);
                        return new GameDownloadTask(dependencyManager, resolved.launchManifest())
                                .thenApplyAsync(minecraftJar -> {
                                    draft.put(resolved.launchManifest().withPatches(manifest.patches()));
                                    draft.putPrimaryJar(instanceId, minecraftJar);
                                    DefaultGameInstance instance = draft.commit().getInstance(instanceId);
                                    onInstanceCommitted(instance);
                                    return instance;
                                });
                    })
                    .whenComplete(exception -> {
                        if (draft.isOpen()) {
                            draft.abort();
                        }
                    })
                    .withStagesHints(hints);
            draftTransferred = true;
            return buildTask;
        } catch (RuntimeException | Error failure) {
            abortAfterSetupFailure(draft, failure);
            throw failure;
        }
    }

    /// {@inheritDoc}
    ///
    /// @throws UncheckedIOException if reserved instance files cannot be cleaned up
    @Override
    public void close() {
        if (draftTransferred || !draft.isOpen()) {
            return;
        }

        try {
            draft.abort();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot abort game builder draft", e);
        }
    }

    /// Performs repository-specific initialization after an instance is committed.
    ///
    /// This method is invoked after the repository has published the committed snapshot and before
    /// the build task completes. The default implementation does nothing. An unchecked exception
    /// prevents the task from completing successfully but does not roll back the committed instance.
    ///
    /// @param instance the committed instance from the published snapshot
    protected void onInstanceCommitted(DefaultGameInstance instance) {
    }

    /// {@inheritDoc}
    @Override
    protected final void checkOpen() {
        if (draftTransferred) {
            throw new IllegalStateException("GameBuilder has already created its build task");
        }
        if (!draft.isOpen()) {
            throw new IllegalStateException("GameBuilder is closed");
        }
    }

    /// Opens an exclusive draft, validates the exact update snapshot, and reserves the target.
    ///
    /// @param dependencyManager the dependency manager whose repository owns the draft
    /// @param instanceId        the target instance id
    /// @param updateTarget      the exact published instance required for an update, or `null`
    /// @param initialManifest   the empty target manifest to retain in the draft
    /// @return the open draft containing `initialManifest`
    /// @throws IllegalStateException if the target conflicts with the selected operation or cannot
    ///                               be reserved
    private static DefaultGameRepositoryDraft openDraft(
            DefaultDependencyManager dependencyManager,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            GameInstanceManifest initialManifest) {
        DefaultGameRepositoryDraft draft = dependencyManager.getGameRepository().openDraft();
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
            abortAfterSetupFailure(draft, e);
            throw new IllegalStateException("Cannot reserve game instance " + instanceId, e);
        } catch (RuntimeException | Error failure) {
            abortAfterSetupFailure(draft, failure);
            throw failure;
        }
    }

    /// Aborts a draft after target validation or initial reservation fails.
    ///
    /// @param draft   the draft to abort
    /// @param failure the setup failure that receives any cleanup failure as suppressed
    private static void abortAfterSetupFailure(
            DefaultGameRepositoryDraft draft,
            Throwable failure) {
        try {
            draft.abort();
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

}
