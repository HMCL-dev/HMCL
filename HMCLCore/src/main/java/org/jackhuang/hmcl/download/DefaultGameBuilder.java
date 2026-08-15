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
    /// stages the completed manifest, and commits it once. Failure or cancellation aborts the draft.
    ///
    /// @return the build task
    /// @throws NullPointerException if [#id] was not set
    @Override
    public Task<?> buildAsync() {
        GameInstanceID id = Objects.requireNonNull(this.id, "GameBuilder.id must be set");
        var hints = new ArrayList<Task.StagesHint>();

        hints.add(new Task.StagesHint("hmcl.install.game:" + gameVersion));
        hints.add(new Task.StagesHint("hmcl.install.libraries"));
        hints.add(new Task.StagesHint("hmcl.install.assets"));
        for (Map.Entry<String, String> entry : toolVersions.entrySet()) {
            hints.add(new Task.StagesHint(
                    String.format("hmcl.install.%s:%s", entry.getKey(), entry.getValue())));
        }
        for (RemoteVersion remoteVersion : remoteVersions) {
            hints.add(new Task.StagesHint(String.format(
                    "hmcl.install.%s:%s",
                    remoteVersion.getLibraryId(),
                    remoteVersion.getSelfVersion())));
        }

        DefaultGameRepository repository = dependencyManager.getGameRepository();
        //noinspection resource
        GameRepositoryDraft draft = repository.openDraft();

        Task<GameInstanceManifest> libraryTask = dependencyManager.installNewInstanceComponentAsync(
                id, new GameInstanceManifest(id), gameVersion, GameComponentType.GAME, gameVersion);

        for (Map.Entry<String, String> entry : toolVersions.entrySet()) {
            libraryTask = libraryTask.thenComposeAsync(manifest -> dependencyManager.installNewInstanceComponentAsync(
                    id, manifest, gameVersion, GameComponentType.fromPatchId(entry.getKey()), entry.getValue()));
        }

        for (RemoteVersion remoteVersion : remoteVersions) {
            libraryTask = libraryTask.thenComposeAsync(manifest ->
                    dependencyManager.installNewInstanceComponentAsync(
                            id, manifest, gameVersion, remoteVersion));
        }

        return libraryTask.thenComposeAsync(manifest ->
                        new GameDownloadTask(dependencyManager, gameVersion, manifest)
                                .thenApplyAsync(minecraftJar -> {
                                    draft.put(manifest);
                                    draft.putPrimaryJar(id, minecraftJar);
                                    return draft.commit().getInstance(id);
                                }))
                .whenComplete(exception -> {
                    if (draft.isOpen()) {
                        draft.abort();
                    }
                })
                .withStagesHints(hints);
    }

}
