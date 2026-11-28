// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.Lang;

import java.util.Collections;

/**
 * Note: LiteLoader must be installed after Forge.
 *
 * @author huangyuhui
 */
public final class LiteLoaderInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final LiteLoaderRemoteVersion remote;

    public LiteLoaderInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, LiteLoaderRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;
    }

    /// Downloads the libraries referenced by the LiteLoader patch and returns the patch.
    @Override
    public GameInstancePatch execute(TaskExecutionContext context) throws Exception {
        Library library = new Library(
                new Artifact("com.mumfrey", "liteloader", remote.getSelfVersion()),
                "https://dl.liteloader.com/versions/",
                new LibrariesDownloadInfo(new LibraryDownloadInfo(null, remote.getUrls().get(0)))
        );

        // Disable the official logging configuration because some mods produce malformed XML.
        GameInstancePatch patch = new GameInstancePatch(GameComponentType.LITELOADER.getPatchId(),
                remote.getSelfVersion(),
                60000,
                new Arguments().addGameArguments("--tweakClass", "com.mumfrey.liteloader.launch.LiteLoaderTweaker"),
                GameComponentAnalyzer.LAUNCH_WRAPPER_MAIN,
                Lang.merge(remote.getLibraries(), Collections.singleton(library)))
                .withLogging(Collections.emptyMap());

        context.run(new GameLibrariesTask(dependencyManager, manifest, true, patch.getLibraries()));
        return patch;
    }
}
