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

    /// Creates a builder bound to the given dependency manager.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    public DefaultGameBuilder(DefaultDependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    /// Returns the dependency manager used by this builder.
    ///
    /// @return the dependency manager
    public DefaultDependencyManager getDependencyManager() {
        return dependencyManager;
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
                            version instanceof RemoteVersion remoteVersion
                                    ? remoteVersion.getSelfVersion()
                                    : (String) version)));

            if (componentType == GameComponentType.GAME) {
                hints.add(new Task.StagesHint("hmcl.install.libraries"));
                hints.add(new Task.StagesHint("hmcl.install.assets"));
            }
        });


        DefaultGameRepository repository = dependencyManager.getGameRepository();
        //noinspection resource
        DefaultGameRepositoryDraft draft = repository.openDraft();
        GameInstanceManifest initialManifest = new GameInstanceManifest(id);
        try {
            draft.put(initialManifest);
        } catch (IOException | RuntimeException e) {
            abortAfterReservationFailure(draft, e);
            throw new IllegalStateException("Cannot reserve game instance " + id, e);
        }

        Path runDirectory = repository.hasInstance(id)
                ? repository.getInstance(id).getRunDirectory()
                : useInstanceRunDirectory
                        ? repository.getLayout().getInstanceRoot(id)
                        : repository.getBaseDirectory();
        Path modsDirectory = runDirectory.resolve("mods");

        Task<GameInstanceManifest> libraryTask = dependencyManager.installNewInstanceComponentAsync(
                initialManifest, modsDirectory, gameVersion, GameComponentType.GAME, gameVersion);

        for (Map.Entry<GameComponentType, Object> entry : components.entrySet()) {
            GameComponentType componentType = entry.getKey();
            if (componentType == GameComponentType.GAME)
                continue;

            if (entry.getValue() instanceof RemoteVersion remoteVersion) {
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
                                return draft.commit().getInstance(id);
                            });
                })
                .whenComplete(exception -> {
                    if (draft.isOpen()) {
                        draft.abort();
                    }
                })
                .withStagesHints(hints);
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
