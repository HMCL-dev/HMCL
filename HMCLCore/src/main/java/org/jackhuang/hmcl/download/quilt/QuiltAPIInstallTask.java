// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.quilt;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * <b>Note</b>: Quilt should be installed first.
 *
 * @author huangyuhui
 */
public final class QuiltAPIInstallTask extends Task<@Nullable GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final QuiltAPIRemoteVersion remote;
    private final Path modsDirectory;

    /// @param dependencyManager the dependency manager
    /// @param manifest           the manifest being installed into
    /// @param remoteVersion      the Quilt API remote version
    /// @param modsDirectory      the target mods directory (must already be resolved by the caller)
    public QuiltAPIInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            QuiltAPIRemoteVersion remoteVersion,
            Path modsDirectory) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;
        this.modsDirectory = modsDirectory;
    }

    /// Downloads the API mod without adding a component patch to the manifest.
    @Override
    public @Nullable GameInstancePatch execute(TaskExecutionContext context) throws Exception {
        context.run(new FileDownloadTask(
                remote.getVersion().file().url(),
                modsDirectory.resolve("quilt-api-" + remote.getVersion().version() + ".jar"),
                remote.getVersion().file().getIntegrityCheck()));
        return null;
    }
}
