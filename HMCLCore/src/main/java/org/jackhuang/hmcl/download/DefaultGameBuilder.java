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

import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
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
        var hints = new ArrayList<Task.StagesHint>();

        var repository = dependencyManager.getGameRepository();
        boolean isUpdate = repository.hasInstance(name);
        GameInstanceManifest preUpdateManifest = null;
        String preUpdateGameVersion = null;
        if (isUpdate) {
            try {
                preUpdateManifest = repository.getInstanceManifest(name);
                preUpdateGameVersion = repository.getGameVersion(preUpdateManifest).orElse(null);
            } catch (Exception ignored) {
            }
        }

        Task<GameInstanceManifest> libraryTask = Task.supplyAsync(() -> new GameInstanceManifest(name));
        libraryTask = libraryTask.thenComposeAsync(libraryTaskHelper(gameVersion, "game", gameVersion));
        hints.add(new Task.StagesHint("hmcl.install.game:" + gameVersion));
        hints.add(new Task.StagesHint("hmcl.install.libraries"));
        hints.add(new Task.StagesHint("hmcl.install.assets"));

        for (Map.Entry<String, String> entry : toolVersions.entrySet()) {
            GameInstancePatch matchingPatch = getIntactLoaderPatch(preUpdateManifest, preUpdateGameVersion, entry.getKey(), entry.getValue());
            if (matchingPatch != null) {
                libraryTask = libraryTask.thenApplyAsync(version -> version.addPatch(matchingPatch));
            } else {
                libraryTask = libraryTask.thenComposeAsync(libraryTaskHelper(gameVersion, entry.getKey(), entry.getValue()));
            }
            hints.add(new Task.StagesHint(String.format("hmcl.install.%s:%s", entry.getKey(), entry.getValue())));
        }

        for (RemoteVersion remoteVersion : remoteVersions) {
            GameInstancePatch matchingPatch = getIntactLoaderPatch(preUpdateManifest, preUpdateGameVersion, remoteVersion.getLibraryId(), remoteVersion.getSelfVersion());
            if (matchingPatch != null) {
                libraryTask = libraryTask.thenApplyAsync(version -> version.addPatch(matchingPatch));
            } else {
                libraryTask = libraryTask.thenComposeAsync(version -> dependencyManager.installLibraryAsync(version, remoteVersion));
            }
            hints.add(new Task.StagesHint(String.format("hmcl.install.%s:%s", remoteVersion.getLibraryId(), remoteVersion.getSelfVersion())));
        }

        return libraryTask.thenComposeAsync(repository::saveAsync).whenComplete(exception -> {
            if (exception != null && !isUpdate) {
                repository.removeInstanceFromDisk(name);
            }
        }).withStagesHints(hints);
    }

    /// Checks if a matching loader patch was installed prior to the rebuild for the same game version and is intact.
    private @Nullable GameInstancePatch getIntactLoaderPatch(
            @Nullable GameInstanceManifest preUpdateManifest,
            @Nullable String preUpdateGameVersion,
            String libraryId,
            String libraryVersion) {
        if (preUpdateManifest == null || !Objects.equals(gameVersion, preUpdateGameVersion)) {
            return null;
        }

        GameInstancePatch matchingPatch = preUpdateManifest.getPatches().stream()
                .filter(patch -> libraryId.equals(patch.id()) && Objects.equals(patch.version(), libraryVersion))
                .findFirst()
                .orElse(null);
        if (matchingPatch == null) {
            return null;
        }

        var repository = dependencyManager.getGameRepository();
        List<Library> patchLibraries = matchingPatch.libraries();
        boolean needsRepair = patchLibraries != null && patchLibraries.stream()
                .filter(Library::appliesToCurrentEnvironment)
                .anyMatch(lib -> GameLibrariesTask.shouldDownloadLibrary(repository, preUpdateManifest, lib, true));

        return needsRepair ? null : matchingPatch;
    }

    private ExceptionalFunction<GameInstanceManifest, Task<GameInstanceManifest>, ?> libraryTaskHelper(String gameVersion, String libraryId, String libraryVersion) {
        return version -> dependencyManager.installLibraryAsync(gameVersion, version, libraryId, libraryVersion);
    }
}
