// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    /// Selected remote game version.
    private final GameRemoteVersion remote;

    /// Task that downloads the selected version's manifest JSON.
    private final GameInstanceJsonDownloadTask downloadTask;

    /// Downloads scheduled after the remote manifest has been decoded.
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    /// Creates a base-game installation task.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param manifest          the working instance manifest
    /// @param remoteVersion     the selected remote game version
    public GameInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, GameRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;
        this.downloadTask = new GameInstanceJsonDownloadTask(remoteVersion.getGameVersion().toString(), dependencyManager);
    }

    /// {@inheritDoc}
    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(downloadTask);
    }

    /// {@inheritDoc}
    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    /// {@inheritDoc}
    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    /// Decodes the downloaded game manifest and schedules its files without saving repository state.
    @Override
    public void execute() throws Exception {
        GameInstancePatch patch = GameInstancePatch.fromManifest(
                JsonUtils.fromNonNullJson(downloadTask.getResult(), GameInstanceManifest.class),
                GameComponentType.GAME.getPatchId(),
                remote.getGameVersion().toString(),
                GameInstancePatch.PRIORITY_MC).withJar(null);
        setResult(patch);

        GameInstanceManifest newManifest = new GameInstanceManifest(this.manifest.id()).addPatch(patch).reconstructByPatches();
        dependencies.add(Task.allOf(
                new GameDownloadTask(dependencyManager, newManifest),
                Task.allOf(
                        new GameAssetDownloadTask(dependencyManager, newManifest, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true),
                        new GameLibrariesTask(dependencyManager, newManifest, true)
                ).withRunAsync(() -> {
                    // ignore failure
                })
        ));
    }

}
