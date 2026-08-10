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

import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameRepositoryDraft;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a new game instance by registering a placeholder via [GameRepositoryDraft], installing
 * components against that instance, then saving the final manifest.
 *
 * @author huangyuhui
 */
public class DefaultGameBuilder extends GameBuilder {

    private final DefaultDependencyManager dependencyManager;

    public DefaultGameBuilder(DefaultDependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    public DefaultDependencyManager getDependencyManager() {
        return dependencyManager;
    }

    @Override
    public Task<?> buildAsync() {
        Objects.requireNonNull(name, "GameBuilder.name must be set");
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

        // Register the placeholder instance through a draft (single index publish), then install
        // components. A final saveAsync publishes the completed manifest.
        return Task.supplyAsync(() -> {
                    GameRepositoryDraft draft = repository.openDraft();
                    try {
                        GameInstance instance = draft.put(new GameInstanceManifest(name));
                        draft.commit();
                        return instance;
                    } catch (IOException e) {
                        draft.abort();
                        throw e;
                    } catch (RuntimeException e) {
                        draft.abort();
                        throw e;
                    }
                })
                .thenComposeAsync(instance -> {
                    Task<GameInstanceManifest> libraryTask = Task.completed(instance.getManifest());
                    libraryTask = libraryTask.thenComposeAsync(
                            libraryTaskHelper(instance, gameVersion, "game", gameVersion));

                    for (Map.Entry<String, String> entry : toolVersions.entrySet()) {
                        libraryTask = libraryTask.thenComposeAsync(
                                libraryTaskHelper(instance, gameVersion, entry.getKey(), entry.getValue()));
                    }

                    for (RemoteVersion remoteVersion : remoteVersions) {
                        libraryTask = libraryTask.thenComposeAsync(working ->
                                dependencyManager.installComponentAsync(instance, working, remoteVersion));
                    }

                    return libraryTask.thenComposeAsync(repository::saveAsync);
                })
                .whenComplete(exception -> {
                    if (exception != null) {
                        dependencyManager.getGameRepository().removeInstanceFromDisk(name);
                    }
                })
                .withStagesHints(hints);
    }

    private ExceptionalFunction<GameInstanceManifest, Task<GameInstanceManifest>, ?> libraryTaskHelper(
            GameInstance instance,
            String gameVersion,
            String libraryId,
            String libraryVersion) {
        return working -> dependencyManager.installComponentAsync(
                instance, working, gameVersion, libraryId, libraryVersion);
    }
}
