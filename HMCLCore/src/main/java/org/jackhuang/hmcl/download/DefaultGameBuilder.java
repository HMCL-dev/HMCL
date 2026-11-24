// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskStage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/// Publishes the manifest and primary JAR together. Replacement builds retain the existing run
/// directory unless isolation is enabled. Shared libraries, assets, and caches may remain after failure.
@NotNullByDefault
public class DefaultGameBuilder extends GameBuilder {

    /// Dependency manager used for component installation and repository access.
    private final DefaultDependencyManager dependencyManager;

    /// Id of the instance to create or replace.
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    /// Exclusive repository draft used to publish the build.
    private final DefaultGameRepositoryDraft draft;

    /// Empty manifest reserved in the draft for the target instance.
    private final GameInstanceManifest initialManifest;

    /// Whether installation has started and configuration is frozen.
    private boolean buildStarted;

    /// Published manifest identifying a new installation that this builder can still roll back.
    private @Nullable GameInstanceManifest committedManifest;

    /// Whether instance isolation was requested for this build.
    protected boolean isolationEnabled;

    /// Uses the manager's open draft containing the empty manifest for `instanceId`.
    /// For an update, `updateTarget` must be the published instance reserved in that draft.
    protected DefaultGameBuilder(
            DefaultDependencyManager dependencyManager,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget,
            DefaultGameRepositoryDraft draft,
            GameInstanceManifest initialManifest) {
        this.dependencyManager = dependencyManager;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.draft = draft;
        this.initialManifest = initialManifest;
    }

    /// Returns the dependency manager used by this builder.
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
    @Override
    public DefaultGameInstance build(TaskExecutionContext context) throws Exception {
        checkOpen();
        buildStarted = true;
        try (draft) {
            context.checkCancellation();
            @Nullable String gameVersion = (String) components.get(GameComponentType.GAME);
            if (gameVersion == null)
                throw new IllegalStateException("GameBuilder.gameVersion must be set");

            var hints = new ArrayList<TaskStage.Hint>();

            components.forEach((componentType, version) -> {
                hints.add(new TaskStage.Hint(InstallStages.component(componentType,
                        version instanceof ComponentRemoteVersion remoteVersion
                                ? remoteVersion.getSelfVersion()
                                : (String) version)));

                if (componentType == GameComponentType.GAME) {
                    hints.add(new TaskStage.Hint(InstallStages.LIBRARIES));
                    hints.add(new TaskStage.Hint(InstallStages.ASSETS));
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

            return context.run(libraryTask.thenComposeAsync(manifest -> {
                GameInstanceManifest resolved = draft.getBaseSnapshot().resolve(manifest);
                return new GameDownloadTask(dependencyManager, resolved)
                        .withStage(InstallStages.component(GameComponentType.GAME, gameVersion))
                        .thenApplyAsync(minecraftJar -> {
                            draft.put(resolved.withPatches(manifest.patches()));
                            draft.putPrimaryJar(instanceId, minecraftJar);
                            DefaultGameInstance instance = draft.commit().getInstance(instanceId);
                            if (updateTarget == null)
                                committedManifest = instance.getManifest();
                            onInstanceCommitted(instance);
                            return instance;
                        });
            }).withStageHints(hints));
        }
    }

    /// {@inheritDoc}
    ///
    /// @throws UncheckedIOException if reserved instance files cannot be cleaned up
    @Override
    public void close() {
        try {
            draft.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot abort game builder draft", e);
        }
    }

    /// Aborts an open draft after all work on the reserved instance has stopped.
    /// Committed installations and shared download caches are retained.
    ///
    /// @throws IOException if reserved instance files cannot be removed; draft exclusivity is
    ///                     released even in this case
    public void abort() throws IOException {
        draft.close();
    }

    /// Aborts an open draft and removes a new instance this builder has already published if its
    /// stored manifest is still current. Updates and subsequently replaced instances are retained.
    /// All work using this builder or writing the target instance must have stopped before this call.
    ///
    /// @throws IOException if the draft cannot be aborted or the new instance cannot be removed
    /// @throws IllegalStateException if another draft prevents removal of the committed instance
    public void rollback() throws IOException {
        abort();
        if (committedManifest == null)
            return;

        try (DefaultGameRepositoryDraft removal = dependencyManager.getGameRepository().openDraft()) {
            @Nullable DefaultGameInstance current = removal.getBaseSnapshot().findInstance(instanceId);
            if (current != null && current.getManifest() == committedManifest) {
                removal.remove(instanceId);
                removal.commit();
            }
            committedManifest = null;
        }
    }

    /// Runs after publication. If initialization fails, the build fails and the instance remains published.
    protected void onInstanceCommitted(DefaultGameInstance instance) {
    }

    /// {@inheritDoc}
    @Override
    protected final void checkOpen() {
        if (buildStarted) {
            throw new IllegalStateException("GameBuilder has already started its build");
        }
        if (!draft.isOpen()) {
            throw new IllegalStateException("GameBuilder is closed");
        }
    }

}
