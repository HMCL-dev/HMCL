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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/// Builds a new game instance in an exclusive [GameRepositoryDraft], installs its components, and
/// publishes the completed instance once.
///
/// Shared libraries, assets, and download caches may remain after failure. The instance manifest
/// and primary JAR enter the instance tree only when the draft commits.
@NotNullByDefault
public class DefaultGameBuilder extends GameBuilder {

    /// Dependency manager used for component installation and repository access.
    private final DefaultDependencyManager dependencyManager;

    private final @Nullable DefaultGameInstance instance;

    /// Whether instance isolation was requested for this build.
    protected boolean isolationEnabled;

    /// Creates a builder bound to the given dependency manager.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param instance the existing game instance, or null to create a new one
    public DefaultGameBuilder(DefaultDependencyManager dependencyManager, @Nullable DefaultGameInstance instance) {
        this.dependencyManager = dependencyManager;
        this.instance = instance;
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
        isolationEnabled = true;
        return this;
    }

    /// {@inheritDoc}
    ///
    /// Retains an unpublished working manifest, installs the configured game and optional loaders,
    /// resolves the launch view, and commits it with the original patches once. Failure or
    /// cancellation aborts the draft.
    ///
    /// @return the build task
    /// @throws NullPointerException if [#id] was not set
    @Override
    public Task<?> buildAsync() {
        GameInstanceID id = Objects.requireNonNull(this.id, "GameBuilder.id must be set");
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
        DefaultGameRepositoryDraft draft = repository.openDraft();
        GameInstanceManifest initialManifest = new GameInstanceManifest(id);
        try {
            draft.put(initialManifest);
        } catch (IOException | RuntimeException e) {
            abortAfterReservationFailure(draft, e);
            throw new IllegalStateException("Cannot reserve game instance " + id, e);
        }

        Path runDirectory = isolationEnabled
                ? repository.getLayout().getInstanceRoot(id)
                : repository.hasInstance(id)
                        ? repository.getInstance(id).getRunDirectory()
                        : repository.getBaseDirectory();
        Path modsDirectory = runDirectory.resolve("mods");

        Task<GameInstanceManifest> libraryTask = dependencyManager.installNewInstanceComponentAsync(
                initialManifest, modsDirectory, gameVersion, GameComponentType.GAME, gameVersion);

        for (Map.Entry<GameComponentType, Object> entry : components.entrySet()) {
            GameComponentType componentType = entry.getKey();
            if (componentType == GameComponentType.GAME)
                continue;

            if (entry.getValue() instanceof ComponentRemoteVersion remoteVersion) {
                libraryTask = libraryTask.thenComposeAsync(manifest ->
                        dependencyManager.installNewInstanceComponentAsync(
                                manifest, modsDirectory, remoteVersion));
            } else if (entry.getValue() instanceof String version) {
                libraryTask = libraryTask.thenComposeAsync(manifest ->
                        dependencyManager.installNewInstanceComponentAsync(
                                manifest, modsDirectory, gameVersion, componentType, version));
            } else {
                throw new AssertionError("Unexpected version type: " + entry.getValue().getClass());
            }
        }

        return libraryTask.thenComposeAsync(manifest -> {
                    GameInstanceManifest.Resolved resolved = draft.getBaseSnapshot().resolve(manifest);
                    return new GameDownloadTask(dependencyManager, resolved.launchManifest())
                            .thenApplyAsync(minecraftJar -> {
                                draft.put(resolved.launchManifest().withPatches(manifest.patches()));
                                draft.putPrimaryJar(id, minecraftJar);
                                DefaultGameInstance instance = draft.commit().getInstance(id);
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

    /// Aborts a draft whose initial instance reservation failed.
    ///
    /// @param draft   the draft to abort
    /// @param failure the reservation failure that receives any cleanup failure as suppressed
    private static void abortAfterReservationFailure(
            DefaultGameRepositoryDraft draft,
            Throwable failure) {
        try {
            draft.abort();
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

}
