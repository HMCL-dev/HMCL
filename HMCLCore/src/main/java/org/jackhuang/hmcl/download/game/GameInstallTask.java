// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Objects;

/// Downloads the base game component and returns its manifest patch without publishing it.
///
/// The vanilla client JAR is downloaded into shared cache storage; libraries and assets use their
/// repository-wide stores. The caller owns the working manifest and must stage the returned patch
/// in its repository draft.
@NotNullByDefault
public class GameInstallTask extends Task<GameInstancePatch> {

    /// Dependency manager used by the download tasks.
    private final DefaultDependencyManager dependencyManager;

    /// Working instance manifest that will receive the game patch.
    private final GameInstanceManifest manifest;

    /// Version id used to look up the manifest and identify the game patch.
    private final String gameVersion;

    /// Creates a base-game installation task.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param manifest          the working instance manifest
    /// @param remoteVersion     the selected remote game version
    public GameInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, GameRemoteVersion remoteVersion) {
        this.dependencyManager = Objects.requireNonNull(dependencyManager);
        this.manifest = manifest;
        this.gameVersion = remoteVersion.getGameVersion().toString();
    }

    /// Downloads the base manifest and attempts to install the client, assets and libraries in parallel.
    /// Returns the patch even if one of those file downloads fails.
    @Override
    public GameInstancePatch execute(TaskExecutionContext context) throws Exception {
        String json = context.run(new GameInstanceJsonDownloadTask(gameVersion, dependencyManager));
        GameInstancePatch patch = GameInstancePatch.fromManifest(
                JsonUtils.fromNonNullJson(json, GameInstanceManifest.class),
                GameComponentType.GAME.getPatchId(),
                gameVersion,
                GameInstancePatch.PRIORITY_MC).withJar(null);

        GameInstanceManifest newManifest = new GameInstanceManifest(this.manifest.id()).addPatch(patch).reconstructByPatches();
        context.tryRunAll(List.of(
                new GameDownloadTask(dependencyManager, newManifest),
                new GameAssetDownloadTask(dependencyManager, newManifest, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true),
                new GameLibrariesTask(dependencyManager, newManifest, true)));
        return patch;
    }
}
